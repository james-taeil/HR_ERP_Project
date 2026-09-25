package com.jamestaeil.hrerp.hr.contract;

import static com.jamestaeil.hrerp.hr.contract.ContractTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.platform.port.*;
import java.time.LocalDate;
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
class ContractMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }
    private static final AtomicInteger NUMBERS = new AtomicInteger(97000000);
    @Autowired ContractService service;
    @Autowired DataSource source;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean AuthorizationChecker authorization;
    @MockitoBean RecordAudit audit;
    private JdbcTemplate jdbc;
    private long employee;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(source); employee = createEmployee();
        when(actors.requireActorId()).thenReturn(9L);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM wage_contract_items WHERE wage_contract_id IN (SELECT id FROM wage_contracts WHERE employee_id=?)", employee);
        jdbc.update("DELETE FROM wage_contracts WHERE employee_id=?", employee);
        jdbc.update("DELETE FROM employment_contracts WHERE employee_id=?", employee);
        jdbc.update("DELETE FROM employees WHERE id=?", employee);
        reset(actors, authorization, audit);
    }

    @Test void rejectsBoundaryOverlapAndReplaysSameContract() {
        ContractCommand first = contract("first", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        long id = service.createContract(employee, first).id();
        assertEquals(id, service.createContract(employee, first).id());
        assertThrows(ContractConflictException.class, () -> service.createContract(employee,
            contract("overlap", LocalDate.of(2026, 6, 30), LocalDate.of(2026, 12, 31))));
        assertEquals(1, service.contracts(employee).size());
    }

    @Test void concurrentOverlapCommitsOnlyOne() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger(); AtomicInteger conflict = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> a = pool.submit(() -> attempt(start, contract("a", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30)), success, conflict));
            Future<?> b = pool.submit(() -> attempt(start, contract("b", LocalDate.of(2027, 6, 1), LocalDate.of(2027, 12, 31)), success, conflict));
            start.countDown(); a.get(); b.get();
        }
        assertEquals(1, success.get()); assertEquals(1, conflict.get());
    }

    @Test void appendsWageHistoryWithoutChangingPriorRows() {
        WageCommand first = wage("w1", LocalDate.of(2026, 1, 1), 3_000_000);
        long firstId = service.createWage(employee, first).id();
        assertEquals(firstId, service.createWage(employee, first).id());
        service.createWage(employee, wage("w2", LocalDate.of(2026, 7, 1), 3_300_000));
        List<WageContract> history = service.wages(employee);
        assertEquals(2, history.size()); assertEquals(3_000_000, history.getFirst().items().getFirst().amount());
        assertThrows(ContractConflictException.class, () -> service.createWage(employee,
            wage("w3", LocalDate.of(2026, 7, 1), 4_000_000)));
    }

    @Test void auditFailureRollsBackContract() {
        doThrow(new PlatformIntegrationUnavailableException()).when(audit)
            .changed(anyLong(), eq(employee), eq("EMPLOYMENT_CONTRACT"), anyLong(), isNull(), any());
        assertThrows(PlatformIntegrationUnavailableException.class, () -> service.createContract(employee,
            contract("rollback", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM employment_contracts WHERE employee_id=?", Integer.class, employee));
    }

    private void attempt(CountDownLatch start, ContractCommand command, AtomicInteger success, AtomicInteger conflict) {
        try { start.await(); service.createContract(employee, command); success.incrementAndGet(); }
        catch (ContractConflictException expected) { conflict.incrementAndGet(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
    private ContractCommand contract(String key, LocalDate from, LocalDate to) {
        return new ContractCommand(key, from, to, "서울", 2400, 3_000_000, null, null, null);
    }
    private WageCommand wage(String key, LocalDate from, long amount) {
        return new WageCommand(key, from, List.of(new WageItem("기본급", WageCategory.BASE_PAY, amount, true, true)));
    }
    private long createEmployee() {
        String number = Integer.toString(NUMBERS.incrementAndGet());
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
                employment_type, workplace_id, department_id, position_name)
            VALUES (?, '계약테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
            """, number);
        return jdbc.queryForObject("SELECT id FROM employees WHERE employee_number=?", Long.class, number);
    }
}
