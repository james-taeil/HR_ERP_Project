package com.jamestaeil.hrerp.platform.port;

import java.util.List;

public interface DepartmentMembershipReader {
	List<Long> employeeIdsIn(long departmentId);
}
