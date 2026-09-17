package com.jamestaeil.hrerp.platform.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import com.jamestaeil.hrerp.platform.account.SessionService;

@Configuration
class PlatformSecurityConfiguration {
	@Bean
	SessionCookieSettings sessionCookieSettings(@Value("${app.platform.session.cookie-name}") String name) {
		return new SessionCookieSettings(name);
	}

	@Bean
	SessionAuthenticationFilter sessionAuthenticationFilter(SessionService sessions, SessionCookieSettings cookie) {
		return new SessionAuthenticationFilter(sessions, cookie);
	}

	@Bean
	SecurityFilterChain platformSecurity(HttpSecurity http, SessionAuthenticationFilter sessionFilter) throws Exception {
		CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
		http
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())
			.logout(logout -> logout.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokens)
				.requireCsrfProtectionMatcher(new OrRequestMatcher(
					PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/platform/auth/logout"),
					PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/platform/auth/logout-all"))))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.POST, "/api/platform/auth/login").permitAll()
				.requestMatchers("/api/platform/auth/**").authenticated()
				.anyRequest().permitAll())
			.exceptionHandling(errors -> errors.authenticationEntryPoint(
				(request, response, exception) -> response.sendError(401)))
			.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
			.cors(Customizer.withDefaults());
		return http.build();
	}
}
