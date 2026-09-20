package com.jamestaeil.hrerp.platform.organization;

import static com.jamestaeil.hrerp.platform.authorization.PermissionCode.PLATFORM_ORGANIZATION_MANAGE;

import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jamestaeil.hrerp.platform.authorization.AuthorizationQueryService;
import com.jamestaeil.hrerp.platform.port.DepartmentMembershipReader;

@Service
public class OrganizationService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final JdbcClient jdbc;
	private final AuthorizationQueryService authorization;
	private final Clock clock;
	private final DepartmentMembershipReader membership;

	OrganizationService(JdbcClient jdbc, AuthorizationQueryService authorization, Clock clock,
			DepartmentMembershipReader membership) {
		this.jdbc = jdbc;
		this.authorization = authorization;
		this.clock = clock;
		this.membership = membership;
	}

	@Transactional
	public Company createCompany(long actor, String name, LocalDate activeFrom, LocalDate activeTo) {
		manage(actor);
		validatePeriod(activeFrom, activeTo);
		try {
			jdbc.sql("INSERT INTO platform_companies (company_name, active_from, active_to) VALUES (:name, :start, :end)")
				.param("name", name(name, 200)).param("start", activeFrom)
				.param("end", activeTo, Types.DATE).update();
		} catch (DataIntegrityViolationException exception) {
			throw new OrganizationConflictException("A company already exists");
		}
		return companyById(lastId());
	}

	@Transactional(readOnly = true)
	public Company company(long actor) {
		manage(actor);
		return jdbc.sql("SELECT id, company_name, active_from, active_to FROM platform_companies")
			.query((rs, row) -> new Company(rs.getLong("id"), rs.getString("company_name"),
				rs.getDate("active_from").toLocalDate(), date(rs.getDate("active_to"))))
			.optional().orElseThrow(() -> new OrganizationNotFoundException("Company not found"));
	}

	@Transactional
	public Workplace createWorkplace(long actor, long companyId, String name, String registrationNumber,
			String address, String industry, LocalDate openedOn, LocalDate activeTo) {
		manage(actor);
		validatePeriod(openedOn, activeTo);
		requireActiveCompany(companyId, openedOn);
		String number = number(registrationNumber);
		try {
			jdbc.sql("""
				INSERT INTO platform_workplaces
				(company_id, workplace_name, business_registration_number, address, industry, opened_on, active_to)
				VALUES (:company, :name, :number, :address, :industry, :opened, :end)
				""")
				.param("company", companyId).param("name", name(name, 200)).param("number", number)
				.param("address", name(address, 500)).param("industry", name(industry, 200))
				.param("opened", openedOn).param("end", activeTo, Types.DATE).update();
		} catch (DataIntegrityViolationException exception) {
			throw new OrganizationConflictException("Business registration number is already in use");
		}
		return workplace(lastId());
	}

	@Transactional
	public Workplace updateWorkplace(long actor, long workplaceId, String name, String registrationNumber,
			String address, String industry, LocalDate activeTo) {
		manage(actor);
		Workplace before = workplaceForUpdate(workplaceId);
		LocalDate end = activeTo == null ? before.activeTo() : activeTo;
		validatePeriod(before.openedOn(), end);
		try {
			jdbc.sql("""
				UPDATE platform_workplaces SET workplace_name=:name, business_registration_number=:number,
				address=:address, industry=:industry, active_to=:end WHERE id=:id
				""")
				.param("id", workplaceId).param("name", name == null ? before.name() : name(name, 200))
				.param("number", registrationNumber == null ? before.registrationNumber() : number(registrationNumber))
				.param("address", address == null ? before.address() : name(address, 500))
				.param("industry", industry == null ? before.industry() : name(industry, 200))
				.param("end", end, Types.DATE).update();
		} catch (DataIntegrityViolationException exception) {
			throw new OrganizationConflictException("Workplace change conflicts with existing data");
		}
		return workplace(workplaceId);
	}

	@Transactional(readOnly = true)
	public List<Workplace> workplaces(long actor, long afterId, int limit) {
		manage(actor);
		page(afterId, limit);
		return jdbc.sql("""
			SELECT id, company_id, workplace_name, business_registration_number, address, industry, opened_on, active_to
			FROM platform_workplaces WHERE id > :afterId ORDER BY id LIMIT :limit
			""").param("afterId", afterId).param("limit", limit).query(OrganizationService::mapWorkplace).list();
	}

	@Transactional
	public DepartmentVersion createDepartment(long actor, String code, String name, long workplaceId,
			Long parentId, LocalDate effectiveFrom, int capacity) {
		manage(actor);
		String stableCode = code(code);
		validateCapacity(capacity);
		requireActiveWorkplace(workplaceId, effectiveFrom);
		validateParent(0, parentId, workplaceId, effectiveFrom);
		try {
			jdbc.sql("INSERT INTO platform_departments (stable_code) VALUES (:code)")
				.param("code", stableCode).update();
		} catch (DataIntegrityViolationException exception) {
			throw new OrganizationConflictException("Department code is already in use");
		}
		long departmentId = lastId();
		insertVersion(departmentId, workplaceId, parentId, name(name, 200), effectiveFrom, null, 1, capacity);
		return version(departmentId, effectiveFrom);
	}

	@Transactional
	public DepartmentVersion addVersion(long actor, long departmentId, String name, long workplaceId,
			Long parentId, LocalDate effectiveFrom, LocalDate effectiveTo, int capacity) {
		manage(actor);
		validatePeriod(effectiveFrom, effectiveTo);
		validateCapacity(capacity);
		jdbc.sql("SELECT id FROM platform_departments WHERE id=:id FOR UPDATE")
			.param("id", departmentId).query(Long.class).optional()
			.orElseThrow(() -> new OrganizationNotFoundException("Department not found"));
		DepartmentVersion previous = jdbc.sql("""
			SELECT v.id, v.department_id, d.stable_code, v.workplace_id, v.parent_department_id,
		       v.department_name, v.effective_from, v.effective_to, v.version, v.capacity
			FROM platform_department_versions v JOIN platform_departments d ON d.id=v.department_id
			WHERE v.department_id=:id ORDER BY v.version DESC LIMIT 1 FOR UPDATE
			""").param("id", departmentId).query(OrganizationService::mapVersion).single();
		if (!effectiveFrom.isAfter(previous.effectiveFrom())
				|| previous.effectiveTo() != null && !effectiveFrom.isAfter(previous.effectiveTo())) {
			throw new OrganizationConflictException("Department effective periods overlap");
		}
		requireActiveWorkplace(workplaceId, effectiveFrom);
		validateParent(departmentId, parentId, workplaceId, effectiveFrom);
		if (workplaceId != previous.workplaceId() && hasActiveChildren(departmentId, effectiveFrom)) {
			throw new OrganizationConflictException("Move child departments before changing workplace");
		}
		List<Long> affectedEmployees = membership.employeeIdsIn(departmentId);
		if (effectiveTo == null && previous.effectiveTo() == null
				&& !affectedEmployees.isEmpty() && workplaceId != previous.workplaceId()) {
			throw new OrganizationConflictException("Move employees before changing workplace", affectedEmployees);
		}
		if (previous.effectiveTo() == null) {
			jdbc.sql("UPDATE platform_department_versions SET effective_to=:end WHERE id=:id")
				.param("end", effectiveFrom.minusDays(1)).param("id", previous.id()).update();
		}
		insertVersion(departmentId, workplaceId, parentId, name(name, 200), effectiveFrom, effectiveTo,
			previous.version() + 1, capacity);
		return version(departmentId, effectiveFrom);
	}

	@Transactional
	public void closeDepartment(long actor, long departmentId, LocalDate effectiveTo) {
		manage(actor);
		jdbc.sql("SELECT id FROM platform_departments WHERE id=:id FOR UPDATE")
			.param("id", departmentId).query(Long.class).optional()
			.orElseThrow(() -> new OrganizationNotFoundException("Department not found"));
		if (effectiveTo == null) throw new IllegalArgumentException("Closure date is required");
		DepartmentVersion current = jdbc.sql("""
			SELECT v.id, v.department_id, d.stable_code, v.workplace_id, v.parent_department_id,
			       v.department_name, v.effective_from, v.effective_to, v.version, v.capacity
			FROM platform_department_versions v JOIN platform_departments d ON d.id=v.department_id
			WHERE v.department_id=:id ORDER BY v.version DESC LIMIT 1 FOR UPDATE
			""").param("id", departmentId).query(OrganizationService::mapVersion).single();
		if (current.effectiveTo() != null) throw new OrganizationConflictException("Department is already closed");
		List<Long> affectedEmployees = membership.employeeIdsIn(departmentId);
		if (!affectedEmployees.isEmpty())
			throw new OrganizationConflictException("Move employees before closing department", affectedEmployees);
		if (hasActiveChildren(departmentId, effectiveTo)) {
			throw new OrganizationConflictException("Move child departments before closing department");
		}
		validatePeriod(current.effectiveFrom(), effectiveTo);
		jdbc.sql("UPDATE platform_department_versions SET effective_to=:end WHERE id=:id")
			.param("end", effectiveTo).param("id", current.id()).update();
	}

	@Transactional(readOnly = true)
	public List<DepartmentVersion> tree(long actor, LocalDate asOf) {
		manage(actor);
		LocalDate date = asOf == null ? LocalDate.now(clock.withZone(SEOUL)) : asOf;
		return jdbc.sql("""
			SELECT v.id, v.department_id, d.stable_code, v.workplace_id, v.parent_department_id,
		       v.department_name, v.effective_from, v.effective_to, v.version, v.capacity
			FROM platform_department_versions v JOIN platform_departments d ON d.id=v.department_id
			JOIN platform_workplaces w ON w.id=v.workplace_id
			JOIN platform_companies c ON c.id=w.company_id
			WHERE v.effective_from <= :date AND (v.effective_to IS NULL OR v.effective_to >= :date)
			AND w.opened_on <= :date AND (w.active_to IS NULL OR w.active_to >= :date)
			AND c.active_from <= :date AND (c.active_to IS NULL OR c.active_to >= :date)
			ORDER BY v.workplace_id, v.parent_department_id, v.department_id
			""").param("date", date).query(OrganizationService::mapVersion).list();
	}

	private void validateParent(long departmentId, Long parentId, long workplaceId, LocalDate date) {
		if (parentId == null) return;
		if (parentId == departmentId) throw new OrganizationConflictException("A department cannot parent itself");
		Map<Long, DepartmentVersion> versions = new HashMap<>();
		for (DepartmentVersion item : jdbc.sql("""
			SELECT v.id, v.department_id, d.stable_code, v.workplace_id, v.parent_department_id,
		       v.department_name, v.effective_from, v.effective_to, v.version, v.capacity
			FROM platform_department_versions v JOIN platform_departments d ON d.id=v.department_id
			WHERE v.effective_from <= :date AND (v.effective_to IS NULL OR v.effective_to >= :date)
			FOR UPDATE
			""").param("date", date).query(OrganizationService::mapVersion).list()) {
			if (versions.putIfAbsent(item.departmentId(), item) != null)
				throw new OrganizationConflictException("Overlapping department versions");
		}
		Set<Long> seen = new HashSet<>();
		Long cursor = parentId;
		while (cursor != null) {
			if (cursor == departmentId || !seen.add(cursor)) throw new OrganizationConflictException("Department cycle");
			DepartmentVersion ancestor = versions.get(cursor);
			if (ancestor == null) throw new OrganizationNotFoundException("Parent department is not active");
			if (ancestor.workplaceId() != workplaceId) throw new OrganizationConflictException("Parent workplace differs");
			cursor = ancestor.parentId();
		}
	}

	private void insertVersion(long departmentId, long workplaceId, Long parentId, String name,
			LocalDate from, LocalDate to, long version, int capacity) {
		jdbc.sql("""
			INSERT INTO platform_department_versions
			(department_id, workplace_id, parent_department_id, department_name, effective_from, effective_to, version, capacity)
			VALUES (:department, :workplace, :parent, :name, :start, :end, :version, :capacity)
			""").param("department", departmentId).param("workplace", workplaceId)
			.param("parent", parentId, Types.BIGINT).param("name", name).param("start", from)
			.param("end", to, Types.DATE).param("version", version).param("capacity", capacity).update();
	}

	private Company companyById(long id) {
		return jdbc.sql("SELECT id, company_name, active_from, active_to FROM platform_companies WHERE id=:id")
			.param("id", id).query((rs, row) -> new Company(rs.getLong("id"), rs.getString("company_name"),
				rs.getDate("active_from").toLocalDate(), date(rs.getDate("active_to")))).single();
	}

	private Workplace workplace(long id) {
		return jdbc.sql("""
			SELECT id, company_id, workplace_name, business_registration_number, address, industry, opened_on, active_to
			FROM platform_workplaces WHERE id=:id
			""").param("id", id).query(OrganizationService::mapWorkplace)
			.optional().orElseThrow(() -> new OrganizationNotFoundException("Workplace not found"));
	}

	private Workplace workplaceForUpdate(long id) {
		return jdbc.sql("""
			SELECT id, company_id, workplace_name, business_registration_number, address, industry, opened_on, active_to
			FROM platform_workplaces WHERE id=:id FOR UPDATE
			""").param("id", id).query(OrganizationService::mapWorkplace)
			.optional().orElseThrow(() -> new OrganizationNotFoundException("Workplace not found"));
	}

	private DepartmentVersion version(long departmentId, LocalDate date) {
		return jdbc.sql("""
			SELECT v.id, v.department_id, d.stable_code, v.workplace_id, v.parent_department_id,
		       v.department_name, v.effective_from, v.effective_to, v.version, v.capacity
			FROM platform_department_versions v JOIN platform_departments d ON d.id=v.department_id
			WHERE v.department_id=:id AND v.effective_from<=:date
			AND (v.effective_to IS NULL OR v.effective_to>=:date)
			""").param("id", departmentId).param("date", date).query(OrganizationService::mapVersion)
			.optional().orElseThrow(() -> new OrganizationNotFoundException("Department version not found"));
	}

	private void requireActiveCompany(long id, LocalDate date) {
		Integer count = jdbc.sql("""
			SELECT COUNT(*) FROM platform_companies WHERE id=:id AND active_from<=:date
			AND (active_to IS NULL OR active_to>=:date)
			""").param("id", id).param("date", date).query(Integer.class).single();
		if (count != 1) throw new OrganizationNotFoundException("Active company not found");
	}

	private void requireActiveWorkplace(long id, LocalDate date) {
		Integer count = jdbc.sql("""
			SELECT COUNT(*) FROM platform_workplaces w JOIN platform_companies c ON c.id=w.company_id
			WHERE w.id=:id AND w.opened_on<=:date AND (w.active_to IS NULL OR w.active_to>=:date)
			AND c.active_from<=:date AND (c.active_to IS NULL OR c.active_to>=:date)
			""").param("id", id).param("date", date).query(Integer.class).single();
		if (count != 1) throw new OrganizationNotFoundException("Active workplace not found");
	}

	private boolean hasActiveChildren(long departmentId, LocalDate date) {
		return jdbc.sql("""
			SELECT COUNT(*) FROM platform_department_versions
			WHERE parent_department_id=:id AND effective_from<=:date
			AND (effective_to IS NULL OR effective_to>=:date)
			""").param("id", departmentId).param("date", date).query(Integer.class).single() > 0;
	}

	private long lastId() {
		return jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
	}

	private void manage(long actor) { authorization.requirePermission(actor, PLATFORM_ORGANIZATION_MANAGE); }
	private static void page(long afterId, int limit) {
		if (afterId < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid cursor page");
	}
	private static void validateCapacity(int capacity) {
		if (capacity < 0) throw new IllegalArgumentException("Capacity must not be negative");
	}
	private static void validatePeriod(LocalDate from, LocalDate to) {
		if (from == null || to != null && to.isBefore(from)) throw new IllegalArgumentException("Invalid active period");
	}
	private static String name(String value, int max) {
		if (value == null || value.isBlank() || value.strip().length() > max)
			throw new IllegalArgumentException("Invalid organization value");
		return value.strip();
	}
	private static String number(String value) {
		if (value == null) throw new IllegalArgumentException("Business registration number is required");
		String digits = value.replace("-", "");
		if (!digits.matches("[0-9]{10}")) throw new IllegalArgumentException("Invalid business registration number");
		return digits;
	}
	private static String code(String value) {
		if (value == null || !value.matches("0[1-9]|[1-9][0-9]"))
			throw new IllegalArgumentException("Department code must be 01-99");
		return value;
	}
	private static LocalDate date(java.sql.Date value) { return value == null ? null : value.toLocalDate(); }
	private static Workplace mapWorkplace(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new Workplace(rs.getLong("id"), rs.getLong("company_id"), rs.getString("workplace_name"),
			rs.getString("business_registration_number"), rs.getString("address"), rs.getString("industry"),
			rs.getDate("opened_on").toLocalDate(), date(rs.getDate("active_to")));
	}
	private static DepartmentVersion mapVersion(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new DepartmentVersion(rs.getLong("id"), rs.getLong("department_id"), rs.getString("stable_code"),
			rs.getLong("workplace_id"), (Long) rs.getObject("parent_department_id"),
			rs.getString("department_name"), rs.getDate("effective_from").toLocalDate(),
			date(rs.getDate("effective_to")), rs.getLong("version"), rs.getInt("capacity"));
	}

	public record Company(long id, String name, LocalDate activeFrom, LocalDate activeTo) {}
	public record Workplace(long id, long companyId, String name, String registrationNumber, String address,
		String industry, LocalDate openedOn, LocalDate activeTo) {}
	public record DepartmentVersion(long id, long departmentId, String code, long workplaceId, Long parentId,
		String name, LocalDate effectiveFrom, LocalDate effectiveTo, long version, int capacity) {}
}
