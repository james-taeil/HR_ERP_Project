package com.jamestaeil.hrerp.platform.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jamestaeil.hrerp.platform.account.AccountService;
import com.jamestaeil.hrerp.platform.account.AuthenticationService;
import com.jamestaeil.hrerp.platform.authorization.OrganizationScopeType;
import com.jamestaeil.hrerp.platform.port.OrganizationValidationException;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class OrganizationMysqlTest {
	private static final AtomicInteger NUMBERS = new AtomicInteger(99000000);
	private static final LocalDate START = LocalDate.of(2020, 1, 1);

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
	@Autowired MockMvc mockMvc;
	@Autowired OrganizationService organization;
	@Autowired PlatformOrganizationReader reader;
	@Autowired PlatformOrganizationSnapshotReader snapshots;
	private JdbcTemplate jdbc;
	private long actorEmployeeId;
	private long accountId;
	private long roleId;
	private long companyId;
	private long workplaceId;
	private long otherWorkplaceId;
	private long parentId;
	private long childId;
	private String employeeNumber;

	@BeforeEach
	void setup() {
		jdbc = new JdbcTemplate(source);
		employeeNumber = Integer.toString(NUMBERS.incrementAndGet());
		jdbc.update("""
			INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
				employment_type, workplace_id, department_id, position_name)
			VALUES (?, '조직테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
			""", employeeNumber);
		actorEmployeeId = jdbc.queryForObject("SELECT id FROM employees WHERE employee_number=?",
			Long.class, employeeNumber);
		accountId = accounts.create(actorEmployeeId, "org." + employeeNumber, "correct-password!");
		String roleCode = "ORG" + employeeNumber;
		jdbc.update("INSERT INTO platform_roles (role_code, role_name) VALUES (?, ?)", roleCode, roleCode);
		roleId = jdbc.queryForObject("SELECT id FROM platform_roles WHERE role_code=?", Long.class, roleCode);
		jdbc.update("""
			INSERT INTO platform_role_permissions (role_id, permission_id)
			SELECT ?, id FROM platform_permissions
			WHERE permission_code IN ('PLATFORM_ORGANIZATION_MANAGE', 'HR_LIFECYCLE_READ')
			""", roleId);
		jdbc.update("INSERT INTO platform_account_roles (account_id, role_id, valid_from) VALUES (?, ?, ?)",
			accountId, roleId, Instant.parse("2020-01-01T00:00:00Z"));
	}

	@AfterEach
	void cleanup() {
		jdbc.update("DELETE FROM platform_sessions WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_login_history WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_organization_scopes WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_account_roles WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_role_permissions WHERE role_id=?", roleId);
		jdbc.update("DELETE FROM platform_roles WHERE id=?", roleId);
		jdbc.update("DELETE FROM platform_password_history WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_accounts WHERE id=?", accountId);
		jdbc.update("DELETE FROM employees WHERE id=?", actorEmployeeId);
		if (childId > 0) {
			jdbc.update("DELETE FROM platform_department_versions WHERE department_id=?", childId);
			jdbc.update("DELETE FROM platform_departments WHERE id=?", childId);
		}
		if (parentId > 0) {
			jdbc.update("DELETE FROM platform_department_versions WHERE department_id=?", parentId);
			jdbc.update("DELETE FROM platform_departments WHERE id=?", parentId);
		}
		if (otherWorkplaceId > 0) jdbc.update("DELETE FROM platform_workplaces WHERE id=?", otherWorkplaceId);
		if (workplaceId > 0) jdbc.update("DELETE FROM platform_workplaces WHERE id=?", workplaceId);
		if (companyId > 0) jdbc.update("DELETE FROM platform_companies WHERE id=?", companyId);
	}

	@Test
	void enforcesSingleCompanyNormalizedBusinessNumberAndActivePeriods() {
		companyId = organization.createCompany(accountId, "회사", START, null).id();
		assertThrows(OrganizationConflictException.class,
			() -> organization.createCompany(accountId, "다른 회사", START, null));
		workplaceId = organization.createWorkplace(accountId, companyId, "본사", "123-45-67890",
			"서울", "서비스", START, null).id();
		assertEquals("1234567890", organization.workplaces(accountId, 0, 10).getFirst().registrationNumber());
		assertThrows(OrganizationConflictException.class,
			() -> organization.createWorkplace(accountId, companyId, "중복", "1234567890",
				"서울", "서비스", START, null));
		assertThrows(IllegalArgumentException.class,
			() -> organization.createWorkplace(accountId, companyId, "기간 오류", "9999999999",
				"서울", "서비스", START, START.minusDays(1)));
	}

	@Test
	void preservesHistoricalVersionsAndRejectsCyclesAndWrongWorkplaces() {
		createBase();
		parentId = organization.createDepartment(accountId, "41", "인사", workplaceId, null, START, 20)
			.departmentId();
		childId = organization.createDepartment(accountId, "42", "운영", workplaceId, parentId, START, 5)
			.departmentId();
		LocalDate moved = LocalDate.of(2026, 1, 1);
		organization.addVersion(accountId, childId, "운영지원", workplaceId, parentId, moved, null, 7);
		assertEquals("운영", organization.tree(accountId, moved.minusDays(1)).stream()
			.filter(d -> d.departmentId() == childId).findFirst().orElseThrow().name());
		assertEquals("운영지원", organization.tree(accountId, moved).stream()
			.filter(d -> d.departmentId() == childId).findFirst().orElseThrow().name());
		assertThrows(OrganizationConflictException.class,
			() -> organization.addVersion(accountId, childId, "겹침", workplaceId, parentId, START, null, 7));
		assertThrows(OrganizationConflictException.class,
			() -> organization.addVersion(accountId, parentId, "순환", workplaceId, childId,
				LocalDate.of(2027, 1, 1), null, 20));
		assertThrows(OrganizationConflictException.class,
			() -> organization.addVersion(accountId, childId, "사업장 불일치", otherWorkplaceId, parentId,
				LocalDate.of(2027, 1, 1), null, 7));
		assertThrows(OrganizationConflictException.class,
			() -> organization.addVersion(accountId, parentId, "자식 잔류", otherWorkplaceId, null,
				LocalDate.of(2027, 1, 1), null, 20));
		assertThrows(OrganizationConflictException.class,
			() -> organization.closeDepartment(accountId, parentId, LocalDate.of(2027, 1, 1)));
		jdbc.update("UPDATE employees SET workplace_id=?, department_id=? WHERE id=?",
			workplaceId, childId, actorEmployeeId);
		OrganizationConflictException occupied = assertThrows(OrganizationConflictException.class,
			() -> organization.closeDepartment(accountId, childId, LocalDate.of(2027, 1, 1)));
		assertEquals(List.of(actorEmployeeId), occupied.affectedEmployeeIds());
		jdbc.update("UPDATE employees SET workplace_id=1, department_id=1 WHERE id=?", actorEmployeeId);
		organization.closeDepartment(accountId, childId, LocalDate.of(2027, 1, 1));
		organization.closeDepartment(accountId, parentId, LocalDate.of(2027, 1, 2));
		assertTrue(organization.tree(accountId, LocalDate.of(2027, 1, 3)).isEmpty());
		assertEquals(2, organization.tree(accountId, LocalDate.of(2026, 12, 31)).size());
	}

	@Test
	void connectsCurrentAndSnapshotPortsAndRejectsInvalidDepartment() {
		createBase();
		parentId = organization.createDepartment(accountId, "41", "인사", workplaceId, null, START, 20)
			.departmentId();
		childId = organization.createDepartment(accountId, "42", "운영", workplaceId, parentId, START, 5)
			.departmentId();
		assertEquals("41", reader.requireActiveDepartment(workplaceId, parentId).value());
		assertThrows(OrganizationValidationException.class,
			() -> reader.requireActiveDepartment(otherWorkplaceId, parentId));
		jdbc.update("INSERT INTO platform_organization_scopes (account_id, scope_type, organization_id) VALUES (?, ?, ?)",
			accountId, OrganizationScopeType.DEPARTMENT_TREE.name(), parentId);
		assertEquals(2, snapshots.departmentsOn(accountId, LocalDate.now()).size());
		jdbc.update("DELETE FROM platform_organization_scopes WHERE account_id=?", accountId);
		jdbc.update("INSERT INTO platform_organization_scopes (account_id, scope_type) VALUES (?, 'SELF')", accountId);
		assertTrue(snapshots.departmentsOn(accountId, LocalDate.now()).isEmpty());
		jdbc.update("DELETE FROM platform_organization_scopes WHERE account_id=?", accountId);
		assertTrue(snapshots.departmentsOn(accountId, LocalDate.now()).isEmpty());
	}

	@Test
	void concurrentVersionChangesCreateOnlyOneNewVersion() throws Exception {
		createBase();
		parentId = organization.createDepartment(accountId, "41", "인사", workplaceId, null, START, 20)
			.departmentId();
		LocalDate changeDate = LocalDate.of(2027, 1, 1);
		var workers = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Boolean>> updates = List.of(
				() -> tryVersion("인사기획", changeDate),
				() -> tryVersion("인사운영", changeDate));
			long successes = 0;
			for (var outcome : workers.invokeAll(updates)) if (outcome.get()) successes++;
			assertEquals(1, successes);
		} finally {
			workers.shutdownNow();
		}
		assertEquals(2, jdbc.queryForObject(
			"SELECT COUNT(*) FROM platform_department_versions WHERE department_id=?", Integer.class, parentId));
	}

	@Test
	void organizationApiRequiresSessionAuthentication() throws Exception {
		mockMvc.perform(get("/api/platform/departments/tree"))
			.andExpect(status().isUnauthorized());
		String token = authentication.login("org." + employeeNumber, "correct-password!", null, null)
			.orElseThrow().rawToken();
		mockMvc.perform(get("/api/platform/departments/tree")
			.cookie(new MockCookie("HRERP_SESSION", token)))
			.andExpect(status().isOk());
	}

	private boolean tryVersion(String name, LocalDate date) {
		try {
			organization.addVersion(accountId, parentId, name, workplaceId, null, date, null, 20);
			return true;
		} catch (OrganizationConflictException exception) {
			return false;
		}
	}

	private void createBase() {
		companyId = organization.createCompany(accountId, "회사", START, null).id();
		workplaceId = organization.createWorkplace(accountId, companyId, "본사", "1234567890",
			"서울", "서비스", START, null).id();
		otherWorkplaceId = organization.createWorkplace(accountId, companyId, "지사", "9876543210",
			"부산", "서비스", START, null).id();
	}
}
