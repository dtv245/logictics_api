package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverViolationView;
import com.company.logicstic.entity.DriverViolation;
import org.springframework.stereotype.Component;

@Component
public class DriverViolationMapper {
    public DriverViolationView toView(DriverViolation violation) {
        return new DriverViolationView(
                violation.getId(), violation.getEmployee().getId(), violation.getViolationDate(),
                violation.getViolationType(), violation.getDescription(), violation.getFineAmount(),
                violation.getFineCurrency(), violation.getTrip() == null ? null : violation.getTrip().getId(),
                violation.getDocument() == null ? null : violation.getDocument().getId(),
                violation.getResolutionStatus(), violation.isActive());
    }
}
