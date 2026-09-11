package com.jamestaeil.hrerp.platform.port;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
class PendingPlatformPortsConfiguration {

	@Bean
	@ConditionalOnMissingBean(AuthorizationChecker.class)
	AuthorizationChecker authorizationChecker() {
		return () -> { throw new PlatformIntegrationUnavailableException(); };
	}

	@Bean
	@ConditionalOnMissingBean(OrganizationReader.class)
	OrganizationReader organizationReader() {
		return (workplaceId, departmentId) -> { throw new PlatformIntegrationUnavailableException(); };
	}
}
