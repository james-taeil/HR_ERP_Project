package com.jamestaeil.hrerp.hr.employee.api;

import java.time.LocalDate;

import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeCommand;
import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterEmployeeRequest(
	@NotBlank @Size(max = 100) String idempotencyKey,
	@NotBlank @Size(max = 100) String name,
	@NotNull LocalDate birthDate,
	@NotBlank @Size(max = 30) String phone,
	@NotNull LocalDate hireDate,
	@NotNull EmploymentType employmentType,
	@Positive long workplaceId,
	@Positive long departmentId,
	@NotBlank @Size(max = 100) String position,
	LocalDate probationEndDate,
	boolean foreignWorker,
	String nationality,
	String visaType,
	LocalDate stayFrom,
	LocalDate stayUntil,
	String alienRegistrationNumber
) {
	RegisterEmployeeCommand toCommand() {
		return new RegisterEmployeeCommand(idempotencyKey, name, birthDate, phone, hireDate, employmentType,
			workplaceId, departmentId, position, probationEndDate, foreignWorker, nationality, visaType,
			stayFrom, stayUntil, alienRegistrationNumber);
	}

	@Override
	public String toString() {
		return "RegisterEmployeeRequest[REDACTED]";
	}
}
