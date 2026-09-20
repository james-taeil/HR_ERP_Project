package com.jamestaeil.hrerp.platform.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
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
import com.jamestaeil.hrerp.platform.authorization.AuthorizationDeniedException;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class ReferenceMysqlTest {
	private static final AtomicInteger IDS = new AtomicInteger(99500000);
	@DynamicPropertySource
	static void database(DynamicPropertyRegistry properties) {
		properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
		properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
		properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
	}

	@Autowired DataSource source;
	@Autowired AccountService accounts;
	@Autowired AuthenticationService authentication;
	@Autowired ReferenceService reference;
	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper json;
	private JdbcTemplate jdbc;
	private long accountId;
	private long employeeId;
	private long roleId;
	private long groupId;
	private String number;

	@BeforeEach
	void setup() {
		jdbc = new JdbcTemplate(source);
		number = Integer.toString(IDS.incrementAndGet());
		jdbc.update("""
			INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
			 employment_type, workplace_id, department_id, position_name)
			VALUES (?, '기준정보테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
			""", number);
		employeeId = jdbc.queryForObject("SELECT id FROM employees WHERE employee_number=?", Long.class, number);
		accountId = accounts.create(employeeId, "ref." + number, "correct-password!");
		String role = "REF" + number;
		jdbc.update("INSERT INTO platform_roles (role_code, role_name) VALUES (?, ?)", role, role);
		roleId = jdbc.queryForObject("SELECT id FROM platform_roles WHERE role_code=?", Long.class, role);
		jdbc.update("""
			INSERT INTO platform_role_permissions (role_id, permission_id)
			SELECT ?, id FROM platform_permissions WHERE permission_code='PLATFORM_REFERENCE_MANAGE'
			""", roleId);
		jdbc.update("INSERT INTO platform_account_roles (account_id, role_id, valid_from) VALUES (?, ?, ?)",
			accountId, roleId, Instant.parse("2020-01-01T00:00:00Z"));
	}

	@AfterEach
	void cleanup() {
		jdbc.update("DELETE FROM platform_annual_settings WHERE verified_by=?", accountId);
		if (groupId > 0) {
			jdbc.update("DELETE FROM platform_codes WHERE group_id=?", groupId);
			jdbc.update("DELETE FROM platform_code_groups WHERE id=?", groupId);
		}
		jdbc.update("DELETE FROM platform_sessions WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_login_history WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_account_roles WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_role_permissions WHERE role_id=?", roleId);
		jdbc.update("DELETE FROM platform_roles WHERE id=?", roleId);
		jdbc.update("DELETE FROM platform_password_history WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_accounts WHERE id=?", accountId);
		jdbc.update("DELETE FROM employees WHERE id=?", employeeId);
	}

	@Test
	void codeGroupsAreSeededAndCodesAreUniqueAndDeactivatedWithoutDeletion() throws Exception {
		assertTrue(reference.groups(accountId, 0, 100, false).stream().anyMatch(g -> g.code().equals("POSITION")));
		assertTrue(reference.groups(accountId, 0, 100, false).stream().anyMatch(g -> g.code().equals("JOB_TITLE")));
		assertTrue(reference.groups(accountId, 0, 100, false).stream().anyMatch(g -> g.code().equals("JOB")));
		groupId = reference.createGroup(accountId, "TEST_" + number, "테스트").id();
		assertThrows(ReferenceConflictException.class,
			() -> reference.createGroup(accountId, "TEST_" + number, "중복"));
		long codeId = reference.createCode(accountId, groupId, "LEAD", "책임").id();
		assertThrows(ReferenceConflictException.class,
			() -> reference.createCode(accountId, groupId, "LEAD", "중복"));
		assertEquals(1, reference.codes(accountId, groupId, 0, 10, false).size());
		reference.changeCode(accountId, codeId, null, false);
		assertTrue(reference.codes(accountId, groupId, 0, 10, false).isEmpty());
		assertEquals(1, reference.codes(accountId, groupId, 0, 10, true).size());
		reference.changeGroup(accountId, groupId, null, false);
		assertFalse(reference.groups(accountId, 0, 100, false).stream().anyMatch(g -> g.id() == groupId));
		assertThrows(ReferenceConflictException.class,
			() -> reference.createCode(accountId, groupId, "NEW", "신규"));
		String token = authentication.login("ref." + number, "correct-password!", null, null).orElseThrow().rawToken();
		mockMvc.perform(delete("/api/platform/codes/" + codeId).cookie(new MockCookie("HRERP_SESSION", token))
			.with(csrf())).andExpect(status().isMethodNotAllowed());
		assertEquals(1, reference.codes(accountId, groupId, 0, 10, true).size());
	}

	@Test
	void annualSettingsKeepVersionsAndDoNotFallbackToPriorYear() {
		assertThrows(ReferenceNotFoundException.class,
			() -> reference.currentSetting(accountId, "RATE", 2027, "COMPANY", 1));
		var first = reference.addSetting(accountId, "RATE", 2026, "COMPANY", 1,
			json.readTree("{\"value\":123}"), "법령 A");
		var second = reference.addSetting(accountId, "RATE", 2026, "COMPANY", 1,
			json.readTree("{\"value\":124}"), "법령 B");
		assertEquals(1, first.version());
		assertEquals(2, second.version());
		assertEquals(accountId, second.verifiedBy());
		assertEquals(124, reference.currentSetting(accountId, "RATE", 2026, "COMPANY", 1).value().get("value").asInt());
		assertEquals(2, reference.settingHistory(accountId, "RATE", 2026, "COMPANY", 1, 0, 10).size());
		assertEquals(123, reference.settingHistory(accountId, "RATE", 2026, "COMPANY", 1, 0, 1).getFirst().value().get("value").asInt());
		assertEquals(124, reference.settingHistory(accountId, "RATE", 2026, "COMPANY", 1, 1, 1).getFirst().value().get("value").asInt());
		assertThrows(ReferenceNotFoundException.class,
			() -> reference.currentSetting(accountId, "RATE", 2027, "COMPANY", 1));
	}

	@Test
	void apiRequiresAuthenticationAndPermission() throws Exception {
		mockMvc.perform(get("/api/platform/code-groups")).andExpect(status().isUnauthorized());
		String token = authentication.login("ref." + number, "correct-password!", null, null).orElseThrow().rawToken();
		mockMvc.perform(get("/api/platform/code-groups").cookie(new MockCookie("HRERP_SESSION", token)))
			.andExpect(status().isOk());
		jdbc.update("DELETE FROM platform_role_permissions WHERE role_id=?", roleId);
		assertThrows(AuthorizationDeniedException.class, () -> reference.groups(accountId, 0, 10, false));
		mockMvc.perform(get("/api/platform/code-groups").cookie(new MockCookie("HRERP_SESSION", token)))
			.andExpect(status().isForbidden());
	}
}
