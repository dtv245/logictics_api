package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverlicense.DriverHealthCheckView;
import com.company.logicstic.entity.DriverHealthCheck;
import com.company.logicstic.service.DriverPiiCipher;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DriverHealthCheckMapper {
    private final DriverPiiCipher piiCipher;
    private final com.company.logicstic.service.DriverExpiryStatusCalculator expiryCalculator;

    @Autowired
    public DriverHealthCheckMapper(DriverPiiCipher piiCipher,
                                   com.company.logicstic.service.DriverExpiryStatusCalculator expiryCalculator) {
        this.piiCipher = piiCipher;
        this.expiryCalculator = expiryCalculator;
    }

    public DriverHealthCheckMapper(DriverPiiCipher piiCipher) {
        this(piiCipher, new com.company.logicstic.service.DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public DriverHealthCheckView toView(DriverHealthCheck check) {
        var employeeId = check.getEmployee().getId();
        String conclusion = piiCipher.decryptSensitiveValue(check.getConclusion(), employeeId, "health-check-conclusion");
        return new DriverHealthCheckView(check.getId(), employeeId, check.getCheckedDate(), check.getExpiryDate(),
                check.getFacility(), conclusion, check.getDocument() == null ? null : check.getDocument().getId(),
                "active".equalsIgnoreCase(check.getStatus()), check.getCreatedAt(), check.getLastModifiedAt(),
                expiryCalculator.status(check.getExpiryDate()));
    }
}
