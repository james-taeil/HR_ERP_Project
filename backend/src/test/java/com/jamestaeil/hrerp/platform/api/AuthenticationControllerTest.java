package com.jamestaeil.hrerp.platform.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jamestaeil.hrerp.platform.account.AuthenticationService;
import com.jamestaeil.hrerp.platform.account.AuthenticationService.LoginSuccess;
import com.jamestaeil.hrerp.platform.account.SessionService;

class AuthenticationControllerTest {
	private AuthenticationService authentication;
	private MockMvc mockMvc;

	@BeforeEach
	void setup() {
		authentication = mock(AuthenticationService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(
			authentication, mock(SessionService.class), new SessionCookieSettings("HRERP_SESSION"))).build();
	}

	@Test
	void returnsOneGenericFailureContractForInvalidCredentials() throws Exception {
		when(authentication.login(any(), any(), any(), any())).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/platform/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"username":"unknown","password":"wrong-password!"}
				"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
			.andExpect(jsonPath("$.message").value("Authentication failed"));
	}

	@Test
	void successfulLoginReturnsSummaryAndHardenedOpaqueCookie() throws Exception {
		LoginSuccess success = mock(LoginSuccess.class);
		when(success.accountId()).thenReturn(42L);
		when(success.employeeId()).thenReturn(7L);
		when(success.username()).thenReturn("worker");
		when(success.rawToken()).thenReturn("opaque-token");
		when(success.expiresAt()).thenReturn(Instant.parse("2026-09-17T08:00:00Z"));
		when(authentication.login(eq("worker"), eq("long-password!"), any(), any()))
			.thenReturn(Optional.of(success));

		mockMvc.perform(post("/api/platform/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"username":"worker","password":"long-password!"}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accountId").value(42))
			.andExpect(jsonPath("$.employeeId").value(7))
			.andExpect(header().string("Set-Cookie", containsString("HRERP_SESSION=opaque-token")))
			.andExpect(header().string("Set-Cookie", containsString("Secure")))
			.andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
			.andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
	}
}
