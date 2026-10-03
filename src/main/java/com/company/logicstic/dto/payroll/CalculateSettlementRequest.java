package com.company.logicstic.dto.payroll;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CalculateSettlementRequest(@NotNull UUID driverId, @NotNull UUID payPeriodId) {}
