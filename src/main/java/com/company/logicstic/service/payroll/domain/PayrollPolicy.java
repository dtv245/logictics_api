package com.company.logicstic.service.payroll.domain;
import java.time.LocalDate;
import java.util.UUID;
public record PayrollPolicy(UUID id,String policyCode,PayrollJurisdiction jurisdiction,
 WorkerClassification workerClassification,LocalDate effectiveFrom,LocalDate effectiveTo,int version,
 boolean active,String currency,String calculatorKey,String authoritativeSource,String configurationJson) {}
