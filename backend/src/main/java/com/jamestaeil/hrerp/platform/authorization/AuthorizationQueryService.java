package com.jamestaeil.hrerp.platform.authorization;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationQueryService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final JdbcClient jdbc;
	private final Clock clock;

	AuthorizationQueryService(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean hasPermission(long accountId, PermissionCode permission) {
		if (accountId <= 0 || permission == null) return false;
		Instant now = clock.instant();
		Integer count = jdbc.sql("""
			SELECT COUNT(*)
			FROM platform_account_roles ar
			JOIN platform_accounts a ON a.id = ar.account_id
			JOIN platform_roles r ON r.id = ar.role_id
			JOIN platform_role_permissions rp ON rp.role_id = r.id
			JOIN platform_permissions p ON p.id = rp.permission_id
			WHERE ar.account_id = :accountId
			  AND a.account_status = 'ACTIVE'
			  AND r.active = TRUE
			  AND ar.valid_from <= :now
			  AND (ar.valid_to IS NULL OR ar.valid_to > :now)
			  AND p.permission_code = :permission
			""")
			.param("accountId", accountId)
			.param("now", now)
			.param("permission", permission.name())
			.query(Integer.class)
			.single();
		return count != null && count > 0;
	}

	@Transactional(readOnly = true)
	public boolean canAccessEmployee(long accountId, PermissionCode permission, long targetEmployeeId) {
		if (!hasPermission(accountId, permission) || targetEmployeeId <= 0) return false;
		Optional<AccessTarget> target = jdbc.sql("""
			SELECT a.employee_id AS actor_employee_id,
			       e.workplace_id AS target_workplace_id,
			       e.department_id AS target_department_id,
			       w.company_id AS target_company_id
			FROM platform_accounts a
			JOIN employees e ON e.id = :targetEmployeeId
			LEFT JOIN platform_workplaces w ON w.id = e.workplace_id
			WHERE a.id = :accountId AND a.account_status = 'ACTIVE'
			""")
			.param("targetEmployeeId", targetEmployeeId)
			.param("accountId", accountId)
			.query((rs, row) -> new AccessTarget(
				rs.getLong("actor_employee_id"),
				rs.getLong("target_workplace_id"),
				rs.getLong("target_department_id"),
				(Long) rs.getObject("target_company_id")))
			.optional();
		if (target.isEmpty()) return false;
		AccessTarget employee = target.get();
		if (employee.actorEmployeeId() == targetEmployeeId) return true;

		List<Scope> scopes = jdbc.sql("""
			SELECT scope_type, organization_id
			FROM platform_organization_scopes
			WHERE account_id = :accountId
			""")
			.param("accountId", accountId)
			.query((rs, row) -> new Scope(OrganizationScopeType.valueOf(rs.getString("scope_type")),
				(Long) rs.getObject("organization_id")))
			.list();
		if (scopes.isEmpty()) return false;

		Map<Long, DepartmentVersion> departments = currentDepartments();
		for (Scope scope : scopes) {
			if (matches(scope, employee, departments)) return true;
		}
		return false;
	}

	public void requirePermission(long accountId, PermissionCode permission) {
		if (!hasPermission(accountId, permission)) throw new AuthorizationDeniedException();
	}

	private Map<Long, DepartmentVersion> currentDepartments() {
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		List<DepartmentVersion> rows = jdbc.sql("""
			SELECT department_id, workplace_id, parent_department_id
			FROM platform_department_versions
			WHERE effective_from <= :today
			  AND (effective_to IS NULL OR effective_to >= :today)
			""")
			.param("today", today)
			.query((rs, row) -> new DepartmentVersion(rs.getLong("department_id"),
				rs.getLong("workplace_id"), (Long) rs.getObject("parent_department_id")))
			.list();
		Map<Long, DepartmentVersion> result = new HashMap<>();
		for (DepartmentVersion row : rows) {
			if (result.putIfAbsent(row.departmentId(), row) != null) return Map.of();
		}
		return result;
	}

	private static boolean matches(Scope scope, AccessTarget target, Map<Long, DepartmentVersion> departments) {
		if (scope.organizationId() == null) return false;
		return switch (scope.type()) {
			case SELF -> false;
			case COMPANY -> scope.organizationId().equals(target.companyId());
			case WORKPLACE -> scope.organizationId() == target.workplaceId();
			case DEPARTMENT -> scope.organizationId() == target.departmentId();
			case DEPARTMENT_TREE -> isInTree(scope.organizationId(), target, departments);
		};
	}

	private static boolean isInTree(long rootId, AccessTarget target, Map<Long, DepartmentVersion> departments) {
		long current = target.departmentId();
		for (int depth = 0; depth <= departments.size(); depth++) {
			if (current == rootId) return true;
			DepartmentVersion version = departments.get(current);
			if (version == null || version.workplaceId() != target.workplaceId()
					|| version.parentId() == null) return false;
			current = version.parentId();
		}
		return false;
	}

	private record AccessTarget(long actorEmployeeId, long workplaceId, long departmentId, Long companyId) {}
	private record Scope(OrganizationScopeType type, Long organizationId) {}
	private record DepartmentVersion(long departmentId, long workplaceId, Long parentId) {}
}
