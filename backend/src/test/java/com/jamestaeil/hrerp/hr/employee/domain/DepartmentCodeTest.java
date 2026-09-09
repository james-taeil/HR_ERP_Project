package com.jamestaeil.hrerp.hr.employee.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DepartmentCodeTest {

	@Test
	void acceptsCodesFrom01To99() {
		assertEquals("01", new DepartmentCode("01").value());
		assertEquals("99", new DepartmentCode("99").value());
	}

	@Test
	void rejectsInvalidCodes() {
		assertThrows(IllegalArgumentException.class, () -> new DepartmentCode("00"));
		assertThrows(IllegalArgumentException.class, () -> new DepartmentCode("1"));
		assertThrows(IllegalArgumentException.class, () -> new DepartmentCode("AA"));
	}
}
