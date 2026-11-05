package com.company.logicstic.service;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Idempotently encrypts legacy cleartext and rewraps old key versions after each schema migration. */
@Service
public class DriverLicensePiiBackfill {
    private static final int BATCH_SIZE = 200;
    private final DriverPiiCipher cipher;

    public DriverLicensePiiBackfill(DriverPiiCipher cipher) {
        this.cipher = cipher;
    }

    public int backfill(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM public.driver_licenses", Integer.class);
        if (rows == null || rows == 0) return 0;

        String activePrefix = cipher.activePrefix();
        int updated = 0;
        while (true) {
            List<LegacyLicense> batch = jdbc.query("""
                    SELECT id, employee_id, issuing_country, license_number
                    FROM public.driver_licenses
                    WHERE license_number_hmac IS NULL OR license_number NOT LIKE ?
                    ORDER BY id
                    LIMIT ?
                    """, (rs, rowNum) -> new LegacyLicense(
                    rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class),
                    rs.getString("issuing_country"), rs.getString("license_number")),
                    activePrefix + "%", BATCH_SIZE);
            if (batch.isEmpty()) return updated;
            for (LegacyLicense row : batch) {
                String clear = cipher.decryptLicenseNumber(row.licenseNumber(), row.employeeId(), row.country());
                String encrypted = cipher.encryptLicenseNumber(clear, row.employeeId(), row.country());
                String hmac = cipher.licenseNumberHmac(clear, row.employeeId(), row.country());
                jdbc.update("""
                        UPDATE public.driver_licenses
                        SET license_number = ?, license_number_hmac = ?
                        WHERE id = ?
                        """, encrypted, hmac, row.id());
                updated++;
            }
        }
    }

    private record LegacyLicense(UUID id, UUID employeeId, String country, String licenseNumber) {}
}
