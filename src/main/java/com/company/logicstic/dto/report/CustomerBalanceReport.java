package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerBalanceReport(
        UUID customerId,
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal openBalance,
        String currency,
        int invoiceCount
) {}
