package com.jamestaeil.hrerp.platform.account;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AccountConfiguration {
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	PasswordPolicy passwordPolicy(
		@Value("${app.platform.password.minimum-length}") int minimumLength,
		@Value("${app.platform.password.history-limit}") int historyLimit
	) {
		return new PasswordPolicy(minimumLength, historyLimit);
	}

	@Bean
	AuthenticationPolicy authenticationPolicy(
		@Value("${app.platform.login.max-failed-attempts}") int maxFailedAttempts,
		@Value("${app.platform.login.lock-duration}") Duration lockDuration
	) {
		return new AuthenticationPolicy(maxFailedAttempts, lockDuration);
	}

	@Bean
	SessionPolicy sessionPolicy(
		@Value("${app.platform.session.absolute-timeout}") Duration absoluteTimeout,
		@Value("${app.platform.session.idle-timeout}") Duration idleTimeout
	) {
		return new SessionPolicy(absoluteTimeout, idleTimeout);
	}

	@Bean
	Clock platformClock() {
		return Clock.systemUTC();
	}
}
