package com.company.logicstic.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyRoundingPolicy {

    public static final int DEFAULT_MONEY_SCALE = 2;
    public static final int DETAILED_RATE_SCALE = 6;
    public static final int DISTANCE_SCALE = 3;

    private MoneyRoundingPolicy() {
    }

    public static int getScaleForCurrency(String currency) {
        if (currency == null) {
            return DEFAULT_MONEY_SCALE;
        }
        String normalized = currency.trim().toUpperCase();
        return switch (normalized) {
            case "VND", "JPY", "KRW" -> 0;
            case "BHD", "KWD", "OMR" -> 3;
            default -> DEFAULT_MONEY_SCALE;
        };
    }

    public static BigDecimal roundMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        int scale = getScaleForCurrency(currency);
        return amount.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal amount, String currency) { return roundMoney(amount, currency); }

    public static BigDecimal roundRate(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        return rate.setScale(DETAILED_RATE_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal roundDistance(BigDecimal distance) {
        if (distance == null) {
            return null;
        }
        return distance.setScale(DISTANCE_SCALE, RoundingMode.HALF_UP);
    }
}
