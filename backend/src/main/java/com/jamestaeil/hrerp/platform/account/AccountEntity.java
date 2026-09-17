package com.jamestaeil.hrerp.platform.account;

import java.time.Instant;
import java.util.Locale;

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
		this.accountStatus = status;
		this.disabledAt = status == AccountStatus.DISABLED ? now : null;
		this.updatedAt = now;
	}

	Long id() { return id; }
	String passwordHash() { return passwordHash; }
	AccountStatus status() { return accountStatus; }

	static String normalize(String username) {
		if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
		return username.strip().toLowerCase(Locale.ROOT);
	}
}
