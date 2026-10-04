package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record LoadRevenueSummary(
        UUID loadId,
        BigDecimal subtotalRevenue,
        BigDecimal taxAmount,
        BigDecimal totalInvoiceAmount,
        BigDecimal paidAmount,
        BigDecimal openBalance,
        String currency,
        String distanceBasis,
        BigDecimal revenuePerMile
) {}
