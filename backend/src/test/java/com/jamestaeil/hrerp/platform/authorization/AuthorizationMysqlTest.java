package com.jamestaeil.hrerp.platform.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jamestaeil.hrerp.platform.account.AccountService;
import com.jamestaeil.hrerp.platform.account.AuthenticationService;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class AuthorizationMysqlTest {
	private static final AtomicInteger NUMBERS = new AtomicInteger(97000000);

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
	@Autowired AuthenticationService authentication;
	@Autowired AuthorizationQueryService authorization;
	@Autowired AuthorizationAdminService admin;
	@Autowired MockMvc mockMvc;
	private JdbcTemplate jdbc;
	private int suffix;
	private long companyId;
	private long workplaceId;
	private long otherWorkplaceId;
	private long departmentId;
	private long childDepartmentId;
	private long otherDepartmentId;
	private long adminEmployeeId;
	private long actorEmployeeId;
	private long sameDepartmentEmployeeId;
	private long childEmployeeId;
	private long otherWorkplaceEmployeeId;
	private long adminAccountId;
	private long actorAccountId;
	private long readRoleId;

	@BeforeEach
	void setup() {
		jdbc = new JdbcTemplate(source);
		suffix = NUMBERS.incrementAndGet();
		jdbc.update("INSERT INTO platform_companies (company_name, active_from) VALUES (?, '2020-01-01')",
			"권한회사" + suffix);
		companyId = id("SELECT id FROM platform_companies WHERE company_name=?", "권한회사" + suffix);
		workplaceId = workplace("본사", Integer.toString(suffix));
		otherWorkplaceId = workplace("지사", Integer.toString(suffix + 1));
		departmentId = department("A" + suffix, "인사", workplaceId, null);
		childDepartmentId = department("B" + suffix, "인사운영", workplaceId, departmentId);
		otherDepartmentId = department("C" + suffix, "지사팀", otherWorkplaceId, null);

		adminEmployeeId = employee("1", workplaceId, departmentId);
		actorEmployeeId = employee("2", workplaceId, departmentId);
		sameDepartmentEmployeeId = employee("3", workplaceId, departmentId);
		childEmployeeId = employee("4", workplaceId, childDepartmentId);
		otherWorkplaceEmployeeId = employee("5", otherWorkplaceId, otherDepartmentId);
		adminAccountId = accounts.create(adminEmployeeId, "admin." + suffix, "correct-password!");
		actorAccountId = accounts.create(actorEmployeeId, "actor." + suffix, "correct-password!");
		role("ADMIN" + suffix, PermissionCode.PLATFORM_AUTHORIZATION_MANAGE, adminAccountId);
		readRoleId = role("READER" + suffix, PermissionCode.HR_RECORD_READ, actorAccountId);
	}

	@AfterEach
	void cleanup() {
		jdbc.update("DELETE FROM platform_authorization_change_logs WHERE actor_account_id IN (?, ?)",
			adminAccountId, actorAccountId);
		jdbc.update("DELETE FROM platform_organization_scopes WHERE account_id IN (?, ?)", adminAccountId, actorAccountId);
		jdbc.update("DELETE FROM platform_sessions WHERE account_id IN (?, ?)", adminAccountId, actorAccountId);
		jdbc.update("DELETE FROM platform_login_history WHERE account_id IN (?, ?)", adminAccountId, actorAccountId);
		jdbc.update("DELETE FROM platform_password_history WHERE account_id IN (?, ?)", adminAccountId, actorAccountId);
		jdbc.update("DELETE FROM platform_account_roles WHERE account_id IN (?, ?)", adminAccountId, actorAccountId);
		jdbc.update("DELETE rp FROM platform_role_permissions rp JOIN platform_roles r ON r.id=rp.role_id WHERE r.role_code IN (?, ?)",
			"ADMIN" + suffix, "READER" + suffix);
		jdbc.update("DELETE FROM platform_roles WHERE role_code IN (?, ?)", "ADMIN" + suffix, "READER" + suffix);
		jdbc.update("DELETE FROM platform_accounts WHERE id IN (?, ?)", adminAccountId, actorAccountId);
		jdbc.update("DELETE FROM employees WHERE id IN (?, ?, ?, ?, ?)", adminEmployeeId, actorEmployeeId,
			sameDepartmentEmployeeId, childEmployeeId, otherWorkplaceEmployeeId);
		jdbc.update("DELETE FROM platform_department_versions WHERE department_id IN (?, ?, ?)",
			departmentId, childDepartmentId, otherDepartmentId);
		jdbc.update("DELETE FROM platform_departments WHERE id IN (?, ?, ?)",
			departmentId, childDepartmentId, otherDepartmentId);
		jdbc.update("DELETE FROM platform_workplaces WHERE id IN (?, ?)", workplaceId, otherWorkplaceId);
		jdbc.update("DELETE FROM platform_companies WHERE id=?", companyId);
	}

	@Test
	void appliesPermissionChangesOnNextRequestAndKeepsSensitivePermissionsSeparate() {
		assertTrue(authorization.hasPermission(actorAccountId, PermissionCode.HR_RECORD_READ));
		assertFalse(authorization.hasPermission(actorAccountId, PermissionCode.HR_RECORD_SENSITIVE_READ));
		assertTrue(admin.permissions(adminAccountId).stream()
			.anyMatch(item -> item.permissionCode() == PermissionCode.HR_RECORD_SENSITIVE_READ
				&& item.sensitiveOperation()));

		jdbc.update("DELETE FROM platform_role_permissions WHERE role_id=?", readRoleId);
		assertFalse(authorization.hasPermission(actorAccountId, PermissionCode.HR_RECORD_READ));
	}

	@Test
	void combinesFunctionalPermissionWithSelfAndEveryOrganizationScope() {
		assertTrue(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ, actorEmployeeId));
		assertFalse(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ,
			sameDepartmentEmployeeId));

		scope(OrganizationScopeType.DEPARTMENT, departmentId);
		assertTrue(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ,
			sameDepartmentEmployeeId));
		assertFalse(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ, childEmployeeId));

		scope(OrganizationScopeType.DEPARTMENT_TREE, departmentId);
		assertTrue(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ, childEmployeeId));

		scope(OrganizationScopeType.WORKPLACE, workplaceId);
		assertTrue(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ, childEmployeeId));
		assertFalse(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ,
			otherWorkplaceEmployeeId));

		scope(OrganizationScopeType.COMPANY, companyId);
		assertTrue(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ,
			otherWorkplaceEmployeeId));

		scope(OrganizationScopeType.SELF, null);
		assertTrue(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ, actorEmployeeId));
		assertFalse(authorization.canAccessEmployee(actorAccountId, PermissionCode.HR_RECORD_READ,
			sameDepartmentEmployeeId));
	}

	@Test
	void serverRejectsUnauthenticatedAndOutOfScopeHrApiRequests() throws Exception {
		mockMvc.perform(get("/api/hr/employees/{id}/record", sameDepartmentEmployeeId))
			.andExpect(status().isUnauthorized());
		String token = authentication.login("actor." + suffix, "correct-password!", null, null)
			.orElseThrow().rawToken();
		mockMvc.perform(get("/api/hr/employees/{id}/record", sameDepartmentEmployeeId)
			.cookie(new MockCookie("HRERP_SESSION", token)))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/hr/employees/{id}/family-members", actorEmployeeId)
			.cookie(new MockCookie("HRERP_SESSION", token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isForbidden());
	}

	private long workplace(String name, String number) {
		String businessNumber = String.format("%010d", Long.parseLong(number));
		jdbc.update("""
			INSERT INTO platform_workplaces
				(company_id, workplace_name, business_registration_number, address, industry, opened_on)
			VALUES (?, ?, ?, '서울', '서비스', '2020-01-01')
			""", companyId, name + suffix, businessNumber);
		return id("SELECT id FROM platform_workplaces WHERE business_registration_number=?", businessNumber);
	}

	private long department(String code, String name, long workplace, Long parent) {
		jdbc.update("INSERT INTO platform_departments (stable_code) VALUES (?)", code);
		long id = id("SELECT id FROM platform_departments WHERE stable_code=?", code);
		jdbc.update("""
			INSERT INTO platform_department_versions
				(department_id, workplace_id, parent_department_id, department_name, effective_from, version)
			VALUES (?, ?, ?, ?, '2020-01-01', 1)
			""", id, workplace, parent, name + suffix);
		return id;
	}

	private long employee(String discriminator, long workplace, long department) {
		String employeeNumber = Integer.toString(suffix + Integer.parseInt(discriminator));
		jdbc.update("""
			INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
				employment_type, workplace_id, department_id, position_name)
			VALUES (?, '권한테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', ?, ?, '사원')
			""", employeeNumber, workplace, department);
		return id("SELECT id FROM employees WHERE employee_number=?", employeeNumber);
	}

	private long role(String code, PermissionCode permission, long accountId) {
		jdbc.update("INSERT INTO platform_roles (role_code, role_name) VALUES (?, ?)", code, code);
		long roleId = id("SELECT id FROM platform_roles WHERE role_code=?", code);
		jdbc.update("""
			INSERT INTO platform_role_permissions (role_id, permission_id)
			SELECT ?, id FROM platform_permissions WHERE permission_code=?
			""", roleId, permission.name());
		jdbc.update("INSERT INTO platform_account_roles (account_id, role_id, valid_from) VALUES (?, ?, ?)",
			accountId, roleId, Instant.now().minus(1, ChronoUnit.HOURS));
		return roleId;
	}

	private void scope(OrganizationScopeType type, Long organizationId) {
		jdbc.update("DELETE FROM platform_organization_scopes WHERE account_id=?", actorAccountId);
		jdbc.update("""
			INSERT INTO platform_organization_scopes (account_id, scope_type, organization_id)
			VALUES (?, ?, ?)
			""", actorAccountId, type.name(), organizationId);
	}

	private long id(String sql, Object value) {
		return jdbc.queryForObject(sql, Long.class, value);
	}
}
