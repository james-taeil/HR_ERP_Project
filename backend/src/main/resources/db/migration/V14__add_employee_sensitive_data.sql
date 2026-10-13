CREATE TABLE employee_sensitive_data (
    employee_id BIGINT PRIMARY KEY,
    encrypted_resident_number VARBINARY(512) NOT NULL,
    resident_number_mask VARCHAR(20) NOT NULL,
    encrypted_bank_account VARBINARY(512) NOT NULL,
    bank_account_mask VARCHAR(40) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sensitive_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE TABLE sensitive_access_audits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    accessed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sensitive_audit_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE TRIGGER trg_sensitive_audit_no_update BEFORE UPDATE ON sensitive_access_audits
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sensitive access audits are immutable';
CREATE TRIGGER trg_sensitive_audit_no_delete BEFORE DELETE ON sensitive_access_audits
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sensitive access audits are immutable';
