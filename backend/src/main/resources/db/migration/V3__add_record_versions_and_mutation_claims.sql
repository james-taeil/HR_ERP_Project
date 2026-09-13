ALTER TABLE family_members ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE educations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE careers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE certifications ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE record_mutation_requests (
    actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    employee_id BIGINT NOT NULL,
    record_kind VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    result_id BIGINT NULL,
    result_version BIGINT NULL,
    PRIMARY KEY (actor_id, idempotency_key),
    CONSTRAINT fk_record_mutation_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT chk_record_mutation_result CHECK ((result_id IS NULL) = (result_version IS NULL))
);
