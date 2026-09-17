package com.jamestaeil.hrerp.platform.account;

import java.time.Duration;

record SessionPolicy(Duration absoluteTimeout, Duration idleTimeout) {
	SessionPolicy {
		if (absoluteTimeout == null || absoluteTimeout.isZero() || absoluteTimeout.isNegative()) {
			throw new IllegalArgumentException("absoluteTimeout must be positive");
		}
		if (idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()) {
			throw new IllegalArgumentException("idleTimeout must be positive");
		}
		if (idleTimeout.compareTo(absoluteTimeout) > 0) {
			throw new IllegalArgumentException("idleTimeout cannot exceed absoluteTimeout");
		}
	}
}
