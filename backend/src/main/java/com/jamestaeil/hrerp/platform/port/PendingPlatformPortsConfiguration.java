package com.jamestaeil.hrerp.platform.port;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
class PendingPlatformPortsConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurrentActorProvider.class)
    CurrentActorProvider currentActorProvider() {
        return () -> { throw new PlatformIntegrationUnavailableException(); };
    }

    @Bean
    @ConditionalOnMissingBean(FileStorage.class)
    FileStorage fileStorage() {
        return (actor, employee, file) -> { throw new PlatformIntegrationUnavailableException(); };
    }

    @Bean
    @ConditionalOnMissingBean(RecordAudit.class)
    RecordAudit recordAudit() {
        return new RecordAudit() {
            public void changed(long actor, long employee, String section, long record, Object before, Object after) {
                throw new PlatformIntegrationUnavailableException();
            }
            public void viewed(long actor, long employee, String section) {
                throw new PlatformIntegrationUnavailableException();
            }
        };
    }

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

    @Bean
    @ConditionalOnMissingBean(NotificationSender.class)
    NotificationSender notificationSender() {
        return notification -> { throw new PlatformIntegrationUnavailableException(); };
    }

    @Bean
    @ConditionalOnMissingBean(AccountAccessScheduler.class)
    AccountAccessScheduler accountAccessScheduler() {
        return (employeeId, date) -> { throw new PlatformIntegrationUnavailableException(); };
    }

    @Bean
    @ConditionalOnMissingBean(LifecycleTaskPublisher.class)
    LifecycleTaskPublisher lifecycleTaskPublisher() {
        return task -> { throw new PlatformIntegrationUnavailableException(); };
    }

    @Bean
    @ConditionalOnMissingBean(OrganizationSnapshotReader.class)
    OrganizationSnapshotReader organizationSnapshotReader() {
        return (actorId, date) -> { throw new PlatformIntegrationUnavailableException(); };
    }
}
