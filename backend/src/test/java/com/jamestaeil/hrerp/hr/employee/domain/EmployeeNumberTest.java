package com.jamestaeil.hrerp.hr.employee.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmployeeNumberTest {

	@Test
	void acceptsEightDigitsOnly() {
		assertEquals("26000101", new EmployeeNumber("26000101").value());
		assertThrows(IllegalArgumentException.class, () -> new EmployeeNumber("2600101"));
		assertThrows(IllegalArgumentException.class, () -> new EmployeeNumber("2600010A"));
	}
}
