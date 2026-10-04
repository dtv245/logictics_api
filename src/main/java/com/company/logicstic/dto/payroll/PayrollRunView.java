package com.company.logicstic.dto.payroll;
import com.company.logicstic.service.payroll.domain.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
public record PayrollRunView(UUID id,String runNumber,UUID payPeriodId,String currency,String status,String validationReason,
 LocalDate effectiveDate,OffsetDateTime calculatedAt,OffsetDateTime approvedAt,OffsetDateTime lockedAt,
 OffsetDateTime completedAt,UUID completedBy,String completionSource,List<Item> items) {
 public record Item(UUID id,UUID driverId,String status,String currency,BigDecimal grossAmount,BigDecimal incomeTaxAmount,
 BigDecimal insuranceAmount,BigDecimal otherDeductionAmount,BigDecimal reimbursementAmount,BigDecimal netAmount,
 String taxAvailability,String validationReason,PayrollJurisdiction jurisdiction,WorkerClassification workerClassification,
 UUID policyId,Integer policyVersion,LocalDate effectiveDate,List<UUID> settlementIds,String calculationSnapshotJson,
 OffsetDateTime noPaymentRequiredAt,UUID noPaymentRequiredBy,String noPaymentReasonCode,String noPaymentReason) {}
}
