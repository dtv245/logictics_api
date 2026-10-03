package com.company.logicstic.dto.payroll;
import java.util.UUID;
public record PayrollPaymentEventView(UUID eventId,String sourceKey,String status,String reason,PayrollPaymentView payment) {}
