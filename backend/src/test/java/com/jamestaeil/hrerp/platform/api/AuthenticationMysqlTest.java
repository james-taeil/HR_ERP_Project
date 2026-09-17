package com.jamestaeil.hrerp.platform.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jamestaeil.hrerp.platform.account.AccountService;
import com.jamestaeil.hrerp.platform.account.AuthenticationService;
import com.jamestaeil.hrerp.platform.account.AccountStatus;
import com.jamestaeil.hrerp.platform.account.SessionService;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class AuthenticationMysqlTest {
	private static final AtomicInteger NUMBERS = new AtomicInteger(96000000);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry properties) {
		properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
		properties.add("spring.datasource.username",
			() -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
		properties.add("spring.datasource.password",
			() -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
	}

	@Autowired AccountService accounts;
	@Autowired AuthenticationService authentication;
	@Autowired SessionService sessions;
	@Autowired DataSource source;
	@Autowired MockMvc mockMvc;
	private JdbcTemplate jdbc;
	private long employeeId;
	private long accountId;
	private String username;

	@BeforeEach
	void setup() {
		jdbc = new JdbcTemplate(source);
		String employeeNumber = Integer.toString(NUMBERS.incrementAndGet());
		username = "auth." + employeeNumber;
		jdbc.update("""
			INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
				employment_type, workplace_id, department_id, position_name)
			VALUES (?, '인증테스트', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '사원')
			""", employeeNumber);
		employeeId = jdbc.queryForObject(
			"SELECT id FROM employees WHERE employee_number=?", Long.class, employeeNumber);
		accountId = accounts.create(employeeId, username, "correct-password!");
	}

	@AfterEach
	void cleanup() {
		jdbc.update("DELETE FROM platform_login_history WHERE account_id=? OR normalized_username=?", accountId, "missing");
		jdbc.update("DELETE FROM platform_sessions WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_password_history WHERE account_id=?", accountId);
		jdbc.update("DELETE FROM platform_accounts WHERE id=?", accountId);
		jdbc.update("DELETE FROM employees WHERE id=?", employeeId);
	}

	@Test
	void loginSessionCsrfAndLogoutFlowNeverStoresRawToken() throws Exception {
		String missingFailure = mockMvc.perform(post("/api/platform/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"missing\",\"password\":\"wrong-password!\"}"))
			.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		String wrongFailure = mockMvc.perform(post("/api/platform/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"%s\",\"password\":\"wrong-password!\"}".formatted(username)))
			.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		assertEquals(missingFailure, wrongFailure);

		MvcResult login = mockMvc.perform(post("/api/platform/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"%s\",\"password\":\"correct-password!\"}".formatted(username)))
			.andExpect(status().isOk())
			.andExpect(header().string("Set-Cookie", containsString("Secure")))
			.andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
			.andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
			.andReturn();
		String rawToken = login.getResponse().getCookie("HRERP_SESSION").getValue();
		String storedDigest = jdbc.queryForObject(
			"SELECT token_digest FROM platform_sessions WHERE account_id=?", String.class, accountId);
		assertNotEquals(rawToken, storedDigest);
		assertEquals(64, storedDigest.length());

		MockCookie session = new MockCookie("HRERP_SESSION", rawToken);
		mockMvc.perform(get("/api/platform/auth/me").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accountId").value(accountId))
			.andExpect(jsonPath("$.employeeId").value(employeeId));
		mockMvc.perform(post("/api/platform/auth/logout").cookie(session))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/platform/auth/logout").cookie(session).with(csrf()))
			.andExpect(status().isNoContent())
			.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
		mockMvc.perform(get("/api/platform/auth/me").cookie(session))
			.andExpect(status().isUnauthorized());
		assertEquals(3, jdbc.queryForObject(
			"SELECT COUNT(*) FROM platform_login_history WHERE account_id=? OR normalized_username='missing'",
			Integer.class, accountId));
	}

	@Test
	void concurrentFailuresCannotBypassFiveAttemptLock() throws Exception {
		var executor = Executors.newFixedThreadPool(5);
		try {
			List<Callable<Boolean>> attempts = new ArrayList<>();
			for (int index = 0; index < 5; index++) {
				attempts.add(() -> authentication.login(username, "wrong-password!", "127.0.0.1", "test").isEmpty());
			}
			for (var result : executor.invokeAll(attempts)) assertTrue(result.get());
		} finally {
			executor.shutdownNow();
		}

		assertEquals("LOCKED", jdbc.queryForObject(
			"SELECT account_status FROM platform_accounts WHERE id=?", String.class, accountId));
		assertEquals(5, jdbc.queryForObject(
			"SELECT failed_attempts FROM platform_accounts WHERE id=?", Integer.class, accountId));
		assertEquals(5, jdbc.queryForObject(
			"SELECT COUNT(*) FROM platform_login_history WHERE account_id=?", Integer.class, accountId));
	}

	@Test
	void logoutAllAndAccountDisableInvalidateEverySession() throws Exception {
		String first = authentication.login(username, "correct-password!", null, null)
			.orElseThrow().rawToken();
		String second = authentication.login(username, "correct-password!", null, null)
			.orElseThrow().rawToken();

		mockMvc.perform(post("/api/platform/auth/logout-all")
			.cookie(new MockCookie("HRERP_SESSION", first)).with(csrf()))
			.andExpect(status().isNoContent());
		assertTrue(sessions.authenticate(first).isEmpty());
		assertTrue(sessions.authenticate(second).isEmpty());

		String third = authentication.login(username, "correct-password!", null, null)
			.orElseThrow().rawToken();
		accounts.changeStatus(accountId, AccountStatus.DISABLED);
		assertTrue(sessions.authenticate(third).isEmpty());
		assertEquals(3, jdbc.queryForObject(
			"SELECT COUNT(*) FROM platform_sessions WHERE account_id=? AND revoked_at IS NOT NULL",
			Integer.class, accountId));
	}
}
