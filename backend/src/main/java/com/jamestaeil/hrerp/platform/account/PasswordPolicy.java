package com.jamestaeil.hrerp.platform.account;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordPolicy {
	private final int minimumLength;
	private final int historyLimit;

	PasswordPolicy(int minimumLength, int historyLimit) {
		if (minimumLength < 1) throw new IllegalArgumentException("minimumLength must be positive");
		if (historyLimit < 1) throw new IllegalArgumentException("historyLimit must be positive");
		this.minimumLength = minimumLength;
		this.historyLimit = historyLimit;
	}

	public void validate(String rawPassword, List<String> recentHashes, PasswordEncoder encoder) {
		if (rawPassword == null || rawPassword.length() < minimumLength) {
			throw new IllegalArgumentException("비밀번호는 " + minimumLength + "자 이상이어야 합니다.");
		}
		if (recentHashes.stream().limit(historyLimit).anyMatch(hash -> encoder.matches(rawPassword, hash))) {
			throw new IllegalArgumentException("최근 " + historyLimit + "개 비밀번호는 재사용할 수 없습니다.");
		}
	}

	int historyLimit() {
		return historyLimit;
	}
}
