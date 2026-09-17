package com.jamestaeil.hrerp.platform.account;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordPolicy {
	public static final int MIN_LENGTH = 12;
	public static final int HISTORY_LIMIT = 5;

	private PasswordPolicy() {}

	public static void validate(String rawPassword, List<String> recentHashes, PasswordEncoder encoder) {
		if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
			throw new IllegalArgumentException("비밀번호는 12자 이상이어야 합니다.");
		}
		if (recentHashes.stream().limit(HISTORY_LIMIT).anyMatch(hash -> encoder.matches(rawPassword, hash))) {
			throw new IllegalArgumentException("최근 5개 비밀번호는 재사용할 수 없습니다.");
		}
	}
}
