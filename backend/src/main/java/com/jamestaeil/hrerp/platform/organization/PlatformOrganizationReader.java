package com.jamestaeil.hrerp.platform.organization;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;
import com.jamestaeil.hrerp.platform.port.OrganizationReader;
import com.jamestaeil.hrerp.platform.port.OrganizationValidationException;

@Component
class PlatformOrganizationReader implements OrganizationReader {
	private final JdbcClient jdbc;
	private final Clock clock;

	PlatformOrganizationReader(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public DepartmentCode requireActiveDepartment(long workplaceId, long departmentId) {
		LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
		return jdbc.sql("""
			SELECT d.stable_code
			FROM platform_departments d
			JOIN platform_department_versions v ON v.department_id=d.id
			JOIN platform_workplaces w ON w.id=v.workplace_id
			JOIN platform_companies c ON c.id=w.company_id
			WHERE d.id=:department AND v.workplace_id=:workplace
			AND v.effective_from<=:today AND (v.effective_to IS NULL OR v.effective_to>=:today)
			AND w.opened_on<=:today AND (w.active_to IS NULL OR w.active_to>=:today)
			AND c.active_from<=:today AND (c.active_to IS NULL OR c.active_to>=:today)
			""").param("department", departmentId).param("workplace", workplaceId).param("today", today)
			.query(String.class).optional().map(DepartmentCode::new)
			.orElseThrow(OrganizationValidationException::new);
	}
}
