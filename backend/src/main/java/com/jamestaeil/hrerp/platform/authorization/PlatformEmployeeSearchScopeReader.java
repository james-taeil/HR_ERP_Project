package com.jamestaeil.hrerp.platform.authorization;

import com.jamestaeil.hrerp.platform.port.EmployeeSearchScopeReader;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PlatformEmployeeSearchScopeReader implements EmployeeSearchScopeReader {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcClient jdbc;
    private final AuthorizationQueryService authorization;
    private final Clock clock;

    PlatformEmployeeSearchScopeReader(JdbcClient jdbc, AuthorizationQueryService authorization, Clock clock) {
        this.jdbc = jdbc; this.authorization = authorization; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Scope requireReadableScope(long actorId) {
        if (!authorization.hasPermission(actorId, PermissionCode.HR_RECORD_READ))
            throw new RecordAccessForbiddenException();
        Long self = jdbc.sql("""
            SELECT employee_id FROM platform_accounts WHERE id=:id AND account_status='ACTIVE'
                AND (disabled_at IS NULL OR disabled_at>:now)
            """).param("id", actorId).param("now", clock.instant()).query(Long.class)
            .optional().orElseThrow(RecordAccessForbiddenException::new);
        List<OrgScope> scopes = jdbc.sql("SELECT scope_type, organization_id FROM platform_organization_scopes WHERE account_id=:id")
            .param("id", actorId).query((rs, row) -> new OrgScope(
                OrganizationScopeType.valueOf(rs.getString(1)), (Long) rs.getObject(2))).list();
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        List<Department> departments = jdbc.sql("""
            SELECT v.department_id, v.workplace_id, v.parent_department_id, w.company_id
            FROM platform_department_versions v JOIN platform_workplaces w ON w.id=v.workplace_id
            WHERE v.effective_from<=:today AND (v.effective_to IS NULL OR v.effective_to>=:today)
              AND w.opened_on<=:today AND (w.active_to IS NULL OR w.active_to>=:today)
            """).param("today", today).query((rs, row) -> new Department(rs.getLong(1), rs.getLong(2),
                (Long) rs.getObject(3), rs.getLong(4))).list();
        Map<Long, Department> byId = new HashMap<>();
        for (Department department : departments) {
            if (byId.putIfAbsent(department.id(), department) != null) throw new RecordAccessForbiddenException();
        }
        Set<Long> allowed = new HashSet<>();
        for (Department department : departments) for (OrgScope scope : scopes) {
            if (matches(scope, department, byId)) { allowed.add(department.id()); break; }
        }
        return new Scope(self, allowed);
    }

    private static boolean matches(OrgScope scope, Department department, Map<Long, Department> byId) {
        if (scope.organizationId() == null) return false;
        return switch (scope.type()) {
            case SELF -> false;
            case COMPANY -> scope.organizationId() == department.companyId();
            case WORKPLACE -> scope.organizationId() == department.workplaceId();
            case DEPARTMENT -> scope.organizationId() == department.id();
            case DEPARTMENT_TREE -> inTree(scope.organizationId(), department, byId);
        };
    }
    private static boolean inTree(long root, Department target, Map<Long, Department> byId) {
        Long current = target.id(); Set<Long> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current == root) return true;
            Department node = byId.get(current);
            if (node == null || node.workplaceId() != target.workplaceId()) return false;
            current = node.parentId();
        }
        return false;
    }
    private record OrgScope(OrganizationScopeType type, Long organizationId) {}
    private record Department(long id, long workplaceId, Long parentId, long companyId) {}
}
