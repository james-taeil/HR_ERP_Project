package com.jamestaeil.hrerp.platform.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
	private final AccountRepository accounts;
	private final LoginHistoryRepository history;
	private final PasswordEncoder encoder;
	private final AuthenticationPolicy policy;
	private final SessionService sessions;
	private final Clock clock;
	private final String dummyPasswordHash;

	AuthenticationService(AccountRepository accounts, LoginHistoryRepository history, PasswordEncoder encoder,
			AuthenticationPolicy policy, SessionService sessions, Clock clock) {
		this.accounts = accounts;
		this.history = history;
		this.encoder = encoder;
		this.policy = policy;
		this.sessions = sessions;
		this.clock = clock;
		this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public Optional<LoginSuccess> login(String username, String rawPassword, String ipAddress, String userAgent) {
		String normalizedUsername = AccountEntity.normalize(username);
		Instant now = clock.instant();
		Optional<AccountEntity> found = accounts.findLockedByUsername(normalizedUsername);
		if (found.isEmpty()) {
			encoder.matches(rawPassword, dummyPasswordHash);
			history.save(new LoginHistoryEntity(null, normalizedUsername, false, now, ipAddress, userAgent));
			return Optional.empty();
		}

		AccountEntity account = found.get();
		boolean available = account.prepareForAuthentication(now);
		boolean passwordMatches = encoder.matches(rawPassword, account.passwordHash());
		if (!available || !passwordMatches) {
			if (!available && account.status() == AccountStatus.DISABLED) sessions.revokeAll(account.id());
			if (available) account.recordAuthenticationFailure(policy, now);
			history.save(new LoginHistoryEntity(account.id(), normalizedUsername, false, now, ipAddress, userAgent));
			return Optional.empty();
		}

		account.recordAuthenticationSuccess(now);
		history.save(new LoginHistoryEntity(account.id(), normalizedUsername, true, now, ipAddress, userAgent));
		SessionService.SessionGrant session = sessions.create(account.id());
		return Optional.of(new LoginSuccess(account.id(), account.employeeId(), account.username(),
			session.rawToken(), session.expiresAt()));
	}

	public static final class LoginSuccess {
		private final long accountId;
		private final long employeeId;
		private final String username;
		private final String rawToken;
		private final Instant expiresAt;

		LoginSuccess(long accountId, long employeeId, String username, String rawToken, Instant expiresAt) {
			this.accountId = accountId;
			this.employeeId = employeeId;
			this.username = username;
			this.rawToken = rawToken;
			this.expiresAt = expiresAt;
		}

		public long accountId() { return accountId; }
		public long employeeId() { return employeeId; }
		public String username() { return username; }
		public String rawToken() { return rawToken; }
		public Instant expiresAt() { return expiresAt; }
	}
}
