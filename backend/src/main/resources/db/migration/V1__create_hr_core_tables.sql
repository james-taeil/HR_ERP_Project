CREATE TABLE employee_number_sequences (
    sequence_year SMALLINT PRIMARY KEY,
    last_value SMALLINT NOT NULL,
    CONSTRAINT chk_employee_number_sequence_year CHECK (sequence_year BETWEEN 0 AND 99),
    CONSTRAINT chk_employee_number_sequence_value CHECK (last_value BETWEEN 0 AND 9999)
);

CREATE TABLE employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_number CHAR(8) NOT NULL,
    employee_name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    phone VARCHAR(30) NOT NULL,
    hire_date DATE NOT NULL,
    employment_type VARCHAR(20) NOT NULL,
    workplace_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    position_name VARCHAR(100) NOT NULL,
    probation_end_date DATE NULL,
    foreign_worker BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_employees_employee_number UNIQUE (employee_number),
    CONSTRAINT chk_employees_number CHECK (employee_number REGEXP '^[0-9]{8}$'),
    CONSTRAINT chk_employees_employment_type CHECK (
        employment_type IN ('REGULAR', 'CONTRACT', 'DAILY', 'PART_TIME', 'DISPATCHED', 'FREELANCER')
    ),
    CONSTRAINT chk_employees_probation CHECK (probation_end_date IS NULL OR probation_end_date >= hire_date)
);

CREATE TABLE onboarding_checklists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_onboarding_checklists_employee UNIQUE (employee_id),
    CONSTRAINT fk_onboarding_checklists_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE TABLE family_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    relationship_name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    cohabiting BOOLEAN NOT NULL,
    dependent BOOLEAN NOT NULL,
    disabled BOOLEAN NOT NULL,
    deduction_eligible BOOLEAN NOT NULL,
    CONSTRAINT fk_family_members_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE TABLE educations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    institution VARCHAR(200) NOT NULL,
    major VARCHAR(200) NULL,
    evidence_file_id BIGINT NULL,
    CONSTRAINT fk_educations_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_educations_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE careers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    institution VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    evidence_file_id BIGINT NULL,
    CONSTRAINT fk_careers_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_careers_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE certifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    certification_name VARCHAR(200) NOT NULL,
    issuer VARCHAR(200) NOT NULL,
    acquired_date DATE NOT NULL,
    expires_on DATE NULL,
    CONSTRAINT fk_certifications_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_certifications_period CHECK (expires_on IS NULL OR expires_on >= acquired_date)
);

CREATE TABLE foreign_worker_profiles (
    employee_id BIGINT PRIMARY KEY,
    nationality VARCHAR(100) NOT NULL,
    visa_type VARCHAR(100) NOT NULL,
    stay_from DATE NOT NULL,
    stay_until DATE NOT NULL,
    encrypted_registration_number VARBINARY(512) NOT NULL,
    registration_number_mask VARCHAR(30) NOT NULL,
    CONSTRAINT fk_foreign_worker_profiles_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_foreign_worker_profiles_period CHECK (stay_until >= stay_from)
);

CREATE TABLE reminder_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reminder_type VARCHAR(30) NOT NULL,
    employee_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    delivered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_reminder_deliveries_dedup UNIQUE (reminder_type, employee_id, target_id, due_date),
    CONSTRAINT fk_reminder_deliveries_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_family_members_employee ON family_members (employee_id);
CREATE INDEX idx_educations_employee ON educations (employee_id);
CREATE INDEX idx_careers_employee ON careers (employee_id);
CREATE INDEX idx_certifications_employee ON certifications (employee_id);
CREATE INDEX idx_certifications_expires ON certifications (expires_on);
CREATE INDEX idx_employees_probation_end ON employees (probation_end_date);
CREATE INDEX idx_foreign_workers_stay_until ON foreign_worker_profiles (stay_until);
