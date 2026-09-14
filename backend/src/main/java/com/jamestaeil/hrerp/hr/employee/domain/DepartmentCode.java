package com.jamestaeil.hrerp.hr.employee.domain;

public record DepartmentCode(String value) {

	public DepartmentCode {
		if (value == null || !value.matches("0[1-9]|[1-9][0-9]")) {
			throw new IllegalArgumentException("Department code must be two digits from 01 to 99");
		}
	}
}
