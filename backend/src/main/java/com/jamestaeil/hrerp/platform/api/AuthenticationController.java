package com.jamestaeil.hrerp.platform.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jamestaeil.hrerp.platform.account.AuthenticationService;
import com.jamestaeil.hrerp.platform.account.AuthenticationService.LoginSuccess;
import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.account.SessionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/platform/auth")
class AuthenticationController {
	private final AuthenticationService authentication;
	private final SessionService sessions;
	private final SessionCookieSettings cookie;

	AuthenticationController(AuthenticationService authentication, SessionService sessions,
			SessionCookieSettings cookie) {
		this.authentication = authentication;
		this.sessions = sessions;
		this.cookie = cookie;
	}

	@PostMapping("/login")
	ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		Optional<LoginSuccess> result = authentication.login(request.username(), request.password(),
			httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
		if (result.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new AuthError("AUTHENTICATION_FAILED", "Authentication failed"));
		}
		LoginSuccess success = result.get();
		ResponseCookie sessionCookie = ResponseCookie.from(cookie.name(), success.rawToken())
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path("/")
			.build();
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
			.body(new LoginResponse(success.accountId(), success.employeeId(), success.username(), success.expiresAt()));
	}

	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal SessionPrincipal principal, CsrfToken csrfToken) {
		csrfToken.getToken();
		return new MeResponse(principal.accountId(), principal.employeeId(), principal.username());
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@AuthenticationPrincipal SessionPrincipal principal) {
		sessions.revoke(principal.sessionId());
		return clearCookie();
	}

	@PostMapping("/logout-all")
	ResponseEntity<Void> logoutAll(@AuthenticationPrincipal SessionPrincipal principal) {
		sessions.revokeAll(principal.accountId());
		return clearCookie();
	}

	private ResponseEntity<Void> clearCookie() {
		ResponseCookie cleared = ResponseCookie.from(cookie.name(), "")
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ZERO)
			.build();
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
	}

	record LoginRequest(@NotBlank @Size(max = 100) String username,
		@NotBlank @Size(max = 200) String password) {}
	record LoginResponse(long accountId, long employeeId, String username, Instant expiresAt) {}
	record MeResponse(long accountId, long employeeId, String username) {}
	record AuthError(String code, String message) {}
}
