package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AccountEntityTest {
	private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

	@Test
	void validatesActiveLockedAndDisabledStateTransitions() {
		AccountEntity account = new AccountEntity(7L, "worker", "bcrypt-hash", NOW);
		assertEquals(AccountStatus.ACTIVE, account.status());

		Instant lockExpiry = NOW.plusSeconds(900);
		account.lockUntil(lockExpiry, NOW);
		assertEquals(AccountStatus.LOCKED, account.status());
		assertEquals(lockExpiry, account.lockedUntil());

		account.changeStatus(AccountStatus.DISABLED, NOW.plusSeconds(1));
		assertEquals(AccountStatus.DISABLED, account.status());
		assertEquals(NOW.plusSeconds(1), account.disabledAt());
		assertNull(account.lockedUntil());

		account.changeStatus(AccountStatus.ACTIVE, NOW.plusSeconds(2));
		assertEquals(AccountStatus.ACTIVE, account.status());
		assertNull(account.disabledAt());
	}

	@Test
	void rejectsLockedStateWithoutFutureExpiry() {
		AccountEntity account = new AccountEntity(7L, "worker", "bcrypt-hash", NOW);

		assertThrows(IllegalArgumentException.class,
			() -> account.changeStatus(AccountStatus.LOCKED, NOW));
		assertThrows(IllegalArgumentException.class,
			() -> account.lockUntil(NOW, NOW));
	}
}
