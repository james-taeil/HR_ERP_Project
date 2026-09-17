package com.jamestaeil.hrerp.platform.api;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.account.SessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class SessionAuthenticationFilter extends OncePerRequestFilter {
	private final SessionService sessions;
	private final SessionCookieSettings cookie;

	SessionAuthenticationFilter(SessionService sessions, SessionCookieSettings cookie) {
		this.sessions = sessions;
		this.cookie = cookie;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			String rawToken = readCookie(request);
			sessions.authenticate(rawToken).ifPresent(principal -> authenticate(principal));
		}
		chain.doFilter(request, response);
	}

	private String readCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) return null;
		return Arrays.stream(cookies)
			.filter(item -> cookie.name().equals(item.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private static void authenticate(SessionPrincipal principal) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
		SecurityContextHolder.setContext(context);
	}
}
