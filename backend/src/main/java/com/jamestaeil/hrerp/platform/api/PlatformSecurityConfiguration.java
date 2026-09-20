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
				.requireCsrfProtectionMatcher(request -> {
					String method = request.getMethod();
					boolean unsafe = !("GET".equals(method) || "HEAD".equals(method)
						|| "OPTIONS".equals(method) || "TRACE".equals(method));
					return unsafe && request.getRequestURI().startsWith("/api/")
						&& !request.getRequestURI().equals("/api/platform/auth/login");
				}))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.POST, "/api/platform/auth/login").permitAll()
				.requestMatchers("/api/platform/auth/**").authenticated()
				.requestMatchers("/api/platform/roles/**", "/api/platform/permissions",
					"/api/platform/accounts/**", "/api/platform/companies/**",
					"/api/platform/workplaces/**", "/api/platform/departments/**", "/api/hr/**").authenticated()
				.anyRequest().permitAll())
			.exceptionHandling(errors -> errors.authenticationEntryPoint(
				(request, response, exception) -> response.sendError(401)))
			.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
			.cors(Customizer.withDefaults());
		return http.build();
	}
}
