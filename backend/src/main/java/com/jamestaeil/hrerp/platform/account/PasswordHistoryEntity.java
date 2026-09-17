package com.jamestaeil.hrerp.platform.account;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_password_history")
class PasswordHistoryEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private long accountId;
	private String passwordHash;
	private Instant createdAt;

	protected PasswordHistoryEntity() {}

	PasswordHistoryEntity(long accountId, String passwordHash, Instant createdAt) {
		this.accountId = accountId;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	String passwordHash() { return passwordHash; }
}
