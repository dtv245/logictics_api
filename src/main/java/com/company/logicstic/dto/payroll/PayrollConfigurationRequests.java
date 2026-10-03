package com.company.logicstic.dto.payroll;
import com.company.logicstic.service.payroll.domain.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
public final class PayrollConfigurationRequests {
 private PayrollConfigurationRequests() {}
 public record TenantDefault(@NotNull PayrollJurisdiction jurisdiction) {}
 public record Profile(PayrollJurisdiction jurisdiction,@NotNull WorkerClassification workerClassification,
 @NotNull LocalDate effectiveFrom,LocalDate effectiveTo,boolean active) {}
 public record Policy(@NotBlank @Size(max=80) String policyCode,@NotNull PayrollJurisdiction jurisdiction,
 @NotNull WorkerClassification workerClassification,@NotNull LocalDate effectiveFrom,LocalDate effectiveTo,
 boolean active,@NotBlank String currency,@NotBlank @Size(max=100) String calculatorKey,
 @NotBlank @Size(max=1000) String authoritativeSource,@NotBlank String configurationJson) {}
 public record ProfileView(UUID id,UUID employeeId,PayrollJurisdiction jurisdiction,WorkerClassification workerClassification,
 LocalDate effectiveFrom,LocalDate effectiveTo,int version,boolean active) {}
}
