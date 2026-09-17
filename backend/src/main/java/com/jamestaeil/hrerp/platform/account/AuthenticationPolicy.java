package com.jamestaeil.hrerp.platform.account;

import java.time.Duration;

record AuthenticationPolicy(int maxFailedAttempts, Duration lockDuration) {
	AuthenticationPolicy {
		if (maxFailedAttempts < 1) throw new IllegalArgumentException("maxFailedAttempts must be positive");
		if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
			throw new IllegalArgumentException("lockDuration must be positive");
		}
	}
}
