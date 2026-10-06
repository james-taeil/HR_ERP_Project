package com.jamestaeil.hrerp.hr.employee.application;

import java.time.LocalDate;

import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.employee.domain.Gender;

public record RegisterEmployeeCommand(
	String idempotencyKey,
	String name,
	LocalDate birthDate,
	Gender gender,
	String phone,
	String address,
	LocalDate hireDate,
	EmploymentType employmentType,
	long workplaceId,
	long departmentId,
	String position,
	LocalDate probationEndDate,
	boolean foreignWorker,
	String nationality,
	String visaType,
	LocalDate stayFrom,
	LocalDate stayUntil,
	String alienRegistrationNumber
) {
	@Override
	public String toString() {
		return "RegisterEmployeeCommand[REDACTED]";
	}
}
