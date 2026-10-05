package com.jamestaeil.hrerp.hr.employee.application;

import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.EmploymentStatus;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.EmployeeSearchScopeReader;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeSearchService {
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final EmployeeSearchScopeReader scopes;

    public EmployeeSearchService(JdbcTemplate jdbc, CurrentActorProvider actors, EmployeeSearchScopeReader scopes) {
        this.jdbc = jdbc; this.actors = actors; this.scopes = scopes;
    }
    public record Criteria(Long workplaceId, Long departmentId, EmploymentStatus employmentStatus,
                           EmploymentType employmentType, LocalDate hireDateFrom, LocalDate hireDateTo,
                           String position, String query, long afterId, int limit) {}
    public record Item(long id, String employeeNumber, String name, LocalDate hireDate,
                       EmploymentType employmentType, long workplaceId, long departmentId,
                       String position, EmploymentStatus employmentStatus) {}
    public record Page(List<Item> items, Long nextCursor) {}

    @Transactional(readOnly = true)
    public Page search(Criteria criteria) {
        validate(criteria);
        long actor = actors.requireActorId();
        if (actor <= 0) throw new IllegalArgumentException("Invalid actor");
        var scope = scopes.requireReadableScope(actor);
        if (scope.selfEmployeeId() == null && scope.departmentIds().isEmpty()) return new Page(List.of(), null);
        StringBuilder sql = new StringBuilder("""
            SELECT id, employee_number, employee_name, hire_date, employment_type, workplace_id,
                department_id, position_name, employment_status FROM employees WHERE id>?
            """);
        List<Object> args = new ArrayList<>(); args.add(criteria.afterId());
        sql.append(" AND (");
        if (scope.selfEmployeeId() != null) { sql.append("id=?"); args.add(scope.selfEmployeeId()); }
        if (!scope.departmentIds().isEmpty()) {
            if (scope.selfEmployeeId() != null) sql.append(" OR ");
            sql.append("department_id IN (").append("?,".repeat(scope.departmentIds().size()));
            sql.setLength(sql.length() - 1); sql.append(')'); args.addAll(scope.departmentIds());
        }
        sql.append(')');
        add(sql, args, "workplace_id=?", criteria.workplaceId());
        add(sql, args, "department_id=?", criteria.departmentId());
        add(sql, args, "employment_status=?", criteria.employmentStatus() == null ? null : criteria.employmentStatus().name());
        add(sql, args, "employment_type=?", criteria.employmentType() == null ? null : criteria.employmentType().name());
        add(sql, args, "hire_date>=?", criteria.hireDateFrom());
        add(sql, args, "hire_date<=?", criteria.hireDateTo());
        add(sql, args, "position_name=?", blank(criteria.position()) ? null : criteria.position().trim());
        if (!blank(criteria.query())) {
            sql.append(" AND (employee_name LIKE ? ESCAPE '!' OR employee_number LIKE ? ESCAPE '!')");
            String term = "%" + escape(criteria.query().trim()) + "%"; args.add(term); args.add(term);
        }
        sql.append(" ORDER BY id LIMIT ?"); args.add(criteria.limit() + 1);
        List<Item> rows = jdbc.query(sql.toString(), (rs, row) -> new Item(rs.getLong("id"),
            rs.getString("employee_number"), rs.getString("employee_name"), rs.getObject("hire_date", LocalDate.class),
            EmploymentType.valueOf(rs.getString("employment_type")), rs.getLong("workplace_id"),
            rs.getLong("department_id"), rs.getString("position_name"),
            EmploymentStatus.valueOf(rs.getString("employment_status"))), args.toArray());
        boolean more = rows.size() > criteria.limit();
        List<Item> items = more ? List.copyOf(rows.subList(0, criteria.limit())) : List.copyOf(rows);
        return new Page(items, more ? items.getLast().id() : null);
    }
    private static void add(StringBuilder sql, List<Object> args, String clause, Object value) {
        if (value != null) { sql.append(" AND ").append(clause); args.add(value); }
    }
    private static String escape(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void validate(Criteria c) {
        if (c == null || c.afterId() < 0 || c.limit() < 1 || c.limit() > 100
                || (c.workplaceId() != null && c.workplaceId() <= 0)
                || (c.departmentId() != null && c.departmentId() <= 0)
                || (c.hireDateFrom() != null && c.hireDateTo() != null && c.hireDateTo().isBefore(c.hireDateFrom()))
                || (c.position() != null && c.position().length() > 100)
                || (c.query() != null && c.query().length() > 100)) throw new IllegalArgumentException("Invalid employee search");
    }
}
