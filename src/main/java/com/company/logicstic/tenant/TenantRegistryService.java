package com.company.logicstic.tenant;

import java.util.List;
import java.util.Optional;

import com.company.logicstic.shared.config.TenancyProperties;
import com.company.logicstic.tenant.dto.TenantRegistryRecord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantRegistryService {

    private static final RowMapper<TenantRegistryRecord> TENANT_ROW_MAPPER = (rs, rowNum) -> new TenantRegistryRecord(
            rs.getString("tenant_id"),
            rs.getString("db_url"),
            rs.getString("db_username"),
            rs.getString("db_password"),
            rs.getString("status"));

    private final JdbcTemplate tenantRegistryJdbcTemplate;
    private final TenancyProperties properties;
    private final TenantCredentialCipher credentialCipher;

    @PostConstruct
    void initializeRegistrySchema() {
        if (!properties.getRegistry().isAutoInitialize()) {
            return;
        }
        tenantRegistryJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tenant_registry (
                    tenant_id VARCHAR(50) PRIMARY KEY,
                    db_url VARCHAR(255) NOT NULL,
                    db_username VARCHAR(100) NOT NULL,
                    db_password VARCHAR(255) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                )
                """);
    }

    public Optional<TenantRegistryRecord> findActiveTenant(String tenantId) {
        List<TenantRegistryRecord> tenants = tenantRegistryJdbcTemplate.query("""
                SELECT tenant_id, db_url, db_username, db_password, status
                FROM tenant_registry
                WHERE tenant_id = ? AND status = 'ACTIVE'
                """, TENANT_ROW_MAPPER, tenantId);
        return tenants.stream().findFirst();
    }

    public List<TenantRegistryRecord> findAllActiveTenants() {
        return tenantRegistryJdbcTemplate.query("""
                SELECT tenant_id, db_url, db_username, db_password, status
                FROM tenant_registry
                WHERE status = 'ACTIVE'
                ORDER BY tenant_id
                """, TENANT_ROW_MAPPER);
    }

    public void registerActiveTenant(String tenantId, String dbUrl, String dbUsername, String plaintextDbPassword) {
        tenantRegistryJdbcTemplate.update("""
                INSERT INTO tenant_registry (tenant_id, db_url, db_username, db_password, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, tenantId, dbUrl, dbUsername, credentialCipher.encrypt(plaintextDbPassword));
    }
}
