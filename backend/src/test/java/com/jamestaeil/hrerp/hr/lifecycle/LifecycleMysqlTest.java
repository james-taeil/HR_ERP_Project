package com.jamestaeil.hrerp.hr.lifecycle;

import static com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;
import com.jamestaeil.hrerp.platform.port.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class LifecycleMysqlTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }

    private static final AtomicInteger NUMBERS = new AtomicInteger(96000000);
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    @Autowired LifecycleService service;
    @Autowired DataSource source;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean AuthorizationChecker authorization;
    @MockitoBean OrganizationReader organization;
    @MockitoBean OrganizationSnapshotReader organizationSnapshots;
    @MockitoBean FileStorage files;
    @MockitoBean RecordAudit audit;
    @MockitoBean AccountAccessScheduler accounts;
    @MockitoBean LifecycleTaskPublisher tasks;
    private JdbcTemplate jdbc;
    private long employee;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(source);
        employee = createEmployee();
        when(actors.requireActorId()).thenReturn(9L);
        when(organization.requireActiveDepartment(anyLong(), anyLong())).thenReturn(new DepartmentCode("01"));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM offboarding_checklist_items WHERE termination_id IN (SELECT id FROM terminations WHERE employee_id=?)", employee);
        jdbc.update("DELETE FROM terminations WHERE employee_id=?", employee);
        jdbc.update("DELETE FROM appointments WHERE employee_id=?", employee);
        jdbc.update("DELETE FROM employees WHERE id=?", employee);
        reset(actors, authorization, organization, organizationSnapshots, files, audit, accounts, tasks);
    }

    @Test
    void storesBeforeAndAfterAndReturnsAllowedNextOnInvalidTransition() {
        Saved changed = service.appoint(employee, command("position", TODAY, AppointmentType.POSITION_CHANGE,
            null, null, "팀장", null));
        assertEquals(AppointmentStatus.APPLIED, changed.status());
        Appointment appointment = service.appointments(employee).getFirst();
        assertEquals("사원", appointment.before().position());
        assertEquals("팀장", appointment.after().position());
        assertThrows(LifecycleConflictException.class, () -> service.appoint(employee,
            command("position", TODAY, AppointmentType.POSITION_CHANGE, null, null, "부장", null)));

        service.appoint(employee, command("leave", TODAY, AppointmentType.STATUS_CHANGE,
            null, null, null, EmploymentStatus.ON_LEAVE));
        InvalidStatusTransitionException failure = assertThrows(InvalidStatusTransitionException.class,
            () -> service.appoint(employee, command("suspend", TODAY, AppointmentType.STATUS_CHANGE,
                null, null, null, EmploymentStatus.SUSPENDED)));
        assertEquals(List.of(EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED), failure.allowed());
        assertEquals("ON_LEAVE", currentStatus());
    }

    @Test
    void scheduledAppointmentRollsBackOnApplyFailureThenRetriesOnce() {
        LocalDate future = TODAY.plusDays(2);
        service.appoint(employee, command("future", future, AppointmentType.DEPARTMENT_CHANGE,
            null, 2L, null, null));
        assertEquals(1L, currentDepartment());
        assertEquals("SCHEDULED", appointmentStatus());

        reset(audit);
        doThrow(new IllegalStateException("audit unavailable")).when(audit)
            .changed(anyLong(), anyLong(), eq("APPOINTMENT_APPLIED"), anyLong(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.applyDue(future));
        assertEquals(1L, currentDepartment());
        assertEquals("SCHEDULED", appointmentStatus());

        reset(audit);
        service.applyDue(future);
        service.applyDue(future);
        assertEquals(2L, currentDepartment());
        assertEquals("APPLIED", appointmentStatus());
        verify(audit, times(1)).changed(anyLong(), eq(employee), eq("APPOINTMENT_APPLIED"), anyLong(), any(), any());
    }

    @Test
    void concurrentFutureAppointmentsLeaveOnlyOnePendingChange() throws Exception {
        LocalDate future = TODAY.plusDays(3);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> attempt(start, "a", "대리", future, success, conflict));
            Future<?> second = executor.submit(() -> attempt(start, "b", "과장", future, success, conflict));
            start.countDown();
            first.get(); second.get();
        }
        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE employee_id=?", Integer.class, employee));
    }

    @Test
    void terminationIsAtomicAndSchedulesNextDayAccountBlock() {
        LifecycleService.TerminationCommand command = new LifecycleService.TerminationCommand(
            "termination", TODAY, "VOLUNTARY", true);
        doThrow(new IllegalStateException("task unavailable")).when(tasks).publish(any());
        assertThrows(IllegalStateException.class, () -> service.terminate(employee, command));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM terminations WHERE employee_id=?", Integer.class, employee));
        assertEquals("ACTIVE", currentStatus());

        reset(tasks);
        long first = service.terminate(employee, command);
        long retry = service.terminate(employee, command);
        assertEquals(first, retry);
        assertThrows(LifecycleConflictException.class, () -> service.terminate(employee,
            new LifecycleService.TerminationCommand("termination", TODAY, "DISMISSAL", true)));
        assertEquals(4, jdbc.queryForObject("""
            SELECT COUNT(*) FROM offboarding_checklist_items i JOIN terminations t ON t.id=i.termination_id
            WHERE t.employee_id=?
            """, Integer.class, employee));
        verify(tasks, times(4)).publish(any());
        verify(accounts).disableFrom(employee, TODAY.plusDays(1));
        assertEquals("TERMINATED", currentStatus());
    }

    @Test
    void organizationUsesPointInTimeSnapshotAndHeadcountExcludesLeave() {
        when(organizationSnapshots.departmentsOn(eq(9L), any())).thenReturn(List.of(
            new OrganizationSnapshotReader.Department(1, 1, null, "기존부서", 2),
            new OrganizationSnapshotReader.Department(2, 1, null, "이동부서", 3)));
        service.appoint(employee, command("move", TODAY.minusDays(1), AppointmentType.DEPARTMENT_CHANGE,
            null, 2L, null, null));
        assertEquals(1L, employeeOn(TODAY.minusDays(2)).departmentId());
        assertEquals(2L, employeeOn(TODAY).departmentId());
        long activeBeforeLeave = departmentCount(TODAY, 2);

        service.appoint(employee, command("leave-now", TODAY, AppointmentType.STATUS_CHANGE,
            null, null, null, EmploymentStatus.ON_LEAVE));
        assertEquals(activeBeforeLeave - 1, departmentCount(TODAY, 2));
    }

    private LifecycleService.AppointmentCommand command(String key, LocalDate date, AppointmentType type,
                                                        Long workplace, Long department, String position,
                                                        EmploymentStatus status) {
        return new LifecycleService.AppointmentCommand(key, date, type, workplace, department, position,
            status, "테스트 발령", null);
    }

    private void attempt(CountDownLatch start, String key, String position, LocalDate date,
                         AtomicInteger success, AtomicInteger conflict) {
        try {
            start.await();
            service.appoint(employee, command(key, date, AppointmentType.POSITION_CHANGE,
                null, null, position, null));
            success.incrementAndGet();
        } catch (LifecycleConflictException expected) {
            conflict.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private long createEmployee() {
        String number = Integer.toString(NUMBERS.incrementAndGet());
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
                employment_type, workplace_id, department_id, position_name)
            VALUES (?, '생명주기테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
            """, number);
        return jdbc.queryForObject("SELECT id FROM employees WHERE employee_number=?", Long.class, number);
    }

    private String currentStatus() {
        return jdbc.queryForObject("SELECT employment_status FROM employees WHERE id=?", String.class, employee);
    }
    private long currentDepartment() {
        return jdbc.queryForObject("SELECT department_id FROM employees WHERE id=?", Long.class, employee);
    }
    private String appointmentStatus() {
        return jdbc.queryForObject("SELECT appointment_status FROM appointments WHERE employee_id=?", String.class, employee);
    }
    private OrganizationEmployee employeeOn(LocalDate date) {
        return service.organizationChart(date).employees().stream()
            .filter(item -> item.employeeId() == employee).findFirst().orElseThrow();
    }
    private long departmentCount(LocalDate date, long departmentId) {
        return service.headcount(date).departments().stream()
            .filter(item -> item.departmentId() == departmentId).findFirst().orElseThrow().current();
    }
}
