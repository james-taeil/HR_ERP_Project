package com.jamestaeil.hrerp.platform.authorization;

import org.springframework.stereotype.Component;

import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.EmployeeRegistrationForbiddenException;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;

@Component
class PlatformAuthorizationChecker implements AuthorizationChecker {
	private final CurrentActorProvider actors;
	private final AuthorizationQueryService authorization;

	PlatformAuthorizationChecker(CurrentActorProvider actors, AuthorizationQueryService authorization) {
		this.actors = actors;
		this.authorization = authorization;
	}

	@Override
	public void checkCanRegisterEmployee() {
		try {
			authorization.requirePermission(actors.requireActorId(), PermissionCode.HR_EMPLOYEE_REGISTER);
		} catch (RuntimeException exception) {
			throw new EmployeeRegistrationForbiddenException();
		}
	}

	@Override
	public void checkCanReadRecord(long actorId, long employeeId) {
		requireEmployeeAccess(actorId, PermissionCode.HR_RECORD_READ, employeeId);
	}

	@Override
	public void checkCanWriteRecord(long actorId, long employeeId) {
		requireEmployeeAccess(actorId, PermissionCode.HR_RECORD_WRITE, employeeId);
	}

	@Override
	public void checkCanReadLifecycle(long actorId, long employeeId) {
		requireEmployeeAccess(actorId, PermissionCode.HR_LIFECYCLE_READ, employeeId);
	}

	@Override
	public void checkCanWriteLifecycle(long actorId, long employeeId) {
		requireEmployeeAccess(actorId, PermissionCode.HR_LIFECYCLE_WRITE, employeeId);
	}

	private void requireEmployeeAccess(long actorId, PermissionCode permission, long employeeId) {
		if (!authorization.canAccessEmployee(actorId, permission, employeeId)) {
			throw new RecordAccessForbiddenException();
		}
	}
}
