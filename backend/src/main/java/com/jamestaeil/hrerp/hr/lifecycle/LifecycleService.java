package com.jamestaeil.hrerp.hr.lifecycle;

import static com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.*;
import static com.jamestaeil.hrerp.platform.port.LifecycleTaskPublisher.Kind.*;

import com.jamestaeil.hrerp.platform.port.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuthorizationChecker authorization;
    private final OrganizationReader organization;
    private final OrganizationSnapshotReader organizationSnapshots;
    private final FileStorage files;
    private final RecordAudit audit;
    private final AccountAccessScheduler accounts;
    private final LifecycleTaskPublisher tasks;

    public LifecycleService(JdbcTemplate jdbc, CurrentActorProvider actors, AuthorizationChecker authorization,
                            OrganizationReader organization, OrganizationSnapshotReader organizationSnapshots,
                            FileStorage files, RecordAudit audit, AccountAccessScheduler accounts,
                            LifecycleTaskPublisher tasks) {
        this.jdbc = jdbc; this.actors = actors; this.authorization = authorization;
        this.organization = organization; this.organizationSnapshots = organizationSnapshots;
        this.files = files; this.audit = audit; this.accounts = accounts; this.tasks = tasks;
    }

    public record AppointmentCommand(String idempotencyKey, LocalDate effectiveDate, AppointmentType type,
                                     Long workplaceId, Long departmentId, String position,
                                     EmploymentStatus status, String reason, Long evidenceFileId) {}
    public record TerminationCommand(String idempotencyKey, LocalDate terminationDate,
                                     String reasonCode, boolean separationCertificateRequired) {}

    @Transactional
    public Saved appoint(long employeeId, AppointmentCommand command) {
        validateEmployeeAndKey(employeeId, command == null ? null : command.idempotencyKey());
        long actor = actorFor(employeeId, true);
        EmployeeState employee = lockEmployee(employeeId);
        Appointment prior = findByKey(actor, command.idempotencyKey());
        if (prior != null) {
            if (!sameRequest(prior, employeeId, command)) throw new LifecycleConflictException();
            return new Saved(prior.id(), prior.appointmentStatus());
        }
        ensureNoPending(employeeId);
        Snapshot after = changed(employee.snapshot(), command, false);
        if (after.equals(employee.snapshot())) throw new IllegalArgumentException();
        if (command.evidenceFileId() != null) files.requireUsableEvidence(actor, employeeId, command.evidenceFileId());
        AppointmentStatus state = command.effectiveDate().isAfter(today()) ? AppointmentStatus.SCHEDULED : AppointmentStatus.APPLIED;
        insertAppointment(employeeId, actor, command.idempotencyKey(), command.effectiveDate(), command.type(), state,
            employee.snapshot(), after, command.reason(), command.evidenceFileId());
        Appointment saved = requireByKey(actor, command.idempotencyKey());
        if (state == AppointmentStatus.APPLIED) applySnapshot(employeeId, after);
        audit.changed(actor, employeeId, "APPOINTMENT", saved.id(), employee.snapshot(), after);
        return new Saved(saved.id(), state);
    }

    @Transactional
    public long terminate(long employeeId, TerminationCommand command) {
        validateEmployeeAndKey(employeeId, command == null ? null : command.idempotencyKey());
        if (command.terminationDate() == null || command.reasonCode() == null
                || !command.reasonCode().matches("[A-Z0-9_-]{1,50}")) throw new IllegalArgumentException();
        long actor = actorFor(employeeId, true);
        EmployeeState employee = lockEmployee(employeeId);
        TerminationRecord prior = jdbc.query("""
            SELECT id, employee_id, termination_date, reason_code, separation_certificate_required
            FROM terminations WHERE actor_id=? AND idempotency_key=?
            """, rs -> rs.next() ? new TerminationRecord(rs.getLong("id"), rs.getLong("employee_id"),
                rs.getObject("termination_date", LocalDate.class), rs.getString("reason_code"),
                rs.getBoolean("separation_certificate_required")) : null, actor, command.idempotencyKey());
        if (prior != null) {
            if (prior.employeeId() != employeeId || !prior.date().equals(command.terminationDate())
                    || !prior.reasonCode().equals(command.reasonCode())
                    || prior.certificateRequired() != command.separationCertificateRequired())
                throw new LifecycleConflictException();
            return prior.id();
        }
        if (command.terminationDate().isBefore(employee.hireDate())) throw new IllegalArgumentException();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM terminations WHERE employee_id=?", Integer.class, employeeId) != 0)
            throw new LifecycleConflictException();
        ensureNoPending(employeeId);
        requireTransition(employee.snapshot().status(), EmploymentStatus.TERMINATED);
        Snapshot after = new Snapshot(employee.snapshot().workplaceId(), employee.snapshot().departmentId(),
            employee.snapshot().position(), EmploymentStatus.TERMINATED);
        AppointmentStatus state = command.terminationDate().isAfter(today()) ? AppointmentStatus.SCHEDULED : AppointmentStatus.APPLIED;
        insertAppointment(employeeId, actor, command.idempotencyKey(), command.terminationDate(),
            AppointmentType.STATUS_CHANGE, state, employee.snapshot(), after, "TERMINATION:" + command.reasonCode(), null);
        long appointmentId = requireByKey(actor, command.idempotencyKey()).id();
        jdbc.update("""
            INSERT INTO terminations (employee_id, appointment_id, actor_id, idempotency_key, termination_date,
                reason_code, separation_certificate_required) VALUES (?, ?, ?, ?, ?, ?, ?)
            """, employeeId, appointmentId, actor, command.idempotencyKey(), command.terminationDate(),
            command.reasonCode(), command.separationCertificateRequired());
        long terminationId = jdbc.queryForObject("SELECT id FROM terminations WHERE employee_id=?", Long.class, employeeId);
        for (LifecycleTaskPublisher.Kind kind : LifecycleTaskPublisher.Kind.values()) {
            jdbc.update("INSERT INTO offboarding_checklist_items (termination_id, item_type) VALUES (?, ?)",
                terminationId, kind.name());
            tasks.publish(new LifecycleTaskPublisher.Task(kind, employeeId, command.terminationDate()));
        }
        accounts.disableFrom(employeeId, command.terminationDate().plusDays(1));
        if (state == AppointmentStatus.APPLIED) applySnapshot(employeeId, after);
        audit.changed(actor, employeeId, "TERMINATION", terminationId, employee.snapshot(), after);
        return terminationId;
    }

    @Transactional
    public List<Appointment> appointments(long employeeId) {
        long actor = actorFor(employeeId, false);
        List<Appointment> result = jdbc.query(APPOINTMENT_SELECT +
            " WHERE employee_id=? ORDER BY effective_date, id", LifecycleService::mapAppointment, employeeId);
        audit.viewed(actor, employeeId, "APPOINTMENT");
        return result;
    }

    @Transactional
    public OrganizationChart organizationChart(LocalDate date) {
        if (date == null) throw new IllegalArgumentException();
        long actor = requireActor();
        List<Department> departments = organizationSnapshots.departmentsOn(actor, date).stream()
            .map(d -> new Department(d.id(), d.workplaceId(), d.parentId(), d.name(), d.capacity())).toList();
        Set<Long> allowed = new HashSet<>(departments.stream().map(Department::id).toList());
        List<OrganizationEmployee> employees = employeeSnapshots(date).stream()
            .filter(e -> allowed.contains(e.departmentId())).toList();
        return new OrganizationChart(date, departments, employees);
    }

    @Transactional
    public Headcount headcount(LocalDate date) {
        OrganizationChart chart = organizationChart(date);
        Map<Long, Long> current = new HashMap<>();
        chart.employees().stream().filter(e -> e.status() == EmploymentStatus.ACTIVE)
            .forEach(e -> current.merge(e.departmentId(), 1L, Long::sum));
        return new Headcount(date, chart.departments().stream().map(d -> {
            long count = current.getOrDefault(d.id(), 0L);
            return new DepartmentCount(d.id(), d.capacity(), count, d.capacity() - count);
        }).toList());
    }

    @Transactional
    public void applyDue(LocalDate date) {
        Integer locked = jdbc.queryForObject("SELECT GET_LOCK('hr_appointment_batch', 10)", Integer.class);
        if (!Integer.valueOf(1).equals(locked)) throw new IllegalStateException("Could not acquire appointment batch lock");
        try {
            List<Long> ids = jdbc.queryForList("""
                SELECT id FROM appointments WHERE appointment_status='SCHEDULED' AND effective_date <= ? ORDER BY effective_date, id
                """, Long.class, date);
            for (long id : ids) applyAppointment(id);
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK('hr_appointment_batch')", Integer.class);
        }
    }

    private void applyAppointment(long id) {
        Appointment appointment = jdbc.query(APPOINTMENT_SELECT + " WHERE id=? FOR UPDATE",
            rs -> rs.next() ? mapAppointment(rs, 0) : null, id);
        if (appointment == null || appointment.appointmentStatus() == AppointmentStatus.APPLIED) return;
        lockEmployee(appointment.employeeId());
        int updated = jdbc.update("""
            UPDATE appointments SET appointment_status='APPLIED', applied_at=CURRENT_TIMESTAMP(6), version=version+1
            WHERE id=? AND appointment_status='SCHEDULED'
            """, id);
        if (updated == 1) {
            applySnapshot(appointment.employeeId(), appointment.after());
            audit.changed(appointmentActor(id), appointment.employeeId(), "APPOINTMENT_APPLIED", id,
                appointment.before(), appointment.after());
        }
    }

    private List<OrganizationEmployee> employeeSnapshots(LocalDate date) {
        return jdbc.query("SELECT id FROM employees WHERE hire_date <= ? ORDER BY id", (rs, row) -> rs.getLong(1), date)
            .stream().map(id -> {
                Snapshot snapshot = snapshotAt(id, date);
                return new OrganizationEmployee(id, snapshot.workplaceId(), snapshot.departmentId(),
                    snapshot.position(), snapshot.status());
            }).toList();
    }

    private Snapshot snapshotAt(long employeeId, LocalDate date) {
        boolean future = date.isAfter(today());
        String stateClause = future ? "" : " AND appointment_status='APPLIED'";
        List<Appointment> before = jdbc.query(APPOINTMENT_SELECT +
            " WHERE employee_id=? AND effective_date <= ?" + stateClause + " ORDER BY effective_date DESC, id DESC LIMIT 1",
            LifecycleService::mapAppointment, employeeId, date);
        if (!before.isEmpty()) return before.getFirst().after();
        List<Appointment> after = jdbc.query(APPOINTMENT_SELECT +
            " WHERE employee_id=? AND effective_date > ? AND appointment_status='APPLIED' ORDER BY effective_date, id LIMIT 1",
            LifecycleService::mapAppointment, employeeId, date);
        return after.isEmpty() ? lockFreeEmployee(employeeId).snapshot() : after.getFirst().before();
    }

    private Snapshot changed(Snapshot before, AppointmentCommand command, boolean termination) {
        if (command.effectiveDate() == null || command.type() == null || command.reason() == null
                || command.reason().isBlank() || command.reason().length() > 500) throw new IllegalArgumentException();
        return switch (command.type()) {
            case DEPARTMENT_CHANGE -> {
                if (command.departmentId() == null || command.departmentId() <= 0) throw new IllegalArgumentException();
                organization.requireActiveDepartment(before.workplaceId(), command.departmentId());
                yield new Snapshot(before.workplaceId(), command.departmentId(), before.position(), before.status());
            }
            case WORKPLACE_CHANGE -> {
                if (command.workplaceId() == null || command.workplaceId() <= 0
                        || command.departmentId() == null || command.departmentId() <= 0) throw new IllegalArgumentException();
                organization.requireActiveDepartment(command.workplaceId(), command.departmentId());
                yield new Snapshot(command.workplaceId(), command.departmentId(), before.position(), before.status());
            }
            case POSITION_CHANGE -> {
                if (command.position() == null || command.position().isBlank() || command.position().length() > 100)
                    throw new IllegalArgumentException();
                yield new Snapshot(before.workplaceId(), before.departmentId(), command.position(), before.status());
            }
            case STATUS_CHANGE -> {
                if (command.status() == null || (!termination && command.status() == EmploymentStatus.TERMINATED))
                    throw new IllegalArgumentException();
                requireTransition(before.status(), command.status());
                yield new Snapshot(before.workplaceId(), before.departmentId(), before.position(), command.status());
            }
        };
    }

    private static void requireTransition(EmploymentStatus before, EmploymentStatus after) {
        if (!before.allowedNext().contains(after)) throw new InvalidStatusTransitionException(before.allowedNext());
    }

    private void insertAppointment(long employeeId, long actor, String key, LocalDate date, AppointmentType type,
                                   AppointmentStatus status, Snapshot before, Snapshot after, String reason, Long fileId) {
        jdbc.update("""
            INSERT INTO appointments (employee_id, actor_id, idempotency_key, effective_date, appointment_type,
                appointment_status, before_workplace_id, after_workplace_id, before_department_id, after_department_id,
                before_position_name, after_position_name, before_employment_status, after_employment_status,
                reason, evidence_file_id, applied_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                CASE WHEN ?='APPLIED' THEN CURRENT_TIMESTAMP(6) ELSE NULL END)
            """, employeeId, actor, key, date, type.name(), status.name(), before.workplaceId(), after.workplaceId(),
            before.departmentId(), after.departmentId(), before.position(), after.position(), before.status().name(),
            after.status().name(), reason, fileId, status.name());
    }

    private void applySnapshot(long employeeId, Snapshot snapshot) {
        jdbc.update("""
            UPDATE employees SET workplace_id=?, department_id=?, position_name=?, employment_status=?, version=version+1
            WHERE id=?
            """, snapshot.workplaceId(), snapshot.departmentId(), snapshot.position(), snapshot.status().name(), employeeId);
    }

    private EmployeeState lockEmployee(long employeeId) {
        List<EmployeeState> rows = jdbc.query("""
            SELECT workplace_id, department_id, position_name, employment_status, hire_date
            FROM employees WHERE id=? FOR UPDATE
            """, LifecycleService::mapEmployee, employeeId);
        if (rows.isEmpty()) throw new LifecycleNotFoundException();
        return rows.getFirst();
    }

    private EmployeeState lockFreeEmployee(long employeeId) {
        List<EmployeeState> rows = jdbc.query("""
            SELECT workplace_id, department_id, position_name, employment_status, hire_date FROM employees WHERE id=?
            """, LifecycleService::mapEmployee, employeeId);
        if (rows.isEmpty()) throw new LifecycleNotFoundException();
        return rows.getFirst();
    }

    private void ensureNoPending(long employeeId) {
        if (!jdbc.queryForList("""
            SELECT id FROM appointments WHERE employee_id=? AND appointment_status='SCHEDULED' FOR UPDATE
            """, Long.class, employeeId).isEmpty()) throw new LifecycleConflictException();
    }

    private long actorFor(long employeeId, boolean write) {
        long actor = requireActor();
        if (write) authorization.checkCanWriteLifecycle(actor, employeeId);
        else authorization.checkCanReadLifecycle(actor, employeeId);
        return actor;
    }

    private long requireActor() {
        long actor = actors.requireActorId();
        if (actor <= 0) throw new LifecycleForbiddenException();
        return actor;
    }

    private static void validateEmployeeAndKey(long employeeId, String key) {
        if (employeeId <= 0 || key == null || !key.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException();
    }

    private Appointment findByKey(long actor, String key) {
        List<Appointment> rows = jdbc.query(APPOINTMENT_SELECT + " WHERE actor_id=? AND idempotency_key=?",
            LifecycleService::mapAppointment, actor, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Appointment requireByKey(long actor, String key) {
        Appointment result = findByKey(actor, key);
        if (result == null) throw new IllegalStateException("Appointment insert failed");
        return result;
    }

    private boolean sameRequest(Appointment prior, long employeeId, AppointmentCommand command) {
        if (prior.employeeId() != employeeId || prior.type() != command.type()
                || !prior.effectiveDate().equals(command.effectiveDate()) || !prior.reason().equals(command.reason())
                || !Objects.equals(prior.evidenceFileId(), command.evidenceFileId())) return false;
        return switch (command.type()) {
            case DEPARTMENT_CHANGE -> Objects.equals(command.departmentId(), prior.after().departmentId());
            case WORKPLACE_CHANGE -> Objects.equals(command.workplaceId(), prior.after().workplaceId())
                && Objects.equals(command.departmentId(), prior.after().departmentId());
            case POSITION_CHANGE -> Objects.equals(command.position(), prior.after().position());
            case STATUS_CHANGE -> command.status() == prior.after().status();
        };
    }

    private long appointmentActor(long id) {
        return jdbc.queryForObject("SELECT actor_id FROM appointments WHERE id=?", Long.class, id);
    }

    private static EmployeeState mapEmployee(ResultSet rs, int row) throws SQLException {
        return new EmployeeState(new Snapshot(rs.getLong("workplace_id"), rs.getLong("department_id"),
            rs.getString("position_name"), EmploymentStatus.valueOf(rs.getString("employment_status"))),
            rs.getObject("hire_date", LocalDate.class));
    }

    private static Appointment mapAppointment(ResultSet rs, int row) throws SQLException {
        Long fileId = rs.getObject("evidence_file_id", Long.class);
        return new Appointment(rs.getLong("id"), rs.getLong("employee_id"),
            rs.getObject("effective_date", LocalDate.class), AppointmentType.valueOf(rs.getString("appointment_type")),
            AppointmentStatus.valueOf(rs.getString("appointment_status")),
            new Snapshot(rs.getLong("before_workplace_id"), rs.getLong("before_department_id"),
                rs.getString("before_position_name"), EmploymentStatus.valueOf(rs.getString("before_employment_status"))),
            new Snapshot(rs.getLong("after_workplace_id"), rs.getLong("after_department_id"),
                rs.getString("after_position_name"), EmploymentStatus.valueOf(rs.getString("after_employment_status"))),
            rs.getString("reason"), fileId);
    }

    private LocalDate today() { return LocalDate.now(SEOUL); }
    private record EmployeeState(Snapshot snapshot, LocalDate hireDate) {}
    private record TerminationRecord(long id, long employeeId, LocalDate date, String reasonCode,
                                     boolean certificateRequired) {}

    private static final String APPOINTMENT_SELECT = """
        SELECT id, employee_id, effective_date, appointment_type, appointment_status,
            before_workplace_id, after_workplace_id, before_department_id, after_department_id,
            before_position_name, after_position_name, before_employment_status, after_employment_status,
            reason, evidence_file_id FROM appointments
        """;
}

class LifecycleConflictException extends RuntimeException {}
class LifecycleNotFoundException extends RuntimeException {}
class LifecycleForbiddenException extends RuntimeException {}
class InvalidStatusTransitionException extends RuntimeException {
    private final List<EmploymentStatus> allowed;
    InvalidStatusTransitionException(List<EmploymentStatus> allowed) { this.allowed = allowed; }
    List<EmploymentStatus> allowed() { return allowed; }
}
