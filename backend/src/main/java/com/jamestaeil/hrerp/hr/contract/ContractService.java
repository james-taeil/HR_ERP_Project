package com.jamestaeil.hrerp.hr.contract;

import static com.jamestaeil.hrerp.hr.contract.ContractTypes.*;

import com.jamestaeil.hrerp.platform.port.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuthorizationChecker authorization;
    private final RecordAudit audit;

    public ContractService(JdbcTemplate jdbc, CurrentActorProvider actors,
                           AuthorizationChecker authorization, RecordAudit audit) {
        this.jdbc = jdbc; this.actors = actors; this.authorization = authorization; this.audit = audit;
    }

    @Transactional
    public Saved createContract(long employeeId, ContractCommand command) {
        validate(employeeId, command);
        long actor = authorize(employeeId, true);
        lockEmployee(employeeId);
        Contract prior = findContract(actor, command.idempotencyKey());
        if (prior != null) {
            if (!same(prior, employeeId, command)) throw new ContractConflictException();
            return new Saved(prior.id());
        }
        if (jdbc.queryForObject("""
            SELECT COUNT(*) FROM employment_contracts
            WHERE employee_id=? AND contract_start <= ? AND contract_end >= ?
            """, Integer.class, employeeId, command.contractEnd(), command.contractStart()) != 0)
            throw new ContractConflictException();
        try {
            jdbc.update("""
                INSERT INTO employment_contracts (employee_id, actor_id, idempotency_key, contract_start,
                    contract_end, work_location, weekly_work_minutes, agreed_monthly_wage,
                    probation_start, probation_end, probation_terms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, employeeId, actor, command.idempotencyKey(), command.contractStart(), command.contractEnd(),
                command.workLocation(), command.weeklyWorkMinutes(), command.agreedMonthlyWage(),
                command.probationStart(), command.probationEnd(), command.probationTerms());
        } catch (DataIntegrityViolationException failure) { throw new ContractConflictException(); }
        Contract saved = Objects.requireNonNull(findContract(actor, command.idempotencyKey()));
        audit.changed(actor, employeeId, "EMPLOYMENT_CONTRACT", saved.id(), null, saved);
        return new Saved(saved.id());
    }

    @Transactional
    public List<Contract> contracts(long employeeId) {
        long actor = authorize(employeeId, false);
        requireEmployee(employeeId);
        List<Contract> result = contractsForCard(employeeId);
        audit.viewed(actor, employeeId, "EMPLOYMENT_CONTRACT");
        return result;
    }

    public List<Contract> contractsForCard(long employeeId) {
        return jdbc.query("""
            SELECT id, employee_id, contract_start, contract_end, work_location, weekly_work_minutes,
                agreed_monthly_wage, probation_start, probation_end, probation_terms
            FROM employment_contracts WHERE employee_id=? ORDER BY contract_start, id
            """, ContractService::mapContract, employeeId);
    }

    @Transactional
    public Saved createWage(long employeeId, WageCommand command) {
        validate(employeeId, command);
        long actor = authorize(employeeId, true);
        lockEmployee(employeeId);
        WageContract prior = findWage(actor, command.idempotencyKey());
        if (prior != null) {
            if (prior.employeeId() != employeeId || !prior.effectiveFrom().equals(command.effectiveFrom())
                    || !prior.items().equals(command.items())) throw new ContractConflictException();
            return new Saved(prior.id());
        }
        try {
            jdbc.update("INSERT INTO wage_contracts (employee_id, actor_id, idempotency_key, effective_from) VALUES (?, ?, ?, ?)",
                employeeId, actor, command.idempotencyKey(), command.effectiveFrom());
            long id = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id FROM wage_contracts WHERE actor_id=? AND idempotency_key=?", Long.class,
                actor, command.idempotencyKey()));
            int line = 1;
            for (WageItem item : command.items()) jdbc.update("""
                INSERT INTO wage_contract_items (wage_contract_id, line_number, item_name, item_category,
                    amount, taxable, ordinary_wage) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, line++, item.itemName(), item.category().name(), item.amount(), item.taxable(), item.ordinaryWage());
        } catch (DataIntegrityViolationException failure) { throw new ContractConflictException(); }
        WageContract saved = Objects.requireNonNull(findWage(actor, command.idempotencyKey()));
        audit.changed(actor, employeeId, "WAGE_CONTRACT", saved.id(), null, saved);
        return new Saved(saved.id());
    }

    @Transactional
    public List<WageContract> wages(long employeeId) {
        long actor = authorize(employeeId, false);
        requireEmployee(employeeId);
        List<WageContract> result = jdbc.query(
            "SELECT id, employee_id, effective_from FROM wage_contracts WHERE employee_id=? ORDER BY effective_from, id",
            (rs, row) -> wage(rs), employeeId);
        audit.viewed(actor, employeeId, "WAGE_CONTRACT");
        return result;
    }

    private WageContract findWage(long actor, String key) {
        List<WageContract> rows = jdbc.query(
            "SELECT id, employee_id, effective_from FROM wage_contracts WHERE actor_id=? AND idempotency_key=?",
            (rs, row) -> wage(rs), actor, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private WageContract wage(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        List<WageItem> items = jdbc.query("""
            SELECT item_name, item_category, amount, taxable, ordinary_wage FROM wage_contract_items
            WHERE wage_contract_id=? ORDER BY line_number
            """, (item, row) -> new WageItem(item.getString(1), WageCategory.valueOf(item.getString(2)),
                item.getLong(3), item.getBoolean(4), item.getBoolean(5)), id);
        return new WageContract(id, rs.getLong("employee_id"), rs.getObject("effective_from", LocalDate.class), items);
    }
    private Contract findContract(long actor, String key) {
        List<Contract> rows = jdbc.query("""
            SELECT id, employee_id, contract_start, contract_end, work_location, weekly_work_minutes,
                agreed_monthly_wage, probation_start, probation_end, probation_terms
            FROM employment_contracts WHERE actor_id=? AND idempotency_key=?
            """, ContractService::mapContract, actor, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private static Contract mapContract(ResultSet rs, int row) throws SQLException {
        return new Contract(rs.getLong("id"), rs.getLong("employee_id"), rs.getObject("contract_start", LocalDate.class),
            rs.getObject("contract_end", LocalDate.class), rs.getString("work_location"), rs.getInt("weekly_work_minutes"),
            rs.getLong("agreed_monthly_wage"), rs.getObject("probation_start", LocalDate.class),
            rs.getObject("probation_end", LocalDate.class), rs.getString("probation_terms"));
    }
    private void lockEmployee(long employeeId) {
        if (jdbc.queryForList("SELECT id FROM employees WHERE id=? FOR UPDATE", Long.class, employeeId).isEmpty())
            throw new ContractNotFoundException();
    }
    private void requireEmployee(long employeeId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE id=?", Integer.class, employeeId) == 0)
            throw new ContractNotFoundException();
    }
    private long authorize(long employeeId, boolean write) {
        if (employeeId <= 0) throw new IllegalArgumentException();
        long actor = actors.requireActorId();
        if (actor <= 0) throw new RecordAccessForbiddenException();
        if (write) authorization.checkCanWriteRecord(actor, employeeId);
        else authorization.checkCanReadRecord(actor, employeeId);
        return actor;
    }
    private static void validate(long employeeId, ContractCommand c) {
        if (employeeId <= 0 || c == null || !key(c.idempotencyKey()) || c.contractStart() == null
                || c.contractEnd() == null || c.contractEnd().isBefore(c.contractStart())
                || c.workLocation() == null || c.workLocation().isBlank() || c.workLocation().length() > 200
                || c.weeklyWorkMinutes() < 1 || c.weeklyWorkMinutes() > 10080 || c.agreedMonthlyWage() < 0
                || (c.probationStart() == null) != (c.probationEnd() == null)
                || (c.probationStart() != null && (c.probationStart().isBefore(c.contractStart())
                    || c.probationEnd().isAfter(c.contractEnd()) || c.probationEnd().isBefore(c.probationStart())))
                || (c.probationTerms() != null && c.probationTerms().length() > 500)) throw new IllegalArgumentException();
    }
    private static void validate(long employeeId, WageCommand c) {
        if (employeeId <= 0 || c == null || !key(c.idempotencyKey()) || c.effectiveFrom() == null
                || c.items() == null || c.items().isEmpty() || c.items().size() > 100
                || c.items().stream().anyMatch(i -> i == null || i.itemName() == null || i.itemName().isBlank()
                    || i.itemName().length() > 100 || i.category() == null || i.amount() < 0))
            throw new IllegalArgumentException();
    }
    private static boolean key(String key) { return key != null && key.matches("[A-Za-z0-9_-]{1,100}"); }
    private static boolean same(Contract a, long employeeId, ContractCommand b) {
        return a.employeeId() == employeeId && a.contractStart().equals(b.contractStart()) && a.contractEnd().equals(b.contractEnd())
            && a.workLocation().equals(b.workLocation()) && a.weeklyWorkMinutes() == b.weeklyWorkMinutes()
            && a.agreedMonthlyWage() == b.agreedMonthlyWage() && Objects.equals(a.probationStart(), b.probationStart())
            && Objects.equals(a.probationEnd(), b.probationEnd()) && Objects.equals(a.probationTerms(), b.probationTerms());
    }
}
