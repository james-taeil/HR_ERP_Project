package com.jamestaeil.hrerp.platform.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

import com.jamestaeil.hrerp.platform.account.AccountService;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationAdminService.RoleAssignment;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class AuthorizationRollbackMysqlTest {
	private static final AtomicInteger NUMBERS = new AtomicInteger(98000000);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry properties) {
		properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
		properties.add("spring.datasource.username",
			() -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
		properties.add("spring.datasource.password",
			() -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
	}

	@Autowired DataSource source;
	@Autowired AccountService accounts;
	@Autowired AuthorizationAdminService authorization;
	@MockitoBean AuthorizationChangeLogWriter changes;
	private JdbcTemplate jdbc;
	private int suffix;
	private long actorEmployeeId;
	private long targetEmployeeId;
	private long actorAccountId;
	private long targetAccountId;
	private long actorRoleId;
	private long originalRoleId;
	private long replacementRoleId;

	@BeforeEach
	void setup() {
		jdbc = new JdbcTemplate(source);
		suffix = NUMBERS.incrementAndGet();
		actorEmployeeId = employee("1");
		targetEmployeeId = employee("2");
		actorAccountId = accounts.create(actorEmployeeId, "rollback.admin." + suffix, "correct-password!");
		targetAccountId = accounts.create(targetEmployeeId, "rollback.target." + suffix, "correct-password!");
		actorRoleId = role("ROLLBACK_ADMIN" + suffix, PermissionCode.PLATFORM_AUTHORIZATION_MANAGE);
		originalRoleId = role("ROLLBACK_ORIGINAL" + suffix, PermissionCode.HR_RECORD_READ);
		replacementRoleId = role("ROLLBACK_REPLACEMENT" + suffix, PermissionCode.HR_RECORD_WRITE);
		assign(actorAccountId, actorRoleId);
		assign(targetAccountId, originalRoleId);
	}

	@AfterEach
	void cleanup() {
		jdbc.update("DELETE FROM platform_account_roles WHERE account_id IN (?, ?)", actorAccountId, targetAccountId);
		jdbc.update("DELETE FROM platform_password_history WHERE account_id IN (?, ?)", actorAccountId, targetAccountId);
		jdbc.update("DELETE FROM platform_accounts WHERE id IN (?, ?)", actorAccountId, targetAccountId);
		jdbc.update("DELETE FROM platform_role_permissions WHERE role_id IN (?, ?, ?)",
			actorRoleId, originalRoleId, replacementRoleId);
		jdbc.update("DELETE FROM platform_roles WHERE id IN (?, ?, ?)", actorRoleId, originalRoleId, replacementRoleId);
		jdbc.update("DELETE FROM employees WHERE id IN (?, ?)", actorEmployeeId, targetEmployeeId);
	}

	@Test
	void rollsBackRoleReplacementWhenChangeLogFails() {
		doThrow(new IllegalStateException("log unavailable")).when(changes)
			.write(anyLong(), anyLong(), anyString(), any(), any());

		assertThrows(IllegalStateException.class, () -> authorization.replaceAccountRoles(actorAccountId,
			targetAccountId, List.of(new RoleAssignment(replacementRoleId,
				Instant.now().minus(1, ChronoUnit.HOURS), null))));

		assertEquals(originalRoleId, jdbc.queryForObject(
			"SELECT role_id FROM platform_account_roles WHERE account_id=?", Long.class, targetAccountId));
	}

	private long employee(String discriminator) {
		String employeeNumber = Integer.toString(suffix + Integer.parseInt(discriminator));
		jdbc.update("""
			INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
				employment_type, workplace_id, department_id, position_name)
			VALUES (?, '롤백테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
			""", employeeNumber);
		return jdbc.queryForObject("SELECT id FROM employees WHERE employee_number=?", Long.class, employeeNumber);
	}

	private long role(String code, PermissionCode permission) {
		jdbc.update("INSERT INTO platform_roles (role_code, role_name) VALUES (?, ?)", code, code);
		long roleId = jdbc.queryForObject("SELECT id FROM platform_roles WHERE role_code=?", Long.class, code);
		jdbc.update("""
			INSERT INTO platform_role_permissions (role_id, permission_id)
			SELECT ?, id FROM platform_permissions WHERE permission_code=?
			""", roleId, permission.name());
		return roleId;
	}

	private void assign(long accountId, long roleId) {
		jdbc.update("INSERT INTO platform_account_roles (account_id, role_id, valid_from) VALUES (?, ?, ?)",
			accountId, roleId, Instant.now().minus(1, ChronoUnit.HOURS));
	}
}
