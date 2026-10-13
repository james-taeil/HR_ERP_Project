package com.jamestaeil.hrerp.hr.employee.application;

import com.jamestaeil.hrerp.hr.record.application.RecordNotFoundException;
import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;
import com.jamestaeil.hrerp.platform.port.RecordAudit;
import com.jamestaeil.hrerp.platform.port.SensitiveValueCipher;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeSensitiveDataService {
    public record Masked(String residentRegistrationNumber, String bankAccountNumber) {}
    public record Plain(String residentRegistrationNumber, String bankAccountNumber) {}
    private record Stored(byte[] resident, String residentMask, byte[] bank, String bankMask) {}

    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuthorizationChecker authorization;
    private final SensitiveValueCipher cipher;
    private final RecordAudit audit;

    public EmployeeSensitiveDataService(JdbcTemplate jdbc, CurrentActorProvider actors,
            AuthorizationChecker authorization, SensitiveValueCipher cipher, RecordAudit audit) {
        this.jdbc = jdbc; this.actors = actors; this.authorization = authorization; this.cipher = cipher; this.audit = audit;
    }

    @Transactional
    public Masked save(long employeeId, String residentNumber, String bankAccount) {
        validateEmployee(employeeId); validate(residentNumber, bankAccount);
        long actor = actor(); authorization.checkCanWriteRecord(actor, employeeId); requireEmployee(employeeId);
        Masked before = existingMasked(employeeId);
        String residentDigits = digits(residentNumber), bankDigits = digits(bankAccount);
        String residentMask = residentDigits.substring(0, 6) + "-*******";
        String bankMask = "*".repeat(Math.max(4, bankDigits.length() - 4)) + bankDigits.substring(bankDigits.length() - 4);
        jdbc.update("""
            INSERT INTO employee_sensitive_data(employee_id,encrypted_resident_number,resident_number_mask,
                encrypted_bank_account,bank_account_mask) VALUES (?,?,?,?,?)
            ON DUPLICATE KEY UPDATE encrypted_resident_number=VALUES(encrypted_resident_number),
                resident_number_mask=VALUES(resident_number_mask), encrypted_bank_account=VALUES(encrypted_bank_account),
                bank_account_mask=VALUES(bank_account_mask)
            """, employeeId, cipher.encrypt(residentDigits), residentMask, cipher.encrypt(bankDigits), bankMask);
        Masked after = new Masked(residentMask, bankMask);
        audit.changed(actor, employeeId, "SENSITIVE_DATA", employeeId, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public Masked masked(long employeeId) {
        validateEmployee(employeeId); long actor = actor(); authorization.checkCanReadRecord(actor, employeeId);
        Stored stored = stored(employeeId);
        return new Masked(stored.residentMask(), stored.bankMask());
    }

    @Transactional
    public Plain reveal(long employeeId, String reason) {
        validateEmployee(employeeId);
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) throw new IllegalArgumentException();
        long actor = actor(); authorization.checkCanReadSensitiveRecord(actor, employeeId);
        Stored stored = stored(employeeId);
        jdbc.update("INSERT INTO sensitive_access_audits(actor_id,employee_id,reason) VALUES (?,?,?)",
            actor, employeeId, reason.trim());
        return new Plain(cipher.decrypt(stored.resident()), cipher.decrypt(stored.bank()));
    }

    private Stored stored(long employeeId) {
        List<Stored> rows = jdbc.query("""
            SELECT encrypted_resident_number,resident_number_mask,encrypted_bank_account,bank_account_mask
            FROM employee_sensitive_data WHERE employee_id=?
            """, (rs, row) -> new Stored(rs.getBytes(1), rs.getString(2), rs.getBytes(3), rs.getString(4)), employeeId);
        if (rows.isEmpty()) throw new RecordNotFoundException();
        return rows.getFirst();
    }
    private Masked existingMasked(long employeeId) {
        List<Masked> rows = jdbc.query("SELECT resident_number_mask,bank_account_mask FROM employee_sensitive_data WHERE employee_id=?",
            (rs, row) -> new Masked(rs.getString(1), rs.getString(2)), employeeId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private void requireEmployee(long employeeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE id=?", Integer.class, employeeId);
        if (count == null || count == 0) throw new RecordNotFoundException();
    }
    private long actor() { long id = actors.requireActorId(); if (id <= 0) throw new RecordAccessForbiddenException(); return id; }
    private static void validateEmployee(long id) { if (id <= 0) throw new IllegalArgumentException(); }
    private static void validate(String resident, String bank) {
        if (resident == null || !resident.matches("[0-9-]{13,14}") || !digits(resident).matches("\\d{13}")
                || bank == null || !bank.matches("[0-9-]{6,40}") || !digits(bank).matches("\\d{6,30}"))
            throw new IllegalArgumentException();
    }
    private static String digits(String value) { return value == null ? "" : value.replaceAll("[^0-9]", ""); }
}
