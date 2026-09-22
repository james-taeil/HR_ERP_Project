package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {
	private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
	@Mock SessionRepository sessions;
	@Mock AccountRepository accounts;
	private SessionService service;

	@BeforeEach
	void setup() {
		service = new SessionService(sessions, accounts,
			new SessionPolicy(Duration.ofHours(8), Duration.ofMinutes(30)),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void storesOnlySha256DigestOfRandomToken() {
		SessionService.SessionGrant grant = service.create(42L);
		ArgumentCaptor<SessionEntity> saved = ArgumentCaptor.forClass(SessionEntity.class);
		verify(sessions).save(saved.capture());

		assertNotEquals(grant.rawToken(), saved.getValue().tokenDigest());
		assertEquals(SessionService.digest(grant.rawToken()), saved.getValue().tokenDigest());
		assertEquals(64, saved.getValue().tokenDigest().length());
		assertEquals(NOW.plus(Duration.ofHours(8)), grant.expiresAt());
	}

	@Test
	void authenticatesActiveAccountAndRejectsExpiredSession() {
		SessionEntity activeSession = session(9L, NOW, NOW.plus(Duration.ofHours(8)));
		AccountEntity activeAccount = account(42L, AccountStatus.ACTIVE);
		when(sessions.findByTokenDigest(SessionService.digest("raw"))).thenReturn(Optional.of(activeSession));
		when(accounts.findById(42L)).thenReturn(Optional.of(activeAccount));

		SessionPrincipal principal = service.authenticate("raw").orElseThrow();
		assertEquals(9L, principal.sessionId());
		assertEquals(7L, principal.employeeId());

		SessionEntity expired = session(10L, NOW.minus(Duration.ofHours(9)), NOW.minusSeconds(1));
		when(sessions.findByTokenDigest(SessionService.digest("expired"))).thenReturn(Optional.of(expired));
		assertTrue(service.authenticate("expired").isEmpty());
		assertEquals(NOW, expired.revokedAt());
	}

	@Test
	void rejectsSessionAsSoonAsAccountIsDisabled() {
		SessionEntity session = session(9L, NOW, NOW.plus(Duration.ofHours(8)));
		AccountEntity disabled = account(42L, AccountStatus.DISABLED);
		when(sessions.findByTokenDigest(SessionService.digest("raw"))).thenReturn(Optional.of(session));
		when(accounts.findById(42L)).thenReturn(Optional.of(disabled));

		assertTrue(service.authenticate("raw").isEmpty());
		assertEquals(NOW, session.revokedAt());
	}

	@Test
	void rejectsAndRevokesAllSessionsAtScheduledDisableInstant() {
		SessionEntity session = session(9L, NOW.minusSeconds(60), NOW.plus(Duration.ofHours(8)));
		AccountEntity account = account(42L, AccountStatus.ACTIVE);
		account.scheduleDisable(NOW, NOW.minusSeconds(60));
		when(sessions.findByTokenDigest(SessionService.digest("scheduled"))).thenReturn(Optional.of(session));
		when(accounts.findById(42L)).thenReturn(Optional.of(account));

		assertTrue(service.authenticate("scheduled").isEmpty());
		assertEquals(AccountStatus.DISABLED, account.status());
		verify(sessions).revokeAllByAccountId(42L, NOW);
	}

	@Test
	void rejectsSessionAfterIdleTimeoutEvenBeforeAbsoluteExpiry() {
		SessionEntity idle = session(9L, NOW.minus(Duration.ofMinutes(31)), NOW.plus(Duration.ofHours(7)));
		AccountEntity active = account(42L, AccountStatus.ACTIVE);
		when(sessions.findByTokenDigest(SessionService.digest("idle"))).thenReturn(Optional.of(idle));
		when(accounts.findById(42L)).thenReturn(Optional.of(active));

		assertTrue(service.authenticate("idle").isEmpty());
		assertEquals(NOW, idle.revokedAt());
	}

	private SessionEntity session(long id, Instant createdAt, Instant expiresAt) {
		SessionEntity session = new SessionEntity(42L, SessionService.digest("stored"), createdAt, expiresAt);
		ReflectionTestUtils.setField(session, "id", id);
		return session;
	}

	private AccountEntity account(long id, AccountStatus status) {
		AccountEntity account = new AccountEntity(7L, "worker", "hash", NOW);
		ReflectionTestUtils.setField(account, "id", id);
		if (status == AccountStatus.DISABLED) account.changeStatus(status, NOW);
		return account;
	}
}
