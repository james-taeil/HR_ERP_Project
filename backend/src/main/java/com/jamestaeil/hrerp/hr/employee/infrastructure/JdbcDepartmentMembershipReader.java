package com.jamestaeil.hrerp.hr.employee.infrastructure;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jamestaeil.hrerp.platform.port.DepartmentMembershipReader;

@Component
class JdbcDepartmentMembershipReader implements DepartmentMembershipReader {
	private final JdbcClient jdbc;

	JdbcDepartmentMembershipReader(JdbcClient jdbc) { this.jdbc = jdbc; }

	@Override
	public List<Long> employeeIdsIn(long departmentId) {
		return jdbc.sql("SELECT id FROM employees WHERE department_id=:id ORDER BY id")
			.param("id", departmentId).query(Long.class).list();
	}
}
