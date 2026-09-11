package com.jamestaeil.hrerp.platform.port;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;

public interface OrganizationReader {
	DepartmentCode requireActiveDepartment(long workplaceId, long departmentId);
}
