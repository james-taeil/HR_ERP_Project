package com.jamestaeil.hrerp.platform.account;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "platform_accounts")
class AccountEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private long employeeId;
	private String username;
	private String passwordHash;
	@Enumerated(EnumType.STRING)
	private AccountStatus accountStatus;
	private int failedAttempts;
	private Instant lockedUntil;
	private Instant disabledAt;
	private Instant createdAt;
	private Instant updatedAt;
	@Version
	private long version;

	protected AccountEntity() {}

	AccountEntity(long employeeId, String username, String passwordHash, Instant now) {
		if (employeeId <= 0) throw new IllegalArgumentException("employeeId must be positive");
		this.employeeId = employeeId;
		this.username = normalize(username);
		this.passwordHash = passwordHash;
		this.accountStatus = AccountStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void changePassword(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

	void changeStatus(AccountStatus status, Instant now) {
		Objects.requireNonNull(status, "status is required");
		if (status == AccountStatus.LOCKED) {
			throw new IllegalArgumentException("잠금 상태에는 만료 시각이 필요합니다.");
		}
		this.accountStatus = status;
		this.disabledAt = status == AccountStatus.DISABLED ? now : null;
		this.lockedUntil = null;
		this.updatedAt = now;
	}

	void lockUntil(Instant until, Instant now) {
		if (until == null || !until.isAfter(now)) {
			throw new IllegalArgumentException("잠금 만료 시각은 현재보다 이후여야 합니다.");
		}
		this.accountStatus = AccountStatus.LOCKED;
		this.lockedUntil = until;
		this.disabledAt = null;
		this.updatedAt = now;
	}

	boolean prepareForAuthentication(Instant now) {
		if (accountStatus == AccountStatus.LOCKED && lockedUntil != null && !lockedUntil.isAfter(now)) {
			accountStatus = AccountStatus.ACTIVE;
			failedAttempts = 0;
			lockedUntil = null;
			updatedAt = now;
		}
		return accountStatus == AccountStatus.ACTIVE;
	}

	void recordAuthenticationFailure(AuthenticationPolicy policy, Instant now) {
		if (accountStatus != AccountStatus.ACTIVE) return;
		failedAttempts++;
		if (failedAttempts >= policy.maxFailedAttempts()) {
			lockUntil(now.plus(policy.lockDuration()), now);
		} else {
			updatedAt = now;
		}
	}

	void recordAuthenticationSuccess(Instant now) {
		failedAttempts = 0;
		lockedUntil = null;
		accountStatus = AccountStatus.ACTIVE;
		updatedAt = now;
	}

	Long id() { return id; }
	long employeeId() { return employeeId; }
	String username() { return username; }
	String passwordHash() { return passwordHash; }
	AccountStatus status() { return accountStatus; }
	int failedAttempts() { return failedAttempts; }
	Instant lockedUntil() { return lockedUntil; }
	Instant disabledAt() { return disabledAt; }

	static String normalize(String username) {
		if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
		return username.strip().toLowerCase(Locale.ROOT);
	}
}
