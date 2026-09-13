package com.jamestaeil.hrerp.hr.employee.application;

import java.time.LocalDate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;
import com.jamestaeil.hrerp.hr.employee.domain.EmployeeNumber;

@Service
public class EmployeeNumberGenerator {

	private final JdbcClient jdbcClient;

	public EmployeeNumberGenerator(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Transactional
	public EmployeeNumber generate(LocalDate hireDate, DepartmentCode departmentCode) {
		if (hireDate == null) {
			throw new IllegalArgumentException("Hire date is required");
		}

		int year = hireDate.getYear() % 100;
		jdbcClient.sql("INSERT IGNORE INTO employee_number_sequences (sequence_year, `last_value`) VALUES (:year, 0)")
			.param("year", year)
			.update();

		int lastValue = jdbcClient.sql("SELECT `last_value` FROM employee_number_sequences WHERE sequence_year = :year FOR UPDATE")
			.param("year", year)
			.query(Integer.class)
			.single();
		int nextValue = nextSequence(lastValue, year);
		jdbcClient.sql("UPDATE employee_number_sequences SET `last_value` = :nextValue WHERE sequence_year = :year")
			.param("nextValue", nextValue)
			.param("year", year)
			.update();

		return format(year, nextValue, departmentCode);
	}

	static int nextSequence(int lastValue, int year) {
		if (lastValue >= 9999) {
			throw new EmployeeNumberExhaustedException(year);
		}
		return lastValue + 1;
	}

	static EmployeeNumber format(int year, int sequence, DepartmentCode departmentCode) {
		return new EmployeeNumber("%02d%04d%s".formatted(year, sequence, departmentCode.value()));
	}
}
