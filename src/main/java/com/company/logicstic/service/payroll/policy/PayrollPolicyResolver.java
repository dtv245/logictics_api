package com.company.logicstic.service.payroll.policy;
import com.company.logicstic.service.payroll.domain.*;
import java.time.LocalDate;
public interface PayrollPolicyResolver {
    PayrollPolicy resolve(PayrollJurisdiction jurisdiction, WorkerClassification workerClassification, LocalDate effectiveDate);
}
