package com.jamestaeil.hrerp.platform.account;

public record SessionPrincipal(long sessionId, long accountId, long employeeId, String username) {}
