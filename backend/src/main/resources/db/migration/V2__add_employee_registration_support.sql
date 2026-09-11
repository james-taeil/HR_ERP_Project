CREATE TABLE employee_registration_requests (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    employee_id BIGINT NULL,
    employee_number CHAR(8) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_registration_requests_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_registration_requests_result CHECK (
        (employee_id IS NULL AND employee_number IS NULL AND completed_at IS NULL)
        OR (employee_id IS NOT NULL AND employee_number IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE TABLE employee_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_employee_events_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_employee_events_employee ON employee_events (employee_id);
