package com.jamestaeil.hrerp.platform.authorization;

public final class AuthorizationDeniedException extends RuntimeException {
	public AuthorizationDeniedException() {
		super("Authorization is required");
	}
}
