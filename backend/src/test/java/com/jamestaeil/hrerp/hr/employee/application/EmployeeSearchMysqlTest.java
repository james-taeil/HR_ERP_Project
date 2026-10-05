package com.jamestaeil.hrerp.hr.employee.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.EmploymentStatus;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.EmployeeSearchScopeReader;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
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
class EmployeeSearchMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }
    @Autowired EmployeeSearchService service;
    @Autowired DataSource source;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean EmployeeSearchScopeReader scopes;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(source);
        when(actors.requireActorId()).thenReturn(9L);
        when(scopes.requireReadableScope(9L)).thenReturn(new EmployeeSearchScopeReader.Scope(null, Set.of(701L)));
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM employees WHERE employee_name LIKE 'SEARCH-%'");
        reset(actors, scopes);
    }

    @Test void combinesFiltersAndEscapesLikeWildcards() {
        insert("71000001", "SEARCH-홍%", LocalDate.of(2025, 1, 1), "REGULAR", 70, 701, "팀장", "ACTIVE");
        insert("71000002", "SEARCH-홍길동", LocalDate.of(2025, 2, 1), "CONTRACT", 70, 701, "사원", "ACTIVE");
        insert("71000003", "SEARCH-홍길순", LocalDate.of(2025, 2, 1), "CONTRACT", 70, 702, "사원", "ACTIVE");
        var page = service.search(criteria(70L, 701L, EmploymentStatus.ACTIVE, EmploymentType.CONTRACT,
            LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), "사원", "홍길", 0, 10));
        assertEquals(1, page.items().size()); assertEquals("71000002", page.items().getFirst().employeeNumber());
        var literal = service.search(criteria(null, null, null, null, null, null, null, "%", 0, 10));
        assertEquals(1, literal.items().size()); assertEquals("SEARCH-홍%", literal.items().getFirst().name());
    }

    @Test void usesStableCursorWithoutLeakingOtherDepartments() {
        insert("72000001", "SEARCH-A", LocalDate.of(2025, 1, 1), "REGULAR", 70, 701, "사원", "ACTIVE");
        insert("72000002", "SEARCH-HIDDEN", LocalDate.of(2025, 1, 1), "REGULAR", 70, 702, "사원", "ACTIVE");
        insert("72000003", "SEARCH-B", LocalDate.of(2025, 1, 1), "REGULAR", 70, 701, "사원", "ACTIVE");
        var first = service.search(criteria(null, null, null, null, null, null, null, "SEARCH-", 0, 1));
        assertEquals(1, first.items().size()); assertNotNull(first.nextCursor());
        var second = service.search(criteria(null, null, null, null, null, null, null, "SEARCH-", first.nextCursor(), 1));
        assertEquals("SEARCH-B", second.items().getFirst().name()); assertNull(second.nextCursor());
    }

    @Test void lastPageAtOneHundredThousandRowsStaysUnderOneSecond() {
        jdbc.execute("SET SESSION cte_max_recursion_depth=100001");
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date, employment_type,
                workplace_id, department_id, position_name, employment_status)
            WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<100000)
            SELECT LPAD(80000000+n, 8, '0'), CONCAT('SEARCH-PERF-', n), '1990-01-01', 'TEST', '2025-01-01',
                'REGULAR', 70, 701, '사원', 'ACTIVE' FROM seq
            """);
        long last = jdbc.queryForObject("SELECT id FROM employees WHERE employee_name='SEARCH-PERF-99950'", Long.class);
        service.search(criteria(null, null, null, null, null, null, null, null, last, 50));
        assertTimeout(Duration.ofSeconds(1), () -> {
            var page = service.search(criteria(null, null, null, null, null, null, null, null, last, 50));
            assertEquals(50, page.items().size());
        });
    }

    private EmployeeSearchService.Criteria criteria(Long workplace, Long department, EmploymentStatus status,
            EmploymentType type, LocalDate from, LocalDate to, String position, String query, long after, int limit) {
        return new EmployeeSearchService.Criteria(workplace, department, status, type, from, to, position, query, after, limit);
    }
    private void insert(String number, String name, LocalDate hire, String type, long workplace, long department,
                        String position, String status) {
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date, employment_type,
                workplace_id, department_id, position_name, employment_status) VALUES (?, ?, '1990-01-01', 'TEST', ?, ?, ?, ?, ?, ?)
            """, number, name, hire, type, workplace, department, position, status);
    }
}
