package com.jamestaeil.hrerp.hr.employee.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;

class EmployeeNumberGeneratorTest {

	@Test
	void formatsYearSequenceAndInitialDepartment() {
		assertEquals("26000107", EmployeeNumberGenerator.format(26, 1, new DepartmentCode("07")).value());
		assertEquals("00999999", EmployeeNumberGenerator.format(0, 9999, new DepartmentCode("99")).value());
	}

	@Test
	void rejectsSequenceAfter9999() {
		assertEquals(1, EmployeeNumberGenerator.nextSequence(0, 26));
		assertThrows(EmployeeNumberExhaustedException.class,
				() -> EmployeeNumberGenerator.nextSequence(9999, 26));
	}
}
