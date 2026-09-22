package com.jamestaeil.hrerp.platform.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
	private final AccountRepository accounts;
	private final PasswordHistoryRepository history;
	private final PasswordEncoder encoder;
	private final PasswordPolicy passwordPolicy;
	private final SessionRepository sessions;
	private final Clock clock;

	public AccountService(AccountRepository accounts, PasswordHistoryRepository history, PasswordEncoder encoder,
			PasswordPolicy passwordPolicy, SessionRepository sessions, Clock clock) {
		this.accounts = accounts;
		this.history = history;
		this.encoder = encoder;
		this.passwordPolicy = passwordPolicy;
		this.sessions = sessions;
		this.clock = clock;
	}

	@Transactional
	public long create(long employeeId, String username, String rawPassword) {
		passwordPolicy.validate(rawPassword, List.of(), encoder);
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
		var recent = history.findByAccountIdOrderByCreatedAtDescIdDesc(
			accountId, PageRequest.of(0, passwordPolicy.historyLimit())).stream()
			.map(PasswordHistoryEntity::passwordHash)
			.toList();
		passwordPolicy.validate(rawPassword, recent, encoder);
		Instant now = clock.instant();
		account.changePassword(encoder.encode(rawPassword), now);
		history.save(new PasswordHistoryEntity(accountId, account.passwordHash(), now));
	}

	@Transactional
	public void changeStatus(long accountId, AccountStatus status) {
		AccountEntity account = accounts.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
		Instant now = clock.instant();
		account.changeStatus(status, now);
		if (status == AccountStatus.DISABLED) sessions.revokeAllByAccountId(accountId, now);
	}

	@Transactional
	public void scheduleDisableForEmployee(long employeeId, Instant effectiveAt) {
		if (employeeId <= 0) throw new IllegalArgumentException("사원 ID가 올바르지 않습니다.");
		AccountEntity account = accounts.findByEmployeeId(employeeId)
			.orElseThrow(() -> new IllegalArgumentException("연결된 계정을 찾을 수 없습니다."));
		Instant now = clock.instant();
		account.scheduleDisable(effectiveAt, now);
		if (!effectiveAt.isAfter(now)) sessions.revokeAllByAccountId(account.id(), now);
	}

	@Transactional
	public void lock(long accountId, Instant until) {
		accounts.findById(accountId)
			.orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."))
			.lockUntil(until, clock.instant());
	}
}
