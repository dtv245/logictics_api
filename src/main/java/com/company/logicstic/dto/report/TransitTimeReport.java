package com.company.logicstic.dto.report;

import java.math.BigDecimal;

public record TransitTimeReport(long eligibleLoadCount, BigDecimal averageTransitMinutes) {}
