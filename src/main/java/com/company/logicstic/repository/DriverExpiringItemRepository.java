package com.company.logicstic.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.sql.Types;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Paged, single-query view over all driver compliance dates that can expire. */
@Repository
public class DriverExpiringItemRepository {
    private static final String ITEMS_CTE = """
            WITH expiring_items AS (
                SELECT l.id AS item_id, l.employee_id AS driver_id,
                       concat_ws(' ', e.first_name, e.last_name) AS driver_name,
                       'LICENSE'::text AS item_type, 'GPLX'::text AS item_name,
                       (l.expires_at AT TIME ZONE CAST(:timeZone AS text))::date AS expiry_date
                FROM public.driver_licenses l JOIN public.employees e ON e.id = l.employee_id
                WHERE lower(l.status) = 'active'
                  AND l.expires_at >= (CAST(:fromDate AS date)::timestamp AT TIME ZONE CAST(:timeZone AS text))
                  AND l.expires_at < ((CAST(:toDate AS date) + 1)::timestamp AT TIME ZONE CAST(:timeZone AS text))
                UNION ALL
                SELECT l.id, l.employee_id, concat_ws(' ', e.first_name, e.last_name),
                       'LICENSE_MEDICAL_CERTIFICATE'::text, 'Chứng nhận sức khỏe trên GPLX'::text,
                       (l.medical_cert_expires_at AT TIME ZONE CAST(:timeZone AS text))::date
                FROM public.driver_licenses l JOIN public.employees e ON e.id = l.employee_id
                WHERE lower(l.status) = 'active' AND l.medical_cert_expires_at IS NOT NULL
                  AND l.medical_cert_expires_at >= (CAST(:fromDate AS date)::timestamp AT TIME ZONE CAST(:timeZone AS text))
                  AND l.medical_cert_expires_at < ((CAST(:toDate AS date) + 1)::timestamp AT TIME ZONE CAST(:timeZone AS text))
                UNION ALL
                SELECT h.id, h.employee_id, concat_ws(' ', e.first_name, e.last_name),
                       'HEALTH_CHECK'::text, 'Giấy khám sức khỏe'::text, h.expiry_date
                FROM public.driver_health_checks h JOIN public.employees e ON e.id = h.employee_id
                WHERE lower(h.status) = 'active' AND h.expiry_date BETWEEN :fromDate AND :toDate
                UNION ALL
                SELECT c.id, c.employee_id, concat_ws(' ', e.first_name, e.last_name),
                       'CERTIFICATION'::text, 'Chứng chỉ nghiệp vụ'::text, c.expiry_date
                FROM public.driver_certifications c JOIN public.employees e ON e.id = c.employee_id
                WHERE lower(c.status) = 'active' AND c.expiry_date BETWEEN :fromDate AND :toDate
                UNION ALL
                SELECT c.id, c.employee_id, concat_ws(' ', e.first_name, e.last_name),
                       'CONTRACT'::text, 'Hợp đồng lao động'::text, c.expiry_date
                FROM public.employment_contracts c JOIN public.employees e ON e.id = c.employee_id
                WHERE c.active = TRUE AND c.expiry_date BETWEEN :fromDate AND :toDate
                  AND c.effective_date <= :toDate
                  AND (c.termination_date IS NULL OR c.termination_date >= :fromDate)
                UNION ALL
                SELECT i.id, i.employee_id, concat_ws(' ', e.first_name, e.last_name),
                       'HEALTH_INSURANCE'::text, 'Thẻ bảo hiểm y tế'::text, i.health_insurance_expiry_date
                FROM public.driver_insurances i JOIN public.employees e ON e.id = i.employee_id
                WHERE i.active = TRUE AND i.health_insurance_expiry_date BETWEEN :fromDate AND :toDate
                  AND i.participation_start_date <= :toDate
                  AND (i.participation_end_date IS NULL OR i.participation_end_date >= :fromDate)
                UNION ALL
                SELECT d.id, d.employee_id, concat_ws(' ', e.first_name, e.last_name),
                       'DOCUMENT'::text, 'Tài liệu tài xế'::text, d.expiry_date
                FROM public.documents d JOIN public.employees e ON e.id = d.employee_id
                WHERE d.expiry_date BETWEEN :fromDate AND :toDate
                  AND lower(d.status) = 'active'
                  AND NOT EXISTS (
                      SELECT 1 FROM public.driver_licenses l
                      WHERE l.document_id = d.id OR l.front_image_document_id = d.id OR l.back_image_document_id = d.id
                  )
                  AND NOT EXISTS (SELECT 1 FROM public.driver_health_checks h WHERE h.document_id = d.id)
                  AND NOT EXISTS (SELECT 1 FROM public.driver_certifications c WHERE c.document_id = d.id)
                  AND NOT EXISTS (SELECT 1 FROM public.employment_contracts c WHERE c.document_id = d.id)
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DriverExpiringItemRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DriverExpiringItemRow> findPage(LocalDate fromDate, LocalDate toDate, String type,
                                                 UUID driverId, String timeZone, Pageable pageable) {
        MapSqlParameterSource parameters = parameters(fromDate, toDate, type, driverId, timeZone)
                .addValue("offset", pageable.getOffset()).addValue("limit", pageable.getPageSize());
        return jdbc.query(ITEMS_CTE + " SELECT * FROM expiring_items" + filter(type, driverId)
                        + " ORDER BY expiry_date, driver_name, item_type, item_id LIMIT :limit OFFSET :offset",
                parameters, (rs, rowNum) -> new DriverExpiringItemRow(
                        rs.getObject("item_id", UUID.class), rs.getObject("driver_id", UUID.class),
                        rs.getString("driver_name"), rs.getString("item_type"), rs.getString("item_name"),
                        rs.getDate("expiry_date").toLocalDate()));
    }

    public long count(LocalDate fromDate, LocalDate toDate, String type, UUID driverId, String timeZone) {
        Long total = jdbc.queryForObject(ITEMS_CTE + " SELECT COUNT(*) FROM expiring_items" + filter(type, driverId),
                parameters(fromDate, toDate, type, driverId, timeZone), Long.class);
        return total == null ? 0 : total;
    }

    private static MapSqlParameterSource parameters(LocalDate fromDate, LocalDate toDate, String type,
                                                    UUID driverId, String timeZone) {
        var parameters = new MapSqlParameterSource()
                .addValue("fromDate", Date.valueOf(fromDate), Types.DATE)
                .addValue("toDate", Date.valueOf(toDate), Types.DATE)
                .addValue("timeZone", timeZone, Types.VARCHAR);
        if (type != null) parameters.addValue("type", type, Types.VARCHAR);
        if (driverId != null) parameters.addValue("driverId", driverId, Types.OTHER);
        return parameters;
    }

    private static String filter(String type, UUID driverId) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (driverId != null) where.append(" AND driver_id = :driverId");
        if (type != null) where.append(" AND item_type = :type");
        return where.toString();
    }

    public record DriverExpiringItemRow(UUID itemId, UUID driverId, String driverName, String type,
                                        String itemName, LocalDate expiryDate) {
    }
}
