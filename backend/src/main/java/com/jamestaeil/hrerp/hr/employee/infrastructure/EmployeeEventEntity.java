package com.jamestaeil.hrerp.hr.employee.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_events")
public class EmployeeEventEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private long employeeId;
	private String eventType;

	protected EmployeeEventEntity() {}
	public EmployeeEventEntity(long employeeId) {
		this.employeeId = employeeId;
		this.eventType = "EMPLOYEE_REGISTERED";
	}
}
