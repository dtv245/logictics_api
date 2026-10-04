package com.company.logicstic.common.enums;

public enum ExpenseCategory {
    FUEL,
    TOLL,
    PARKING,
    SCALE,
    REPAIR,
    MAINTENANCE,
    TIRE,
    LUMPER,
    HOTEL,
    PERMIT,
    OTHER;

    public static ExpenseCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase();
        try {
            return ExpenseCategory.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
