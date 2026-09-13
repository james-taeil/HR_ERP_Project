package com.jamestaeil.hrerp.platform.port;

public interface AuthorizationChecker {
	void checkCanRegisterEmployee();

    default void checkCanReadRecord(long actorId, long employeeId) {
        throw new PlatformIntegrationUnavailableException();
    }

    default void checkCanWriteRecord(long actorId, long employeeId) {
        throw new PlatformIntegrationUnavailableException();
    }
}
