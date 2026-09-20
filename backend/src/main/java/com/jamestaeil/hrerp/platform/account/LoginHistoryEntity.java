package com.jamestaeil.hrerp.platform.account;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_login_history")
class LoginHistoryEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private Long accountId;
	private String normalizedUsername;
	private boolean success;
	private Instant occurredAt;
	private String ipAddress;
	private String userAgent;

	protected LoginHistoryEntity() {}

	LoginHistoryEntity(Long accountId, String normalizedUsername, boolean success, Instant occurredAt,
			String ipAddress, String userAgent) {
		this.accountId = accountId;
		this.normalizedUsername = normalizedUsername;
		this.success = success;
		this.occurredAt = occurredAt;
		this.ipAddress = truncate(ipAddress, 45);
		this.userAgent = truncate(userAgent, 500);
	}

	private static String truncate(String value, int limit) {
		if (value == null || value.isBlank()) return null;
		return value.length() <= limit ? value : value.substring(0, limit);
	}
}
