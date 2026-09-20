package com.jamestaeil.hrerp.platform.organization;

public final class OrganizationConflictException extends RuntimeException {
	private final java.util.List<Long> affectedEmployeeIds;
	public OrganizationConflictException(String message) {
		super(message);
		this.affectedEmployeeIds = java.util.List.of();
	}
	public OrganizationConflictException(String message, java.util.List<Long> affectedEmployeeIds) {
		super(message);
		this.affectedEmployeeIds = java.util.List.copyOf(affectedEmployeeIds);
	}
	public java.util.List<Long> affectedEmployeeIds() {
		return affectedEmployeeIds == null ? java.util.List.of() : affectedEmployeeIds;
	}
}
