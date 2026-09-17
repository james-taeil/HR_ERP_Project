package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
	private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

	@Mock AccountRepository accounts;
	@Mock PasswordHistoryRepository history;
	private BCryptPasswordEncoder encoder;
	private AccountService service;

	@BeforeEach
	void setup() {
		encoder = new BCryptPasswordEncoder(4);
		service = new AccountService(accounts, history, encoder, new PasswordPolicy(12, 5),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsAccountWithNormalizedUsernameAndOnlyPersistsPasswordHash() {
		AccountEntity saved = mock(AccountEntity.class);
		when(accounts.findByUsername("worker")).thenReturn(Optional.empty());
		when(saved.id()).thenReturn(42L);
		when(saved.passwordHash()).thenReturn("stored-hash");
		when(accounts.save(any(AccountEntity.class))).thenReturn(saved);

		service.create(7L, " Worker ", "long-password!");

		verify(accounts).save(argThat(account ->
			!account.passwordHash().equals("long-password!")
				&& encoder.matches("long-password!", account.passwordHash())));
		verify(history).save(argThat(item -> item.passwordHash().equals("stored-hash")));
	}

	@Test
	void rejectsAnyConfiguredRecentPasswordBeforeUpdatingAccount() {
		AccountEntity account = new AccountEntity(7L, "worker", encoder.encode("current-pass!"), NOW);
		when(accounts.findById(42L)).thenReturn(Optional.of(account));
		when(history.findByAccountIdOrderByCreatedAtDescIdDesc(
			eq(42L), argThat(page -> page.getPageSize() == 5)))
			.thenReturn(List.of(new PasswordHistoryEntity(42L, encoder.encode("reused-pass!"), NOW)));

		assertThrows(IllegalArgumentException.class, () -> service.changePassword(42L, "reused-pass!"));

		verify(accounts).findById(42L);
	}
}
