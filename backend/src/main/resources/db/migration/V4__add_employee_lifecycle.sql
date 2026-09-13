ALTER TABLE employees
    ADD COLUMN employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER foreign_worker,
    ADD CONSTRAINT chk_employees_status CHECK (
        employment_status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'TERMINATED')
    );

CREATE TABLE appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_date DATE NOT NULL,
    appointment_type VARCHAR(30) NOT NULL,
    appointment_status VARCHAR(20) NOT NULL,
    before_workplace_id BIGINT NOT NULL,
    after_workplace_id BIGINT NOT NULL,
    before_department_id BIGINT NOT NULL,
    after_department_id BIGINT NOT NULL,
    before_position_name VARCHAR(100) NOT NULL,
    after_position_name VARCHAR(100) NOT NULL,
    before_employment_status VARCHAR(20) NOT NULL,
    after_employment_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    evidence_file_id BIGINT NULL,
    applied_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_appointments_actor_key UNIQUE (actor_id, idempotency_key),
    CONSTRAINT fk_appointments_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_appointments_type CHECK (appointment_type IN (
        'DEPARTMENT_CHANGE', 'WORKPLACE_CHANGE', 'POSITION_CHANGE', 'STATUS_CHANGE'
    )),
    CONSTRAINT chk_appointments_state CHECK (appointment_status IN ('SCHEDULED', 'APPLIED')),
    CONSTRAINT chk_appointments_before_status CHECK (before_employment_status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT chk_appointments_after_status CHECK (after_employment_status IN ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT chk_appointments_applied_at CHECK (
        (appointment_status = 'SCHEDULED' AND applied_at IS NULL)
        OR (appointment_status = 'APPLIED' AND applied_at IS NOT NULL)
    )
);

CREATE INDEX idx_appointments_due ON appointments (appointment_status, effective_date, id);
CREATE INDEX idx_appointments_employee_date ON appointments (employee_id, effective_date, id);

CREATE TABLE terminations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    appointment_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    termination_date DATE NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    separation_certificate_required BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_terminations_employee UNIQUE (employee_id),
    CONSTRAINT uk_terminations_actor_key UNIQUE (actor_id, idempotency_key),
    CONSTRAINT uk_terminations_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_terminations_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_terminations_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id)
);

CREATE TABLE offboarding_checklist_items (
    termination_id BIGINT NOT NULL,
    item_type VARCHAR(30) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (termination_id, item_type),
    CONSTRAINT fk_offboarding_items_termination FOREIGN KEY (termination_id) REFERENCES terminations (id),
    CONSTRAINT chk_offboarding_item_type CHECK (item_type IN (
        'LEAVE_SETTLEMENT', 'INSURANCE_LOSS', 'RETIREMENT_INCOME', 'ASSET_RETURN'
    ))
);
