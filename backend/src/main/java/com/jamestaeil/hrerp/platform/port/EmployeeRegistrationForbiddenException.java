package com.jamestaeil.hrerp.platform.port;

public final class EmployeeRegistrationForbiddenException extends RuntimeException {
	public EmployeeRegistrationForbiddenException() {
		super("Employee registration is forbidden");
	}
}
