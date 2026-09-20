package com.jamestaeil.hrerp.platform.reference;

import static com.jamestaeil.hrerp.platform.authorization.PermissionCode.PLATFORM_REFERENCE_MANAGE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationQueryService;

@Service
public class ReferenceService {
	private final JdbcClient jdbc;
	private final AuthorizationQueryService authorization;
	private final ObjectMapper json;

	ReferenceService(JdbcClient jdbc, AuthorizationQueryService authorization, ObjectMapper json) {
		this.jdbc = jdbc;
		this.authorization = authorization;
		this.json = json;
	}

	@Transactional
	public CodeGroup createGroup(long actor, String groupCode, String groupName) {
		manage(actor);
		String code = key(groupCode, 50);
		try {
			jdbc.sql("INSERT INTO platform_code_groups (group_code, group_name) VALUES (:code, :name)")
				.param("code", code).param("name", label(groupName)).update();
		} catch (DataIntegrityViolationException exception) {
			throw new ReferenceConflictException("Code group already exists");
		}
		return groupByCode(code);
	}

	@Transactional(readOnly = true)
	public List<CodeGroup> groups(long actor, long afterId, int limit, boolean includeInactive) {
		manage(actor);
		page(afterId, limit);
		return jdbc.sql("""
			SELECT id, group_code, group_name, active FROM platform_code_groups
			WHERE id > :after AND (:inactive = TRUE OR active = TRUE)
			ORDER BY id LIMIT :limit
			""").param("after", afterId).param("inactive", includeInactive).param("limit", limit)
			.query(ReferenceService::mapGroup).list();
	}

	@Transactional
	public CodeGroup changeGroup(long actor, long id, String groupName, Boolean active) {
		manage(actor);
		CodeGroup before = group(id);
		jdbc.sql("UPDATE platform_code_groups SET group_name=:name, active=:active WHERE id=:id")
			.param("id", id).param("name", groupName == null ? before.name() : label(groupName))
			.param("active", active == null ? before.active() : active).update();
		return group(id);
	}

	@Transactional
	public Code createCode(long actor, long groupId, String code, String name) {
		manage(actor);
		if (!group(groupId).active()) throw new ReferenceConflictException("Code group is inactive");
		String normalized = key(code, 50);
		try {
			jdbc.sql("INSERT INTO platform_codes (group_id, code, code_name) VALUES (:groupId, :code, :name)")
				.param("groupId", groupId).param("code", normalized).param("name", label(name)).update();
		} catch (DataIntegrityViolationException exception) {
			throw new ReferenceConflictException("Code already exists in group");
		}
		return code(groupId, normalized);
	}

	@Transactional(readOnly = true)
	public List<Code> codes(long actor, long groupId, long afterId, int limit, boolean includeInactive) {
		manage(actor);
		page(afterId, limit);
		group(groupId);
		return jdbc.sql("""
			SELECT c.id, c.group_id, c.code, c.code_name, c.active
			FROM platform_codes c JOIN platform_code_groups g ON g.id = c.group_id
			WHERE c.group_id=:groupId AND c.id > :after
			  AND (:inactive = TRUE OR (c.active = TRUE AND g.active = TRUE))
			ORDER BY c.id LIMIT :limit
			""").param("groupId", groupId).param("after", afterId)
			.param("inactive", includeInactive).param("limit", limit)
			.query(ReferenceService::mapCode).list();
	}

	@Transactional
	public Code changeCode(long actor, long id, String name, Boolean active) {
		manage(actor);
		Code before = code(id);
		if (Boolean.TRUE.equals(active) && !group(before.groupId()).active())
			throw new ReferenceConflictException("Code group is inactive");
		jdbc.sql("UPDATE platform_codes SET code_name=:name, active=:active WHERE id=:id")
			.param("id", id).param("name", name == null ? before.name() : label(name))
			.param("active", active == null ? before.active() : active).update();
		return code(id);
	}

	@Transactional
	public AnnualSetting addSetting(long actor, String type, int year, String scopeType, long scopeId,
			JsonNode value, String sourceReference) {
		manage(actor);
		String settingType = key(type, 100);
		String scope = key(scopeType, 30);
		if (year < 2000 || year > 9999 || scopeId <= 0 || value == null || value.isNull()
				|| sourceReference == null || sourceReference.isBlank() || sourceReference.length() > 500)
			throw new IllegalArgumentException("Invalid annual setting");
		String encoded;
		try { encoded = json.writeValueAsString(value); }
		catch (JacksonException exception) { throw new IllegalArgumentException("Invalid setting value", exception); }
		List<Long> versions = jdbc.sql("""
			SELECT setting_version FROM platform_annual_settings
			WHERE setting_type=:type AND applicable_year=:year AND scope_type=:scope AND scope_id=:scopeId
			ORDER BY setting_version DESC FOR UPDATE
			""").param("type", settingType).param("year", year).param("scope", scope)
			.param("scopeId", scopeId).query(Long.class).list();
		long version = versions.isEmpty() ? 1 : versions.getFirst() + 1;
		try {
			jdbc.sql("""
				INSERT INTO platform_annual_settings
				(setting_type, applicable_year, scope_type, scope_id, setting_version,
				 setting_value, source_reference, verified_by)
				VALUES (:type, :year, :scope, :scopeId, :version, :value, :source, :actor)
				""").param("type", settingType).param("year", year).param("scope", scope)
				.param("scopeId", scopeId).param("version", version).param("value", encoded)
				.param("source", sourceReference.trim()).param("actor", actor).update();
		} catch (DataIntegrityViolationException exception) {
			throw new ReferenceConflictException("Annual setting version conflicts with another change");
		}
		return setting(settingType, year, scope, scopeId, version);
	}

	@Transactional(readOnly = true)
	public AnnualSetting currentSetting(long actor, String type, int year, String scopeType, long scopeId) {
		manage(actor);
		return jdbc.sql("""
			SELECT id, setting_type, applicable_year, scope_type, scope_id, setting_version,
			       setting_value, source_reference, verified_by, created_at
			FROM platform_annual_settings
			WHERE setting_type=:type AND applicable_year=:year AND scope_type=:scope AND scope_id=:scopeId
			ORDER BY setting_version DESC LIMIT 1
			""").param("type", key(type, 100)).param("year", year)
			.param("scope", key(scopeType, 30)).param("scopeId", scopeId)
			.query(this::mapSetting).optional()
			.orElseThrow(() -> new ReferenceNotFoundException("No setting for target year"));
	}

	@Transactional(readOnly = true)
	public List<AnnualSetting> settingHistory(long actor, String type, int year, String scopeType, long scopeId,
			long afterVersion, int limit) {
		manage(actor);
		page(afterVersion, limit);
		return jdbc.sql("""
			SELECT id, setting_type, applicable_year, scope_type, scope_id, setting_version,
			       setting_value, source_reference, verified_by, created_at
			FROM platform_annual_settings
			WHERE setting_type=:type AND applicable_year=:year AND scope_type=:scope AND scope_id=:scopeId
			  AND setting_version > :afterVersion
			ORDER BY setting_version LIMIT :limit
			""").param("type", key(type, 100)).param("year", year)
			.param("scope", key(scopeType, 30)).param("scopeId", scopeId)
			.param("afterVersion", afterVersion).param("limit", limit)
			.query(this::mapSetting).list();
	}

	private AnnualSetting setting(String type, int year, String scope, long scopeId, long version) {
		return jdbc.sql("""
			SELECT id, setting_type, applicable_year, scope_type, scope_id, setting_version,
			       setting_value, source_reference, verified_by, created_at
			FROM platform_annual_settings WHERE setting_type=:type AND applicable_year=:year
			  AND scope_type=:scope AND scope_id=:scopeId AND setting_version=:version
			""").param("type", type).param("year", year).param("scope", scope)
			.param("scopeId", scopeId).param("version", version).query(this::mapSetting).single();
	}

	private AnnualSetting mapSetting(ResultSet rs, int row) throws SQLException {
		try {
			return new AnnualSetting(rs.getLong("id"), rs.getString("setting_type"), rs.getInt("applicable_year"),
				rs.getString("scope_type"), rs.getLong("scope_id"), rs.getLong("setting_version"),
				json.readTree(rs.getString("setting_value")), rs.getString("source_reference"),
				rs.getLong("verified_by"), rs.getTimestamp("created_at").toInstant());
		} catch (JacksonException exception) { throw new SQLException("Invalid stored setting JSON", exception); }
	}

	private CodeGroup groupByCode(String code) {
		return jdbc.sql("SELECT id, group_code, group_name, active FROM platform_code_groups WHERE group_code=:code")
			.param("code", code).query(ReferenceService::mapGroup).single();
	}
	private CodeGroup group(long id) {
		return jdbc.sql("SELECT id, group_code, group_name, active FROM platform_code_groups WHERE id=:id")
			.param("id", id).query(ReferenceService::mapGroup).optional()
			.orElseThrow(() -> new ReferenceNotFoundException("Code group not found"));
	}
	private Code code(long groupId, String value) {
		return jdbc.sql("SELECT id, group_id, code, code_name, active FROM platform_codes WHERE group_id=:groupId AND code=:code")
			.param("groupId", groupId).param("code", value).query(ReferenceService::mapCode).single();
	}
	private Code code(long id) {
		return jdbc.sql("SELECT id, group_id, code, code_name, active FROM platform_codes WHERE id=:id")
			.param("id", id).query(ReferenceService::mapCode).optional()
			.orElseThrow(() -> new ReferenceNotFoundException("Code not found"));
	}
	private static CodeGroup mapGroup(ResultSet rs, int row) throws SQLException {
		return new CodeGroup(rs.getLong("id"), rs.getString("group_code"), rs.getString("group_name"), rs.getBoolean("active"));
	}
	private static Code mapCode(ResultSet rs, int row) throws SQLException {
		return new Code(rs.getLong("id"), rs.getLong("group_id"), rs.getString("code"), rs.getString("code_name"), rs.getBoolean("active"));
	}
	private void manage(long actor) { authorization.requirePermission(actor, PLATFORM_REFERENCE_MANAGE); }
	private static void page(long after, int limit) {
		if (after < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid page");
	}
	private static String label(String value) {
		if (value == null || value.isBlank() || value.length() > 100) throw new IllegalArgumentException("Invalid name");
		return value.trim();
	}
	private static String key(String value, int maxLength) {
		if (value == null || value.isBlank() || value.length() > maxLength) throw new IllegalArgumentException("Invalid code");
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException("Invalid code");
		return normalized;
	}

	public record CodeGroup(long id, String code, String name, boolean active) {}
	public record Code(long id, long groupId, String code, String name, boolean active) {}
	public record AnnualSetting(long id, String type, int year, String scopeType, long scopeId,
		long version, JsonNode value, String sourceReference, long verifiedBy, Instant createdAt) {}
}
