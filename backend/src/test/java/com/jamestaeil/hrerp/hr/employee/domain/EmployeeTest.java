package com.jamestaeil.hrerp.hr.employee.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class EmployeeTest {
	private static final LocalDate HIRE_DATE = LocalDate.of(2026, 9, 1);

	@Test
	void acceptsSixEmploymentTypes() {
		for (EmploymentType type : EmploymentType.values()) {
			assertDoesNotThrow(() -> employee(type, null, null));
		}
	}

	@Test
	void rejectsInvalidProbationAndForeignPeriods() {
		assertThrows(IllegalArgumentException.class,
			() -> employee(EmploymentType.REGULAR, HIRE_DATE.minusDays(1), null));
		assertThrows(IllegalArgumentException.class,
			() -> new ForeignWorkerDetails("KR", "F-2", HIRE_DATE, HIRE_DATE.minusDays(1), new byte[] {1}, "***"));
	}

	private static Employee employee(EmploymentType type, LocalDate probationEndDate, ForeignWorkerDetails foreign) {
		return new Employee(new EmployeeNumber("26000101"), "홍길동", LocalDate.of(1990, 1, 1),
			"010-0000-0000", HIRE_DATE, type, 1, 1, "사원", probationEndDate, foreign);
	}
}
