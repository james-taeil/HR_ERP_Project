CREATE TABLE platform_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    disabled_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_platform_accounts_employee UNIQUE (employee_id),
    CONSTRAINT uk_platform_accounts_username UNIQUE (username),
    CONSTRAINT fk_platform_accounts_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_platform_accounts_status CHECK (account_status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_platform_accounts_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE TABLE platform_password_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_platform_password_history_account FOREIGN KEY (account_id) REFERENCES platform_accounts (id)
);
CREATE INDEX idx_platform_password_history_recent ON platform_password_history (account_id, created_at DESC, id DESC);

CREATE TABLE platform_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_accessed_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_platform_sessions_digest UNIQUE (token_digest),
    CONSTRAINT fk_platform_sessions_account FOREIGN KEY (account_id) REFERENCES platform_accounts (id)
);
CREATE INDEX idx_platform_sessions_account_active ON platform_sessions (account_id, revoked_at, expires_at);

CREATE TABLE platform_login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NULL,
    normalized_username VARCHAR(100) NOT NULL,
    success BOOLEAN NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    CONSTRAINT fk_platform_login_history_account FOREIGN KEY (account_id) REFERENCES platform_accounts (id)
);
CREATE INDEX idx_platform_login_history_account_time ON platform_login_history (account_id, occurred_at DESC);

CREATE TABLE platform_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_platform_roles_code UNIQUE (role_code)
);

CREATE TABLE platform_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(150) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sensitive_operation BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_platform_permissions_code UNIQUE (permission_code)
);

CREATE TABLE platform_role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_platform_role_permissions_role FOREIGN KEY (role_id) REFERENCES platform_roles (id),
    CONSTRAINT fk_platform_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES platform_permissions (id)
);

CREATE TABLE platform_account_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    valid_from TIMESTAMP(6) NOT NULL,
    valid_to TIMESTAMP(6) NULL,
    CONSTRAINT fk_platform_account_roles_account FOREIGN KEY (account_id) REFERENCES platform_accounts (id),
    CONSTRAINT fk_platform_account_roles_role FOREIGN KEY (role_id) REFERENCES platform_roles (id),
    CONSTRAINT chk_platform_account_roles_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX idx_platform_account_roles_effective ON platform_account_roles (account_id, valid_from, valid_to);

CREATE TABLE platform_organization_scopes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    organization_id BIGINT NULL,
    CONSTRAINT fk_platform_scopes_account FOREIGN KEY (account_id) REFERENCES platform_accounts (id),
    CONSTRAINT chk_platform_scopes_type CHECK (scope_type IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_TREE', 'WORKPLACE', 'COMPANY')),
    CONSTRAINT chk_platform_scopes_target CHECK (
        (scope_type = 'SELF' AND organization_id IS NULL)
        OR (scope_type <> 'SELF' AND organization_id IS NOT NULL)
    )
);

CREATE TABLE platform_authorization_change_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    before_value JSON NULL,
    after_value JSON NULL,
    changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_platform_auth_log_actor FOREIGN KEY (actor_account_id) REFERENCES platform_accounts (id),
    CONSTRAINT fk_platform_auth_log_target FOREIGN KEY (target_account_id) REFERENCES platform_accounts (id)
);

CREATE TABLE platform_companies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    active_from DATE NOT NULL,
    active_to DATE NULL,
    CONSTRAINT chk_platform_companies_period CHECK (active_to IS NULL OR active_to >= active_from)
);

CREATE TABLE platform_workplaces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    workplace_name VARCHAR(200) NOT NULL,
    business_registration_number VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    address VARCHAR(500) NOT NULL,
    industry VARCHAR(200) NOT NULL,
    opened_on DATE NOT NULL,
    active_to DATE NULL,
    CONSTRAINT uk_platform_workplaces_business_number UNIQUE (business_registration_number),
    CONSTRAINT fk_platform_workplaces_company FOREIGN KEY (company_id) REFERENCES platform_companies (id),
    CONSTRAINT chk_platform_workplaces_period CHECK (active_to IS NULL OR active_to >= opened_on)
);

CREATE TABLE platform_departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stable_code VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT uk_platform_departments_code UNIQUE (stable_code)
);

CREATE TABLE platform_department_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT NOT NULL,
    workplace_id BIGINT NOT NULL,
    parent_department_id BIGINT NULL,
    department_name VARCHAR(200) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_platform_department_version UNIQUE (department_id, version),
    CONSTRAINT fk_platform_department_versions_department FOREIGN KEY (department_id) REFERENCES platform_departments (id),
    CONSTRAINT fk_platform_department_versions_workplace FOREIGN KEY (workplace_id) REFERENCES platform_workplaces (id),
    CONSTRAINT fk_platform_department_versions_parent FOREIGN KEY (parent_department_id) REFERENCES platform_departments (id),
    CONSTRAINT chk_platform_department_versions_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_platform_department_versions_self CHECK (parent_department_id IS NULL OR parent_department_id <> department_id)
);
CREATE INDEX idx_platform_department_versions_asof ON platform_department_versions (department_id, effective_from, effective_to);

CREATE TABLE platform_code_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    group_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_platform_code_groups_code UNIQUE (group_code)
);

CREATE TABLE platform_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    code VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_platform_codes_group_code UNIQUE (group_id, code),
    CONSTRAINT fk_platform_codes_group FOREIGN KEY (group_id) REFERENCES platform_code_groups (id)
);

CREATE TABLE platform_annual_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_type VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applicable_year SMALLINT NOT NULL,
    scope_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_id BIGINT NOT NULL,
    setting_version BIGINT NOT NULL,
    setting_value JSON NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    verified_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_platform_annual_settings_version UNIQUE (setting_type, applicable_year, scope_type, scope_id, setting_version),
    CONSTRAINT fk_platform_annual_settings_verifier FOREIGN KEY (verified_by) REFERENCES platform_accounts (id),
    CONSTRAINT chk_platform_annual_settings_year CHECK (applicable_year BETWEEN 2000 AND 9999)
);
