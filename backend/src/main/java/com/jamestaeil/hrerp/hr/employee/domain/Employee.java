package com.jamestaeil.hrerp.hr.employee.domain;

import java.time.LocalDate;

public final class Employee {

	private final EmployeeNumber employeeNumber;
	private final String name;
	private final LocalDate birthDate;
	private final Gender gender;
	private final String phone;
	private final String address;
	private final LocalDate hireDate;
	private final EmploymentType employmentType;
	private final long workplaceId;
	private final long departmentId;
	private final String position;
	private final LocalDate probationEndDate;
	private final ForeignWorkerDetails foreignWorkerDetails;

	public Employee(EmployeeNumber employeeNumber, String name, LocalDate birthDate, Gender gender, String phone,
		String address,
		LocalDate hireDate, EmploymentType employmentType, long workplaceId, long departmentId,
		String position, LocalDate probationEndDate, ForeignWorkerDetails foreignWorkerDetails) {
		this.employeeNumber = require(employeeNumber, "Employee number");
		this.name = requireText(name, "Name");
		this.birthDate = require(birthDate, "Birth date");
		this.gender = require(gender, "Gender");
		if (gender == Gender.UNSPECIFIED) throw new IllegalArgumentException("Gender must be specified");
		this.phone = requireText(phone, "Phone");
		this.address = requireText(address, "Address");
		if (this.address.length() > 500) throw new IllegalArgumentException("Address is too long");
		this.hireDate = require(hireDate, "Hire date");
		this.employmentType = require(employmentType, "Employment type");
		if (workplaceId <= 0 || departmentId <= 0) {
			throw new IllegalArgumentException("Workplace and department are required");
		}
		this.workplaceId = workplaceId;
		this.departmentId = departmentId;
		this.position = requireText(position, "Position");
		if (probationEndDate != null && probationEndDate.isBefore(hireDate)) {
			throw new IllegalArgumentException("Probation end date cannot precede hire date");
		}
		this.probationEndDate = probationEndDate;
		this.foreignWorkerDetails = foreignWorkerDetails;
	}

	public EmployeeNumber employeeNumber() { return employeeNumber; }
	public String name() { return name; }
	public LocalDate birthDate() { return birthDate; }
	public Gender gender() { return gender; }
	public String phone() { return phone; }
	public String address() { return address; }
	public LocalDate hireDate() { return hireDate; }
	public EmploymentType employmentType() { return employmentType; }
	public long workplaceId() { return workplaceId; }
	public long departmentId() { return departmentId; }
	public String position() { return position; }
	public LocalDate probationEndDate() { return probationEndDate; }
	public boolean foreignWorker() { return foreignWorkerDetails != null; }
	public ForeignWorkerDetails foreignWorkerDetails() { return foreignWorkerDetails; }

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
		return value.trim();
	}

	private static <T> T require(T value, String name) {
		if (value == null) throw new IllegalArgumentException(name + " is required");
		return value;
	}
}
