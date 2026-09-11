package com.jamestaeil.hrerp.hr.employee.domain;

import java.time.LocalDate;

public record ForeignWorkerDetails(
	String nationality,
	String visaType,
	LocalDate stayFrom,
	LocalDate stayUntil,
	byte[] encryptedRegistrationNumber,
	String registrationNumberMask
) {
	public ForeignWorkerDetails {
		requireText(nationality, "Nationality");
		requireText(visaType, "Visa type");
		if (stayFrom == null || stayUntil == null || stayUntil.isBefore(stayFrom)) {
			throw new IllegalArgumentException("Valid stay period is required");
		}
		if (encryptedRegistrationNumber == null || encryptedRegistrationNumber.length == 0) {
			throw new IllegalArgumentException("Encrypted registration number is required");
		}
		requireText(registrationNumberMask, "Registration number mask");
		encryptedRegistrationNumber = encryptedRegistrationNumber.clone();
	}

	@Override
	public byte[] encryptedRegistrationNumber() {
		return encryptedRegistrationNumber.clone();
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
