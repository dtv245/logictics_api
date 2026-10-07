-- V39: Create persistent lark user mappings table
CREATE TABLE IF NOT EXISTS lark_user_mappings (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    open_id VARCHAR(128),
    union_id VARCHAR(128),
    lark_user_id VARCHAR(128),
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    CONSTRAINT pk_lark_user_mappings PRIMARY KEY (id),
    CONSTRAINT fk_lark_user_mappings_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT uq_lark_user_mappings_open_id UNIQUE (open_id),
    CONSTRAINT uq_lark_user_mappings_union_id UNIQUE (union_id)
);

CREATE INDEX IF NOT EXISTS ix_lark_user_mappings_employee_id ON lark_user_mappings(employee_id);
CREATE INDEX IF NOT EXISTS ix_lark_user_mappings_email ON lark_user_mappings(email);
