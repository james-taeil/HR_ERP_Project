ALTER TABLE platform_companies
    ADD COLUMN singleton_guard TINYINT GENERATED ALWAYS AS (1) STORED,
    ADD CONSTRAINT uk_platform_single_company UNIQUE (singleton_guard);

ALTER TABLE platform_department_versions
    ADD COLUMN capacity INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_platform_department_versions_capacity CHECK (capacity >= 0);
