package com.jamestaeil.hrerp.platform.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
	private final AccountRepository accounts;
	private final PasswordHistoryRepository history;
	private final PasswordEncoder encoder;
	private final Clock clock;

	public AccountService(AccountRepository accounts, PasswordHistoryRepository history, PasswordEncoder encoder, Clock clock) {
		this.accounts = accounts;
		this.history = history;
		this.encoder = encoder;
		this.clock = clock;
	}

	@Transactional
	public long create(long employeeId, String username, String rawPassword) {
		PasswordPolicy.validate(rawPassword, List.of(), encoder);
		if (accounts.findByUsername(AccountEntity.normalize(username)).isPresent()) {
			throw new IllegalArgumentException("이미 사용 중인 사용자명입니다.");
		}
		Instant now = clock.instant();
		AccountEntity account = accounts.save(new AccountEntity(employeeId, username, encoder.encode(rawPassword), now));
		history.save(new PasswordHistoryEntity(account.id(), account.passwordHash(), now));
		return account.id();
	}

	@Transactional
	public void changePassword(long accountId, String rawPassword) {
		AccountEntity account = accounts.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
		List<String> recent = history.findTop5ByAccountIdOrderByCreatedAtDescIdDesc(accountId).stream()
			.map(PasswordHistoryEntity::passwordHash)
			.toList();
		PasswordPolicy.validate(rawPassword, recent, encoder);
		Instant now = clock.instant();
		account.changePassword(encoder.encode(rawPassword), now);
		history.save(new PasswordHistoryEntity(accountId, account.passwordHash(), now));
	}

	@Transactional
	public void changeStatus(long accountId, AccountStatus status) {
		accounts.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."))
			.changeStatus(status, clock.instant());
	}
}
