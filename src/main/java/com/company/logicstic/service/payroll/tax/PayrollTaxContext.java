package com.company.logicstic.service.payroll.tax;
import com.company.logicstic.service.payroll.domain.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public record PayrollTaxContext(UUID driverId, PayrollJurisdiction jurisdiction, WorkerClassification workerClassification,
 LocalDate effectiveDate,String currency,BigDecimal grossAmount,BigDecimal otherDeductionAmount,
 BigDecimal reimbursementAmount,List<SourceLine> sourceLines,Map<String,String> additionalInputs) {
 public record SourceLine(UUID settlementId,UUID lineId,UUID originalLineId,String economicClass,
 BigDecimal signedAmount,boolean taxable,String sourceType,UUID sourceId) {}
}
