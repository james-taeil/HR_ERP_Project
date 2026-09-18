package com.jamestaeil.hrerp.platform.authorization;

import static com.jamestaeil.hrerp.platform.authorization.PermissionCode.PLATFORM_AUTHORIZATION_MANAGE;

import java.sql.Types;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationAdminService {
	private final JdbcClient jdbc;
	private final AuthorizationQueryService authorization;
	private final AuthorizationChangeLogWriter changes;

	AuthorizationAdminService(JdbcClient jdbc, AuthorizationQueryService authorization,
			AuthorizationChangeLogWriter changes) {
		this.jdbc = jdbc;
		this.authorization = authorization;
		this.changes = changes;
	}

	@Transactional
	public RoleView createRole(long actorAccountId, String roleCode, String roleName,
			List<PermissionCode> permissionCodes) {
		authorization.requirePermission(actorAccountId, PLATFORM_AUTHORIZATION_MANAGE);
		String normalizedCode = normalizeRoleCode(roleCode);
		String normalizedName = requireName(roleName);
		List<PermissionCode> permissions = distinctPermissions(permissionCodes);
		try {
			jdbc.sql("""
				INSERT INTO platform_roles (role_code, role_name, active)
				VALUES (:code, :name, TRUE)
				""")
				.param("code", normalizedCode)
				.param("name", normalizedName)
				.update();
		} catch (DataIntegrityViolationException exception) {
			throw new AuthorizationConflictException("Role code is already in use");
		}
		long roleId = jdbc.sql("SELECT id FROM platform_roles WHERE role_code = :code")
			.param("code", normalizedCode).query(Long.class).single();
		replaceRolePermissions(roleId, permissions);
		RoleView created = role(roleId);
		changes.write(actorAccountId, actorAccountId, "ROLE_CREATED", null, created);
		return created;
	}

	@Transactional
	public RoleView updateRole(long actorAccountId, long roleId, String roleName, Boolean active,
			List<PermissionCode> permissionCodes) {
		authorization.requirePermission(actorAccountId, PLATFORM_AUTHORIZATION_MANAGE);
		RoleView before = role(roleId);
		String nextName = roleName == null ? before.roleName() : requireName(roleName);
		boolean nextActive = active == null ? before.active() : active;
		jdbc.sql("UPDATE platform_roles SET role_name = :name, active = :active WHERE id = :id")
			.param("name", nextName).param("active", nextActive).param("id", roleId).update();
		if (permissionCodes != null) replaceRolePermissions(roleId, distinctPermissions(permissionCodes));
		RoleView after = role(roleId);
		changes.write(actorAccountId, actorAccountId, "ROLE_UPDATED:" + roleId, before, after);
		return after;
	}

	@Transactional(readOnly = true)
	public List<RoleView> roles(long actorAccountId, long afterId, int limit) {
		authorization.requirePermission(actorAccountId, PLATFORM_AUTHORIZATION_MANAGE);
		if (afterId < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid cursor page");
		return jdbc.sql("""
			SELECT id, role_code, role_name, active
			FROM platform_roles
			WHERE id > :afterId
			ORDER BY id
			LIMIT :limit
			""")
			.param("afterId", afterId).param("limit", limit)
			.query((rs, row) -> new RoleView(rs.getLong("id"), rs.getString("role_code"),
				rs.getString("role_name"), rs.getBoolean("active"), rolePermissions(rs.getLong("id"))))
			.list();
	}

	@Transactional(readOnly = true)
	public List<PermissionView> permissions(long actorAccountId) {
		authorization.requirePermission(actorAccountId, PLATFORM_AUTHORIZATION_MANAGE);
		return jdbc.sql("""
			SELECT permission_code, sensitive_operation
			FROM platform_permissions
			ORDER BY permission_code
			""")
			.query((rs, row) -> new PermissionView(PermissionCode.valueOf(rs.getString("permission_code")),
				rs.getBoolean("sensitive_operation")))
			.list();
	}

	@Transactional
	public void replaceAccountRoles(long actorAccountId, long targetAccountId,
			List<RoleAssignment> assignments) {
		authorization.requirePermission(actorAccountId, PLATFORM_AUTHORIZATION_MANAGE);
		requireAccount(targetAccountId);
		List<RoleAssignment> normalized = validateAssignments(assignments);
		List<RoleAssignment> before = accountRoles(targetAccountId);
		jdbc.sql("DELETE FROM platform_account_roles WHERE account_id = :accountId")
			.param("accountId", targetAccountId).update();
		for (RoleAssignment assignment : normalized) {
			jdbc.sql("""
				INSERT INTO platform_account_roles (account_id, role_id, valid_from, valid_to)
				VALUES (:accountId, :roleId, :validFrom, :validTo)
				""")
				.param("accountId", targetAccountId)
				.param("roleId", assignment.roleId())
				.param("validFrom", assignment.validFrom())
				.param("validTo", assignment.validTo(), Types.TIMESTAMP_WITH_TIMEZONE)
				.update();
		}
		changes.write(actorAccountId, targetAccountId, "ACCOUNT_ROLES_REPLACED", before, normalized);
	}

	@Transactional
	public void replaceOrganizationScopes(long actorAccountId, long targetAccountId,
			List<ScopeAssignment> assignments) {
		authorization.requirePermission(actorAccountId, PLATFORM_AUTHORIZATION_MANAGE);
		requireAccount(targetAccountId);
		List<ScopeAssignment> normalized = validateScopes(assignments);
		List<ScopeAssignment> before = accountScopes(targetAccountId);
		jdbc.sql("DELETE FROM platform_organization_scopes WHERE account_id = :accountId")
			.param("accountId", targetAccountId).update();
		for (ScopeAssignment assignment : normalized) {
			jdbc.sql("""
				INSERT INTO platform_organization_scopes (account_id, scope_type, organization_id)
				VALUES (:accountId, :scopeType, :organizationId)
				""")
				.param("accountId", targetAccountId)
				.param("scopeType", assignment.scopeType().name())
				.param("organizationId", assignment.organizationId(), Types.BIGINT)
				.update();
		}
		changes.write(actorAccountId, targetAccountId, "ORGANIZATION_SCOPES_REPLACED", before, normalized);
	}

	private RoleView role(long roleId) {
		return jdbc.sql("SELECT id, role_code, role_name, active FROM platform_roles WHERE id = :id")
			.param("id", roleId)
			.query((rs, row) -> new RoleView(rs.getLong("id"), rs.getString("role_code"),
				rs.getString("role_name"), rs.getBoolean("active"), rolePermissions(roleId)))
			.optional()
			.orElseThrow(() -> new AuthorizationResourceNotFoundException("Role was not found"));
	}

	private List<PermissionCode> rolePermissions(long roleId) {
		return jdbc.sql("""
			SELECT p.permission_code
			FROM platform_role_permissions rp
			JOIN platform_permissions p ON p.id = rp.permission_id
			WHERE rp.role_id = :roleId
			ORDER BY p.permission_code
			""")
			.param("roleId", roleId)
			.query((rs, row) -> PermissionCode.valueOf(rs.getString("permission_code")))
			.list();
	}

	private void replaceRolePermissions(long roleId, List<PermissionCode> permissions) {
		jdbc.sql("DELETE FROM platform_role_permissions WHERE role_id = :roleId")
			.param("roleId", roleId).update();
		for (PermissionCode permission : permissions) {
			int updated = jdbc.sql("""
				INSERT INTO platform_role_permissions (role_id, permission_id)
				SELECT :roleId, id FROM platform_permissions WHERE permission_code = :permission
				""")
				.param("roleId", roleId).param("permission", permission.name()).update();
			if (updated != 1) throw new AuthorizationResourceNotFoundException("Permission was not found");
		}
	}

	private List<RoleAssignment> validateAssignments(List<RoleAssignment> assignments) {
		if (assignments == null) throw new IllegalArgumentException("Role assignments are required");
		Set<Long> roleIds = new HashSet<>();
		for (RoleAssignment assignment : assignments) {
			if (assignment == null || assignment.roleId() <= 0 || assignment.validFrom() == null
					|| assignment.validTo() != null && !assignment.validTo().isAfter(assignment.validFrom())
					|| !roleIds.add(assignment.roleId())) {
				throw new IllegalArgumentException("Invalid role assignment");
			}
			Integer count = jdbc.sql("SELECT COUNT(*) FROM platform_roles WHERE id = :id AND active = TRUE")
				.param("id", assignment.roleId()).query(Integer.class).single();
			if (count == null || count != 1) throw new AuthorizationResourceNotFoundException("Active role was not found");
		}
		return List.copyOf(assignments);
	}

	private List<ScopeAssignment> validateScopes(List<ScopeAssignment> assignments) {
		if (assignments == null) throw new IllegalArgumentException("Scope assignments are required");
		Set<ScopeAssignment> unique = new HashSet<>();
		for (ScopeAssignment assignment : assignments) {
			if (assignment == null || assignment.scopeType() == null || !unique.add(assignment)) {
				throw new IllegalArgumentException("Invalid organization scope");
			}
			if (assignment.scopeType() == OrganizationScopeType.SELF) {
				if (assignment.organizationId() != null) throw new IllegalArgumentException("SELF has no organization ID");
			} else {
				if (assignment.organizationId() == null || assignment.organizationId() <= 0
						|| !organizationExists(assignment)) {
					throw new AuthorizationResourceNotFoundException("Organization was not found");
				}
			}
		}
		return List.copyOf(assignments);
	}

	private boolean organizationExists(ScopeAssignment assignment) {
		String table = switch (assignment.scopeType()) {
			case COMPANY -> "platform_companies";
			case WORKPLACE -> "platform_workplaces";
			case DEPARTMENT, DEPARTMENT_TREE -> "platform_departments";
			case SELF -> throw new IllegalArgumentException("SELF has no organization target");
		};
		Integer count = jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE id = :id")
			.param("id", assignment.organizationId()).query(Integer.class).single();
		return count != null && count == 1;
	}

	private void requireAccount(long accountId) {
		Integer count = jdbc.sql("SELECT COUNT(*) FROM platform_accounts WHERE id = :id")
			.param("id", accountId).query(Integer.class).single();
		if (count == null || count != 1) throw new AuthorizationResourceNotFoundException("Account was not found");
	}

	private List<RoleAssignment> accountRoles(long accountId) {
		return jdbc.sql("""
			SELECT role_id, valid_from, valid_to
			FROM platform_account_roles WHERE account_id = :accountId ORDER BY role_id
			""").param("accountId", accountId)
			.query((rs, row) -> new RoleAssignment(rs.getLong("role_id"),
				rs.getTimestamp("valid_from").toInstant(),
				rs.getTimestamp("valid_to") == null ? null : rs.getTimestamp("valid_to").toInstant()))
			.list();
	}

	private List<ScopeAssignment> accountScopes(long accountId) {
		return jdbc.sql("""
			SELECT scope_type, organization_id
			FROM platform_organization_scopes WHERE account_id = :accountId ORDER BY id
			""").param("accountId", accountId)
			.query((rs, row) -> new ScopeAssignment(OrganizationScopeType.valueOf(rs.getString("scope_type")),
				(Long) rs.getObject("organization_id")))
			.list();
	}

	private static String normalizeRoleCode(String value) {
		if (value == null) throw new IllegalArgumentException("Role code is required");
		String normalized = value.strip().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z][A-Z0-9_]{2,99}")) throw new IllegalArgumentException("Invalid role code");
		return normalized;
	}

	private static String requireName(String value) {
		if (value == null || value.isBlank() || value.strip().length() > 100) {
			throw new IllegalArgumentException("Invalid role name");
		}
		return value.strip();
	}

	private static List<PermissionCode> distinctPermissions(List<PermissionCode> permissions) {
		if (permissions == null) throw new IllegalArgumentException("Permissions are required");
		if (new HashSet<>(permissions).size() != permissions.size() || permissions.contains(null)) {
			throw new IllegalArgumentException("Duplicate or empty permission");
		}
		return List.copyOf(permissions);
	}

	public record RoleView(long id, String roleCode, String roleName, boolean active,
		List<PermissionCode> permissions) {}
	public record PermissionView(PermissionCode permissionCode, boolean sensitiveOperation) {}
	public record RoleAssignment(long roleId, Instant validFrom, Instant validTo) {}
	public record ScopeAssignment(OrganizationScopeType scopeType, Long organizationId) {}
}
