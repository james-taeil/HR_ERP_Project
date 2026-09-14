package com.jamestaeil.hrerp.hr.employee.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "onboarding_checklists")
public class OnboardingChecklistEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private long employeeId;

	protected OnboardingChecklistEntity() {}
	public OnboardingChecklistEntity(long employeeId) { this.employeeId = employeeId; }
}
