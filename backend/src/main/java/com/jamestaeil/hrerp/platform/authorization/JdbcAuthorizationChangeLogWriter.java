package com.jamestaeil.hrerp.platform.authorization;

import java.sql.Types;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class JdbcAuthorizationChangeLogWriter implements AuthorizationChangeLogWriter {
	private final JdbcClient jdbc;
	private final ObjectMapper json;

	JdbcAuthorizationChangeLogWriter(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public void write(long actorAccountId, long targetAccountId, String changeType,
			Object beforeValue, Object afterValue) {
		jdbc.sql("""
			INSERT INTO platform_authorization_change_logs
				(actor_account_id, target_account_id, change_type, before_value, after_value)
			VALUES (:actor, :target, :type, :beforeValue, :afterValue)
			""")
			.param("actor", actorAccountId)
			.param("target", targetAccountId)
			.param("type", changeType)
			.param("beforeValue", serialize(beforeValue), Types.VARCHAR)
			.param("afterValue", serialize(afterValue), Types.VARCHAR)
			.update();
	}

	private String serialize(Object value) {
		if (value == null) return null;
		try {
			return json.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("Authorization change cannot be serialized", exception);
		}
	}
}
