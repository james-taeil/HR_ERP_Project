package com.jamestaeil.hrerp.hr.employee.infrastructure;

import java.time.LocalDate;

import com.jamestaeil.hrerp.hr.employee.domain.ForeignWorkerDetails;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "foreign_worker_profiles")
public class ForeignWorkerProfileEntity {
	@Id
	private Long employeeId;
	private String nationality;
	private String visaType;
	private LocalDate stayFrom;
	private LocalDate stayUntil;
	private byte[] encryptedRegistrationNumber;
	private String registrationNumberMask;

	protected ForeignWorkerProfileEntity() {}

	public ForeignWorkerProfileEntity(long employeeId, ForeignWorkerDetails details) {
		this.employeeId = employeeId;
		this.nationality = details.nationality();
		this.visaType = details.visaType();
		this.stayFrom = details.stayFrom();
		this.stayUntil = details.stayUntil();
		this.encryptedRegistrationNumber = details.encryptedRegistrationNumber();
		this.registrationNumberMask = details.registrationNumberMask();
	}
}
