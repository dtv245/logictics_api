package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.EmploymentContractView;
import com.company.logicstic.entity.EmploymentContract;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.logicstic.service.DriverExpiryStatusCalculator;

@Component
public class EmploymentContractMapper {
    private final DriverExpiryStatusCalculator expiryCalculator;

    @Autowired
    public EmploymentContractMapper(DriverExpiryStatusCalculator expiryCalculator) {
        this.expiryCalculator = expiryCalculator;
    }

    public EmploymentContractMapper() {
        this(new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public EmploymentContractView toView(EmploymentContract contract) {
        return new EmploymentContractView(contract.getId(), contract.getEmployee().getId(),
                contract.getContractNumber(), contract.getContractType(), contract.getSignedDate(),
                contract.getEffectiveDate(), contract.getExpiryDate(),
                contract.getDocument() == null ? null : contract.getDocument().getId(),
                contract.getTerminationDate(), contract.getTerminationReason(), contract.isActive(),
                contract.getCreatedAt(), contract.getLastModifiedAt(), expiryCalculator.status(contract.getExpiryDate()));
    }
}
