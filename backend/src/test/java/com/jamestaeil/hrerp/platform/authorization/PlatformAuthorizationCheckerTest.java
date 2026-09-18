package com.jamestaeil.hrerp.platform.authorization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.EmployeeRegistrationForbiddenException;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;

@ExtendWith(MockitoExtension.class)
class PlatformAuthorizationCheckerTest {
	@Mock CurrentActorProvider actors;
	@Mock AuthorizationQueryService authorization;

	@Test
	void mapsEmployeeRegistrationToStablePermission() {
		when(actors.requireActorId()).thenReturn(41L);
		PlatformAuthorizationChecker checker = new PlatformAuthorizationChecker(actors, authorization);

		assertDoesNotThrow(checker::checkCanRegisterEmployee);

		verify(authorization).requirePermission(41L, PermissionCode.HR_EMPLOYEE_REGISTER);
	}

	@Test
	void failsClosedWhenEmployeeRegistrationLookupFails() {
		when(actors.requireActorId()).thenThrow(new IllegalStateException("platform unavailable"));
		PlatformAuthorizationChecker checker = new PlatformAuthorizationChecker(actors, authorization);

		assertThrows(EmployeeRegistrationForbiddenException.class, checker::checkCanRegisterEmployee);
	}

	@Test
	void mapsRecordAndLifecycleChecksAndDeniesOutsideScope() {
		PlatformAuthorizationChecker checker = new PlatformAuthorizationChecker(actors, authorization);
		when(authorization.canAccessEmployee(11L, PermissionCode.HR_RECORD_READ, 22L)).thenReturn(true);
		when(authorization.canAccessEmployee(11L, PermissionCode.HR_LIFECYCLE_WRITE, 22L)).thenReturn(false);

		assertDoesNotThrow(() -> checker.checkCanReadRecord(11L, 22L));
		assertThrows(RecordAccessForbiddenException.class,
			() -> checker.checkCanWriteLifecycle(11L, 22L));
	}
}
