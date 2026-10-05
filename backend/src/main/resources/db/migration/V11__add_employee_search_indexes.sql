CREATE INDEX idx_employees_scope_cursor ON employees (department_id, id);
CREATE INDEX idx_employees_workplace_cursor ON employees (workplace_id, id);
CREATE INDEX idx_employees_status_type_hire_cursor
    ON employees (employment_status, employment_type, hire_date, id);
CREATE INDEX idx_employees_position_cursor ON employees (position_name, id);
