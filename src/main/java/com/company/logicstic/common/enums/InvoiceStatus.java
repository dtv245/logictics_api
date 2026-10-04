package com.company.logicstic.common.enums;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    SENT,
    PARTIALLY_PAID,
    PAID,
    CANCELLED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED;

    public boolean countsAsRevenue() {
        return this == ISSUED || this == SENT || this == PARTIALLY_PAID || this == PAID;
    }

    public static InvoiceStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return InvoiceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }
}
