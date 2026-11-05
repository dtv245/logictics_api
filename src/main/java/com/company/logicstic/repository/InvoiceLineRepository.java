package com.company.logicstic.repository;

import com.company.logicstic.dto.invoice.InvoiceLineView;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only projection of invoice lines.
 *
 * <p>Uses JDBC rather than the entity because the rating provenance columns added by V26
 * are intentionally not mapped on {@code InvoiceLineItem}; billing writes those rows
 * through its own command path.</p>
 */
@Repository
@RequiredArgsConstructor
public class InvoiceLineRepository {

    private final JdbcTemplate jdbc;

    public List<InvoiceLineView> findByInvoiceId(UUID invoiceId) {
        return jdbc.query("""
                SELECT id, invoice_id, description, "type", quantity, "order", notes,
                       tax_rate_percent, tax_amount, tax_code, amount_amount, amount_currency,
                       rating_source_id, credited_line_id, credited_quantity
                FROM invoice_line_items
                WHERE invoice_id = ?
                ORDER BY "order", id
                """, (rs, rowNum) -> new InvoiceLineView(
                rs.getObject("id", UUID.class),
                rs.getObject("invoice_id", UUID.class),
                rs.getString("description"),
                rs.getString("type"),
                rs.getObject("quantity", Integer.class),
                rs.getObject("order", Integer.class),
                rs.getString("notes"),
                rs.getBigDecimal("tax_rate_percent"),
                rs.getBigDecimal("tax_amount"),
                rs.getString("tax_code"),
                rs.getBigDecimal("amount_amount"),
                rs.getString("amount_currency"),
                rs.getObject("rating_source_id", UUID.class),
                rs.getObject("credited_line_id", UUID.class),
                rs.getBigDecimal("credited_quantity")
        ), invoiceId);
    }
}
