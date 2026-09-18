package com.jamestaeil.hrerp.platform.authorization;

public interface AuthorizationChangeLogWriter {
	void write(long actorAccountId, long targetAccountId, String changeType, Object beforeValue, Object afterValue);
}
