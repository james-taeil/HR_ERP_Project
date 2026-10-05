CREATE TABLE employment_contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contract_start DATE NOT NULL, contract_end DATE NOT NULL,
    work_location VARCHAR(200) NOT NULL, weekly_work_minutes INT NOT NULL,
    agreed_monthly_wage BIGINT NOT NULL,
    probation_start DATE NULL, probation_end DATE NULL, probation_terms VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_employment_contracts_actor_key UNIQUE (actor_id, idempotency_key),
    CONSTRAINT fk_employment_contracts_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_employment_contract_dates CHECK (contract_end >= contract_start),
    CONSTRAINT chk_employment_contract_minutes CHECK (weekly_work_minutes BETWEEN 1 AND 10080),
    CONSTRAINT chk_employment_contract_wage CHECK (agreed_monthly_wage >= 0),
    CONSTRAINT chk_employment_contract_probation CHECK ((probation_start IS NULL AND probation_end IS NULL)
        OR (probation_start IS NOT NULL AND probation_end IS NOT NULL AND probation_start >= contract_start
            AND probation_end <= contract_end AND probation_end >= probation_start))
);
CREATE INDEX idx_employment_contracts_employee_period ON employment_contracts (employee_id, contract_start, contract_end, id);

CREATE TABLE wage_contracts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    effective_from DATE NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_wage_contracts_actor_key UNIQUE (actor_id, idempotency_key),
    CONSTRAINT uk_wage_contracts_employee_date UNIQUE (employee_id, effective_from),
    CONSTRAINT fk_wage_contracts_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE TABLE wage_contract_items (
    wage_contract_id BIGINT NOT NULL, line_number INT NOT NULL,
    item_name VARCHAR(100) NOT NULL, item_category VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL, taxable BOOLEAN NOT NULL, ordinary_wage BOOLEAN NOT NULL,
    PRIMARY KEY (wage_contract_id, line_number),
    CONSTRAINT fk_wage_contract_items_contract FOREIGN KEY (wage_contract_id) REFERENCES wage_contracts (id),
    CONSTRAINT chk_wage_contract_item_category CHECK (item_category IN ('BASE_PAY', 'FIXED_ALLOWANCE', 'VARIABLE_ALLOWANCE', 'NON_TAXABLE')),
    CONSTRAINT chk_wage_contract_item_amount CHECK (amount >= 0)
);
