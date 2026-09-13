package com.jamestaeil.hrerp.hr.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jamestaeil.hrerp.platform.port.NotificationSender;
import com.jamestaeil.hrerp.platform.port.NotificationSender.Audience;
import com.jamestaeil.hrerp.platform.port.NotificationSender.Kind;
import com.jamestaeil.hrerp.platform.port.NotificationSender.Notification;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Run only against a disposable, explicitly selected hr_record_test schema. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class ReminderMysqlTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }

    private static final AtomicInteger NUMBERS = new AtomicInteger(97000000);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);
    @Autowired ReminderBatchService service;
    @Autowired DataSource source;
    @MockitoBean NotificationSender sender;
    private JdbcTemplate jdbc;
    private long matchingEmployee;
    private long outsideEmployee;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(source);
        matchingEmployee = createEmployee(TODAY.plusDays(14));
        outsideEmployee = createEmployee(TODAY.plusDays(15));
        addCertification(matchingEmployee, TODAY.plusDays(30));
        addCertification(outsideEmployee, TODAY.plusDays(31));
        addForeignProfile(matchingEmployee, TODAY.plusDays(60));
        addForeignProfile(outsideEmployee, TODAY.plusDays(61));
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("reminder_deliveries", "foreign_worker_profiles", "certifications")) {
            jdbc.update("DELETE FROM " + table + " WHERE employee_id IN (?, ?)", matchingEmployee, outsideEmployee);
        }
        jdbc.update("DELETE FROM employees WHERE id IN (?, ?)", matchingEmployee, outsideEmployee);
        reset(sender);
    }

    @Test
    void sendsOnlyExactBoundaryRemindersAndDoesNotResend() {
        service.run(TODAY);
        service.run(TODAY);

        verify(sender).send(new Notification(Audience.EMPLOYEE, Kind.CERTIFICATION_EXPIRY,
            matchingEmployee, certificationId(matchingEmployee), TODAY.plusDays(30)));
        verify(sender).send(new Notification(Audience.HR, Kind.CERTIFICATION_EXPIRY,
            matchingEmployee, certificationId(matchingEmployee), TODAY.plusDays(30)));
        verify(sender).send(new Notification(Audience.HR, Kind.PROBATION_END,
            matchingEmployee, matchingEmployee, TODAY.plusDays(14)));
        verify(sender).send(new Notification(Audience.HR, Kind.STAY_EXPIRY,
            matchingEmployee, matchingEmployee, TODAY.plusDays(60)));
        verify(sender, times(4)).send(any());
        assertEquals(4, deliveryCount());
    }

    @Test
    void concurrentRunsClaimEachReminderOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runAfter(start));
            Future<?> second = executor.submit(() -> runAfter(start));
            start.countDown();
            first.get();
            second.get();
        }
        verify(sender, times(4)).send(any());
        assertEquals(4, deliveryCount());
    }

    @Test
    void senderFailureRollsBackClaimSoNextRunCanRetry() {
        doThrow(new IllegalStateException("temporary notification failure"))
            .when(sender).send(any());
        assertThrows(IllegalStateException.class, () -> service.run(TODAY));
        assertEquals(0, deliveryCount());

        reset(sender);
        service.run(TODAY);
        verify(sender, times(4)).send(any());
        assertEquals(4, deliveryCount());
    }

    private void runAfter(CountDownLatch start) {
        try {
            start.await();
            service.run(TODAY);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private long createEmployee(LocalDate probationEnd) {
        String number = Integer.toString(NUMBERS.incrementAndGet());
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
                employment_type, workplace_id, department_id, position_name, probation_end_date, foreign_worker)
            VALUES (?, '알림테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '테스트', ?, TRUE)
            """, number, probationEnd);
        return jdbc.queryForObject("SELECT id FROM employees WHERE employee_number = ?", Long.class, number);
    }

    private void addCertification(long employeeId, LocalDate expiry) {
        jdbc.update("""
            INSERT INTO certifications (employee_id, certification_name, issuer, acquired_date, expires_on)
            VALUES (?, '알림테스트', '테스트기관', '2026-01-01', ?)
            """, employeeId, expiry);
    }

    private void addForeignProfile(long employeeId, LocalDate stayUntil) {
        jdbc.update("""
            INSERT INTO foreign_worker_profiles (employee_id, nationality, visa_type, stay_from, stay_until,
                encrypted_registration_number, registration_number_mask)
            VALUES (?, 'TEST', 'E-7', '2026-01-01', ?, X'01', 'TEST')
            """, employeeId, stayUntil);
    }

    private long certificationId(long employeeId) {
        return jdbc.queryForObject("SELECT id FROM certifications WHERE employee_id = ?", Long.class, employeeId);
    }

    private int deliveryCount() {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM reminder_deliveries WHERE employee_id IN (?, ?)",
            Integer.class, matchingEmployee, outsideEmployee);
    }
}
