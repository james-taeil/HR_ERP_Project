package com.jamestaeil.hrerp.platform.api;

record SessionCookieSettings(String name) {
	SessionCookieSettings {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("session cookie name is required");
	}
}
