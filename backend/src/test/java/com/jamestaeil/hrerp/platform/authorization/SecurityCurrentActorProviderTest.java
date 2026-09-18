package com.jamestaeil.hrerp.platform.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;

class SecurityCurrentActorProviderTest {
	private final SecurityCurrentActorProvider provider = new SecurityCurrentActorProvider();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsAuthenticatedAccountId() {
		SessionPrincipal principal = new SessionPrincipal(7L, 11L, 13L, "actor");
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));

		assertEquals(11L, provider.requireActorId());
	}

	@Test
	void rejectsMissingOrUnexpectedPrincipal() {
		assertThrows(RecordAccessForbiddenException.class, provider::requireActorId);

		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated("actor", null, java.util.List.of()));
		assertThrows(RecordAccessForbiddenException.class, provider::requireActorId);
	}
}
