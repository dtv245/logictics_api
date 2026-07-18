CREATE TABLE IF NOT EXISTS tenant_registry (
    tenant_id VARCHAR(50) PRIMARY KEY,
    db_url VARCHAR(255) NOT NULL,
    db_username VARCHAR(100) NOT NULL,
    db_password VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
