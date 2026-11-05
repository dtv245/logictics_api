package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverlicense.DriverCertificationView;
import com.company.logicstic.entity.DriverCertification;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.logicstic.service.DriverExpiryStatusCalculator;

@Component
public class DriverCertificationMapper {
    private final DriverExpiryStatusCalculator expiryCalculator;

    @Autowired
    public DriverCertificationMapper(DriverExpiryStatusCalculator expiryCalculator) {
        this.expiryCalculator = expiryCalculator;
    }

    public DriverCertificationMapper() {
        this(new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public DriverCertificationView toView(DriverCertification certification) {
        return new DriverCertificationView(certification.getId(), certification.getEmployee().getId(),
                certification.getType(), certification.getName(), certification.getIssuedDate(),
                certification.getExpiryDate(), certification.getIssuer(),
                certification.getDocument() == null ? null : certification.getDocument().getId(),
                "active".equalsIgnoreCase(certification.getStatus()), certification.getCreatedAt(),
                certification.getLastModifiedAt(), expiryCalculator.status(certification.getExpiryDate()));
    }
}
