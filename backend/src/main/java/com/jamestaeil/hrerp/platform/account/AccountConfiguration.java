package com.jamestaeil.hrerp.platform.account;

import java.time.Clock;

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
	Clock platformClock() {
		return Clock.systemUTC();
	}
}
