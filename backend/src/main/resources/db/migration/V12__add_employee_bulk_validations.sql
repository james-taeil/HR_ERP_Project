CREATE TABLE employee_bulk_validations (
    validation_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    file_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    total_rows INT NOT NULL,
    error_rows INT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    confirmed_at TIMESTAMP(6) NULL,
    result_json JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_employee_bulk_counts CHECK (total_rows BETWEEN 1 AND 1000 AND error_rows BETWEEN 0 AND total_rows),
    CONSTRAINT chk_employee_bulk_result CHECK (
        (confirmed_at IS NULL AND result_json IS NULL) OR (confirmed_at IS NOT NULL AND result_json IS NOT NULL)
    )
);
CREATE INDEX idx_employee_bulk_actor_expiry ON employee_bulk_validations (actor_id, expires_at);
