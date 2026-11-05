package com.company.logicstic.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** One aggregate query for driver profile counts and the current compliance dates. */
@Repository
public class DriverProfileSummaryRepository {
    private static final String SUMMARY_SQL = """
            SELECT
              (SELECT COUNT(*) FROM public.driver_profiles p WHERE p.employee_id = :driverId) AS personal_profiles,
              (SELECT COUNT(*) FROM public.driver_licenses l WHERE l.employee_id = :driverId) AS licenses,
              (SELECT COUNT(*) FROM public.driver_health_checks h WHERE h.employee_id = :driverId) AS health_checks,
              (SELECT COUNT(*) FROM public.driver_certifications c WHERE c.employee_id = :driverId) AS certifications,
              (SELECT COUNT(*) FROM public.driver_emergency_contacts c WHERE c.employee_id = :driverId) AS emergency_contacts,
              (SELECT COUNT(*) FROM public.employment_contracts c WHERE c.employee_id = :driverId) AS contracts,
              (SELECT COUNT(*) FROM public.driver_insurances i WHERE i.employee_id = :driverId) AS insurances,
              (SELECT COUNT(*) FROM public.driver_dependents d WHERE d.employee_id = :driverId) AS dependents,
              (SELECT COUNT(*) FROM public.driver_bank_accounts b WHERE b.employee_id = :driverId) AS bank_accounts,
              (SELECT COUNT(*) FROM public.driver_operational_statuses o WHERE o.employee_id = :driverId) AS operational_statuses,
              (SELECT COUNT(*) FROM public.driver_violations v WHERE v.employee_id = :driverId) AS violations,
              (SELECT COUNT(*) FROM public.accident_reports a WHERE a.driver_id = :driverId) AS accidents,
              (SELECT COUNT(*) FROM public.documents d WHERE d.employee_id = :driverId) AS documents,
              EXISTS (SELECT 1 FROM public.driver_licenses l WHERE l.employee_id = :driverId AND lower(l.status) = 'active') AS has_license,
              (SELECT MAX((l.expires_at AT TIME ZONE CAST(:timeZone AS text))::date)
                 FROM public.driver_licenses l WHERE l.employee_id = :driverId AND lower(l.status) = 'active') AS license_expiry_date,
              EXISTS (SELECT 1 FROM public.driver_health_checks h WHERE h.employee_id = :driverId AND lower(h.status) = 'active') AS has_health_check,
              (SELECT MAX(h.expiry_date) FROM public.driver_health_checks h
                 WHERE h.employee_id = :driverId AND lower(h.status) = 'active') AS health_check_expiry_date,
              EXISTS (SELECT 1 FROM public.employment_contracts c WHERE c.employee_id = :driverId
                 AND c.active = TRUE AND c.effective_date <= :today
                 AND (c.termination_date IS NULL OR c.termination_date >= :today)) AS has_contract,
              (SELECT MAX(c.expiry_date) FROM public.employment_contracts c WHERE c.employee_id = :driverId
                 AND c.active = TRUE AND c.effective_date <= :today
                 AND (c.termination_date IS NULL OR c.termination_date >= :today)) AS contract_expiry_date,
              EXISTS (SELECT 1 FROM public.driver_insurances i WHERE i.employee_id = :driverId
                 AND i.active = TRUE AND i.participation_start_date <= :today
                 AND (i.participation_end_date IS NULL OR i.participation_end_date >= :today)) AS has_health_insurance,
              (SELECT MAX(i.health_insurance_expiry_date) FROM public.driver_insurances i
                 WHERE i.employee_id = :driverId AND i.active = TRUE
                   AND i.participation_start_date <= :today
                   AND (i.participation_end_date IS NULL OR i.participation_end_date >= :today)) AS health_insurance_expiry_date
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DriverProfileSummaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DriverProfileSummaryRow> findByDriverId(UUID driverId, LocalDate today, String timeZone) {
        var parameters = new MapSqlParameterSource(Map.of("driverId", driverId, "today", Date.valueOf(today),
                "timeZone", timeZone));
        return jdbc.query(SUMMARY_SQL, parameters, rs -> {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new DriverProfileSummaryRow(
                    rs.getLong("personal_profiles"), rs.getLong("licenses"), rs.getLong("health_checks"),
                    rs.getLong("certifications"), rs.getLong("emergency_contacts"), rs.getLong("contracts"),
                    rs.getLong("insurances"), rs.getLong("dependents"), rs.getLong("bank_accounts"),
                    rs.getLong("operational_statuses"), rs.getLong("violations"), rs.getLong("accidents"),
                    rs.getLong("documents"), rs.getBoolean("has_license"), localDate(rs.getDate("license_expiry_date")),
                    rs.getBoolean("has_health_check"), localDate(rs.getDate("health_check_expiry_date")),
                    rs.getBoolean("has_contract"), localDate(rs.getDate("contract_expiry_date")),
                    rs.getBoolean("has_health_insurance"), localDate(rs.getDate("health_insurance_expiry_date"))));
        });
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    public record DriverProfileSummaryRow(
            long personalProfiles, long licenses, long healthChecks, long certifications,
            long emergencyContacts, long contracts, long insurances, long dependents,
            long bankAccounts, long operationalStatuses, long violations, long accidents, long documents,
            boolean hasLicense, LocalDate licenseExpiryDate,
            boolean hasHealthCheck, LocalDate healthCheckExpiryDate,
            boolean hasContract, LocalDate contractExpiryDate,
            boolean hasHealthInsurance, LocalDate healthInsuranceExpiryDate) {
    }
}
