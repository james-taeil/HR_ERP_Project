package com.jamestaeil.hrerp.platform.organization;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jamestaeil.hrerp.platform.authorization.AuthorizationQueryService;
import com.jamestaeil.hrerp.platform.authorization.OrganizationScopeType;
import com.jamestaeil.hrerp.platform.authorization.PermissionCode;
import com.jamestaeil.hrerp.platform.port.OrganizationSnapshotReader;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;

@Component
class PlatformOrganizationSnapshotReader implements OrganizationSnapshotReader {
	private final JdbcClient jdbc;
	private final AuthorizationQueryService authorization;

	PlatformOrganizationSnapshotReader(JdbcClient jdbc, AuthorizationQueryService authorization) {
		this.jdbc = jdbc;
		this.authorization = authorization;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Department> departmentsOn(long actorId, LocalDate date) {
		if (date == null || !authorization.hasPermission(actorId, PermissionCode.HR_LIFECYCLE_READ))
			throw new RecordAccessForbiddenException();
		jdbc.sql("SELECT employee_id FROM platform_accounts WHERE id=:id AND account_status='ACTIVE'")
			.param("id", actorId).query(Long.class).optional().orElseThrow(RecordAccessForbiddenException::new);
		List<Scope> scopes = jdbc.sql("SELECT scope_type, organization_id FROM platform_organization_scopes WHERE account_id=:id")
			.param("id", actorId)
			.query((rs, row) -> new Scope(OrganizationScopeType.valueOf(rs.getString("scope_type")),
				(Long) rs.getObject("organization_id"))).list();
		List<Version> versions = jdbc.sql("""
			SELECT v.department_id, v.workplace_id, v.parent_department_id, v.department_name, v.capacity, w.company_id
			FROM platform_department_versions v
			JOIN platform_workplaces w ON w.id=v.workplace_id
			JOIN platform_companies c ON c.id=w.company_id
			WHERE v.effective_from<=:date AND (v.effective_to IS NULL OR v.effective_to>=:date)
			AND w.opened_on<=:date AND (w.active_to IS NULL OR w.active_to>=:date)
			AND c.active_from<=:date AND (c.active_to IS NULL OR c.active_to>=:date)
			""").param("date", date)
			.query((rs, row) -> new Version(rs.getLong("department_id"), rs.getLong("workplace_id"),
				(Long) rs.getObject("parent_department_id"), rs.getString("department_name"),
				rs.getInt("capacity"), rs.getLong("company_id"))).list();
		Map<Long, Version> byId = new HashMap<>();
		for (Version version : versions) {
			if (byId.putIfAbsent(version.id(), version) != null) throw new RecordAccessForbiddenException();
		}
		return versions.stream().filter(version -> allowed(version, scopes, byId))
			.map(version -> new Department(version.id(), version.workplaceId(), version.parentId(),
				version.name(), version.capacity())).toList();
	}

	private static boolean allowed(Version version, List<Scope> scopes, Map<Long, Version> byId) {
		for (Scope scope : scopes) {
			if (switch (scope.type()) {
				case SELF -> false;
				case DEPARTMENT -> scope.organizationId() != null && scope.organizationId() == version.id();
				case DEPARTMENT_TREE -> scope.organizationId() != null && inTree(scope.organizationId(), version, byId);
				case WORKPLACE -> scope.organizationId() != null && scope.organizationId() == version.workplaceId();
				case COMPANY -> scope.organizationId() != null && scope.organizationId() == version.companyId();
			}) return true;
		}
		return false;
	}

	private static boolean inTree(long root, Version target, Map<Long, Version> byId) {
		Long cursor = target.id();
		Set<Long> seen = new HashSet<>();
		while (cursor != null && seen.add(cursor)) {
			if (cursor == root) return true;
			Version current = byId.get(cursor);
			if (current == null || current.workplaceId() != target.workplaceId()) return false;
			cursor = current.parentId();
		}
		return false;
	}

	private record Scope(OrganizationScopeType type, Long organizationId) {}
	private record Version(long id, long workplaceId, Long parentId, String name, int capacity, long companyId) {}
}
