package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class PlatformFoundationMysqlTest {
	private static final AtomicInteger NUMBERS = new AtomicInteger(95000000);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry properties) {
		properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
		properties.add("spring.datasource.username",
			() -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
		properties.add("spring.datasource.password",
			() -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
	}

	@Autowired AccountService accounts;
	@Autowired DataSource source;
	@Autowired Flyway flyway;
	private JdbcTemplate jdbc;
	private long employeeId;

	@BeforeEach
	void setup() {
		jdbc = new JdbcTemplate(source);
		String employeeNumber = Integer.toString(NUMBERS.incrementAndGet());
		jdbc.update("""
			INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
				employment_type, workplace_id, department_id, position_name)
			VALUES (?, '플랫폼테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
			""", employeeNumber);
		employeeId = jdbc.queryForObject(
			"SELECT id FROM employees WHERE employee_number=?", Long.class, employeeNumber);
	}

	@AfterEach
	void cleanup() {
		jdbc.update("DELETE FROM platform_password_history WHERE account_id IN "
			+ "(SELECT id FROM platform_accounts WHERE employee_id=?)", employeeId);
		jdbc.update("DELETE FROM platform_accounts WHERE employee_id=?", employeeId);
		jdbc.update("DELETE FROM employees WHERE id=?", employeeId);
	}

	@Test
	void appliesSchemaValidatesJpaAndReRunsFlywayWithoutChanges() {
		Integer tableCount = jdbc.queryForObject("""
			SELECT COUNT(*) FROM information_schema.tables
			WHERE table_schema = DATABASE() AND table_name LIKE 'platform_%'
			""", Integer.class);
		assertEquals(17, tableCount);
		assertEquals(0, flyway.migrate().migrationsExecuted);

		String rawPassword = "long-password!";
		long accountId = accounts.create(employeeId, "Platform.Worker", rawPassword);
		String storedHash = jdbc.queryForObject(
			"SELECT password_hash FROM platform_accounts WHERE id=?", String.class, accountId);
		assertNotEquals(rawPassword, storedHash);
		assertTrue(storedHash.startsWith("$2"));
		assertEquals(1, jdbc.queryForObject(
			"SELECT COUNT(*) FROM platform_password_history WHERE account_id=?", Integer.class, accountId));
	}
}
