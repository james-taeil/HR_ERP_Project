package com.jamestaeil.hrerp.hr.employee.application;

public final class EmployeeNumberExhaustedException extends RuntimeException {

	public EmployeeNumberExhaustedException(int year) {
		super("Employee number sequence exhausted for year " + year);
	}
}
