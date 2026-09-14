package com.jamestaeil.hrerp.hr.employee.infrastructure;

import java.time.LocalDate;

import com.jamestaeil.hrerp.hr.employee.domain.Employee;
import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "employees")
public class EmployeeEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String employeeNumber;
	private String employeeName;
	private LocalDate birthDate;
	private String phone;
	private LocalDate hireDate;
	@Enumerated(EnumType.STRING)
	private EmploymentType employmentType;
	private long workplaceId;
	private long departmentId;
	private String positionName;
	private LocalDate probationEndDate;
	private boolean foreignWorker;
	@Version
	private long version;

	protected EmployeeEntity() {}

	public EmployeeEntity(Employee employee) {
		this.employeeNumber = employee.employeeNumber().value();
		this.employeeName = employee.name();
		this.birthDate = employee.birthDate();
		this.phone = employee.phone();
		this.hireDate = employee.hireDate();
		this.employmentType = employee.employmentType();
		this.workplaceId = employee.workplaceId();
		this.departmentId = employee.departmentId();
		this.positionName = employee.position();
		this.probationEndDate = employee.probationEndDate();
		this.foreignWorker = employee.foreignWorker();
	}

	public Long id() { return id; }
}
