package com.jamestaeil.hrerp.platform.port;

public final class PlatformIntegrationUnavailableException extends RuntimeException {
	public PlatformIntegrationUnavailableException() {
		super("Platform authorization and organization services are not connected");
	}
}
