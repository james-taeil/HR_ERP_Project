package com.jamestaeil.hrerp.platform.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
	private static final int TOKEN_BYTES = 32;
	private final SessionRepository sessions;
	private final AccountRepository accounts;
	private final SessionPolicy policy;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	SessionService(SessionRepository sessions, AccountRepository accounts, SessionPolicy policy, Clock clock) {
		this.sessions = sessions;
		this.accounts = accounts;
		this.policy = policy;
		this.clock = clock;
	}

	@Transactional
	SessionGrant create(long accountId) {
		byte[] tokenBytes = new byte[TOKEN_BYTES];
		random.nextBytes(tokenBytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		Instant now = clock.instant();
		Instant expiresAt = now.plus(policy.absoluteTimeout());
		sessions.save(new SessionEntity(accountId, digest(rawToken), now, expiresAt));
		return new SessionGrant(rawToken, expiresAt);
	}

	@Transactional
	public Optional<SessionPrincipal> authenticate(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) return Optional.empty();
		Instant now = clock.instant();
		Optional<SessionEntity> found = sessions.findByTokenDigest(digest(rawToken));
		if (found.isEmpty()) return Optional.empty();
		SessionEntity session = found.get();
		Optional<AccountEntity> account = accounts.findById(session.accountId());
		if (account.isEmpty() || !account.get().prepareForAuthentication(now)
				|| !session.isUsable(now, policy.idleTimeout())) {
			if (account.isPresent() && account.get().status() == AccountStatus.DISABLED) {
				sessions.revokeAllByAccountId(account.get().id(), now);
			}
			session.revoke(now);
			return Optional.empty();
		}
		session.touch(now);
		AccountEntity active = account.get();
		return Optional.of(new SessionPrincipal(session.id(), active.id(), active.employeeId(), active.username()));
	}

	@Transactional
	public void revoke(long sessionId) {
		sessions.findById(sessionId).ifPresent(session -> session.revoke(clock.instant()));
	}

	@Transactional
	public void revokeAll(long accountId) {
		sessions.revokeAllByAccountId(accountId, clock.instant());
	}

	static String digest(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	static final class SessionGrant {
		private final String rawToken;
		private final Instant expiresAt;

		SessionGrant(String rawToken, Instant expiresAt) {
			this.rawToken = rawToken;
			this.expiresAt = expiresAt;
		}

		String rawToken() { return rawToken; }
		Instant expiresAt() { return expiresAt; }
	}
}
