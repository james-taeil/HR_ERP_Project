package com.jamestaeil.hrerp.platform.account;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_sessions")
class SessionEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private long accountId;
	@Column(columnDefinition = "char(64)")
	private String tokenDigest;
	private Instant createdAt;
	private Instant lastAccessedAt;
	private Instant expiresAt;
	private Instant revokedAt;

	protected SessionEntity() {}

	SessionEntity(long accountId, String tokenDigest, Instant now, Instant expiresAt) {
		this.accountId = accountId;
		this.tokenDigest = tokenDigest;
		this.createdAt = now;
		this.lastAccessedAt = now;
		this.expiresAt = expiresAt;
	}

	boolean isUsable(Instant now, Duration idleTimeout) {
		return revokedAt == null && expiresAt.isAfter(now) && lastAccessedAt.plus(idleTimeout).isAfter(now);
	}

	void touch(Instant now) {
		lastAccessedAt = now;
	}

	void revoke(Instant now) {
		if (revokedAt == null) revokedAt = now;
	}

	Long id() { return id; }
	long accountId() { return accountId; }
	String tokenDigest() { return tokenDigest; }
	Instant expiresAt() { return expiresAt; }
	Instant revokedAt() { return revokedAt; }
}
