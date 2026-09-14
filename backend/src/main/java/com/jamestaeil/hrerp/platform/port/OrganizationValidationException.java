package com.jamestaeil.hrerp.platform.port;

public final class OrganizationValidationException extends RuntimeException {
	public OrganizationValidationException() {
		super("Workplace or department is invalid");
	}
}
