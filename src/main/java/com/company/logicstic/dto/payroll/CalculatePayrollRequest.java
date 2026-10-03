package com.company.logicstic.dto.payroll;
import com.company.logicstic.service.payroll.domain.PayrollJurisdiction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
public record CalculatePayrollRequest(@NotBlank @Size(max=120) String idempotencyKey,@NotNull UUID payPeriodId,
 @NotBlank String currency,@NotNull LocalDate effectiveDate,@NotEmpty List<@NotNull UUID> settlementIds,
 Map<UUID,PayrollJurisdiction> jurisdictionOverrides,Map<UUID,Map<String,String>> taxInputs,List<@Valid Supplement> supplements) {
 public record Supplement(@NotNull UUID sourceId,@NotNull UUID driverId,@NotBlank String lineClass,
 @NotBlank @Size(max=300) String description,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal amount) {}
}
