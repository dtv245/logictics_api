package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverlicense.DriverLicenseSensitiveView;
import com.company.logicstic.dto.driverlicense.DriverLicenseView;
import com.company.logicstic.entity.DriverLicense;
import com.company.logicstic.service.DriverPiiCipher;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DriverLicenseMapper {
    private final DriverPiiCipher piiCipher;
    private final com.company.logicstic.service.DriverExpiryStatusCalculator expiryCalculator;

    @Autowired
    public DriverLicenseMapper(DriverPiiCipher piiCipher,
                               com.company.logicstic.service.DriverExpiryStatusCalculator expiryCalculator) {
        this.piiCipher = piiCipher;
        this.expiryCalculator = expiryCalculator;
    }

    public DriverLicenseMapper(DriverPiiCipher piiCipher) {
        this(piiCipher, new com.company.logicstic.service.DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public DriverLicenseView toView(DriverLicense license) {
        String clearNumber = clearNumber(license);
        return new DriverLicenseView(
                license.getId(), license.getEmployee().getId(), mask(clearNumber), license.getLicenseClass(),
                license.getEndorsements(), license.getIssuingCountry(), license.getIssuingRegion(),
                license.getIssuedDate(), license.getExpiresAt(), license.getMedicalCertExpiresAt(),
                license.getIssuedPlace(), license.getRemainingPoints() == null ? null : license.getRemainingPoints().intValue(),
                id(license.getDocument()), id(license.getFrontImageDocument()), id(license.getBackImageDocument()),
                "active".equalsIgnoreCase(license.getStatus()), license.getCreatedAt(), license.getLastModifiedAt(),
                expiryCalculator.status(license.getExpiresAt()), expiryCalculator.status(license.getMedicalCertExpiresAt()));
    }

    public DriverLicenseSensitiveView toSensitiveView(DriverLicense license) {
        return new DriverLicenseSensitiveView(license.getId(), license.getEmployee().getId(), clearNumber(license));
    }

    private String clearNumber(DriverLicense license) {
        return piiCipher.decryptLicenseNumber(license.getLicenseNumber(),
                license.getEmployee().getId(), license.getIssuingCountry());
    }

    private static java.util.UUID id(com.company.logicstic.entity.Document document) {
        return document == null ? null : document.getId();
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "****";
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
