package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
	private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
	@Mock AccountRepository accounts;
	@Mock LoginHistoryRepository history;
	@Mock SessionService sessions;
	private BCryptPasswordEncoder encoder;
	private AuthenticationService service;

	@BeforeEach
	void setup() {
		encoder = new BCryptPasswordEncoder(4);
		service = new AuthenticationService(accounts, history, encoder,
			new AuthenticationPolicy(5, Duration.ofMinutes(15)), sessions,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void missingAccountAndWrongPasswordHaveTheSameResult() {
		when(accounts.findLockedByUsername("missing")).thenReturn(Optional.empty());
		AccountEntity account = account(42L, "worker", "correct-password!");
		when(accounts.findLockedByUsername("worker")).thenReturn(Optional.of(account));

		assertTrue(service.login("missing", "wrong-password!", "127.0.0.1", "test").isEmpty());
		assertTrue(service.login("worker", "wrong-password!", "127.0.0.1", "test").isEmpty());
		verify(history, times(2)).save(any(LoginHistoryEntity.class));
	}

	@Test
	void fifthFailureLocksAccountForConfiguredDuration() {
		AccountEntity account = account(42L, "worker", "correct-password!");
		when(accounts.findLockedByUsername("worker")).thenReturn(Optional.of(account));

		for (int attempt = 0; attempt < 5; attempt++) {
			assertTrue(service.login("worker", "wrong-password!", null, null).isEmpty());
		}

		assertEquals(AccountStatus.LOCKED, account.status());
		assertEquals(NOW.plus(Duration.ofMinutes(15)), account.lockedUntil());
		assertEquals(5, account.failedAttempts());
	}

	@Test
	void successfulLoginResetsFailuresAndCreatesOpaqueSession() {
		AccountEntity account = account(42L, "worker", "correct-password!");
		account.recordAuthenticationFailure(new AuthenticationPolicy(5, Duration.ofMinutes(15)), NOW);
		when(accounts.findLockedByUsername("worker")).thenReturn(Optional.of(account));
		when(sessions.create(42L)).thenReturn(
			new SessionService.SessionGrant("opaque-token", NOW.plus(Duration.ofHours(8))));

		AuthenticationService.LoginSuccess result = service.login(
			" Worker ", "correct-password!", "127.0.0.1", "test").orElseThrow();

		assertEquals(0, account.failedAttempts());
		assertEquals("opaque-token", result.rawToken());
		assertEquals(NOW.plus(Duration.ofHours(8)), result.expiresAt());
	}

	private AccountEntity account(long id, String username, String rawPassword) {
		AccountEntity account = new AccountEntity(7L, username, encoder.encode(rawPassword), NOW);
		ReflectionTestUtils.setField(account, "id", id);
		return account;
	}
}
