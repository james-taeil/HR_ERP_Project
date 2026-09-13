package com.jamestaeil.hrerp.hr.record;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import com.jamestaeil.hrerp.HrErpApplication;
import com.jamestaeil.hrerp.platform.port.*;

/** Local UI verification only; src/test is excluded from the production artifact. */
public class RecordUiFixture {
    public static void main(String[] args) {
        String url = System.getenv("HR_RECORD_MYSQL_TEST_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/hr_record_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("A localhost hr_record_test database is required");
        }
        var context = new SpringApplicationBuilder(HrErpApplication.class, FixturePorts.class).run(
            "--server.address=127.0.0.1", "--server.port=18080", "--spring.datasource.url=" + url,
            "--spring.datasource.username=" + System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"),
            "--spring.datasource.password=" + System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
                employment_type, workplace_id, department_id, position_name)
            VALUES ('97999999', 'UI_TEST_ONLY', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '테스트')
            ON DUPLICATE KEY UPDATE employee_number = employee_number
            """);
        System.out.println("UI fixture employee ID: " + jdbc.queryForObject(
            "SELECT id FROM employees WHERE employee_number = '97999999'", Long.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixturePorts {
        @Bean @Primary CurrentActorProvider fixtureActor() { return () -> 999; }
        @Bean @Primary AuthorizationChecker fixtureAuthorization(JdbcTemplate jdbc) {
            return new AuthorizationChecker() {
                public void checkCanRegisterEmployee() { throw new RecordAccessForbiddenException(); }
                public void checkCanReadRecord(long actor, long employee) { check(employee); }
                public void checkCanWriteRecord(long actor, long employee) { check(employee); }
                private void check(long employee) {
                    if (jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE id = ? AND employee_name = 'UI_TEST_ONLY'",
                            Integer.class, employee) != 1) throw new RecordAccessForbiddenException();
                }
            };
        }
        @Bean @Primary FileStorage fixtureFiles() {
            return (actor, employee, file) -> { throw new PlatformIntegrationUnavailableException(); };
        }
        @Bean @Primary RecordAudit fixtureAudit() {
            return new RecordAudit() {
                public void changed(long actor, long employee, String section, long record, Object before, Object after) {}
                public void viewed(long actor, long employee, String section) {}
            };
        }
    }
}
