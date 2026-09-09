package com.jamestaeil.hrerp.hr.employee.domain;

public record EmployeeNumber(String value) {

	public EmployeeNumber {
		if (value == null || !value.matches("[0-9]{8}")) {
			throw new IllegalArgumentException("Employee number must contain exactly eight digits");
		}
	}
}
