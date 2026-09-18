package com.jamestaeil.hrerp.platform.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;

@Component
class SecurityCurrentActorProvider implements CurrentActorProvider {
	@Override
	public long requireActorId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
			throw new RecordAccessForbiddenException();
		}
		return principal.accountId();
	}
}
