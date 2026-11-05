package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverInsuranceSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverInsuranceView;
import com.company.logicstic.entity.DriverInsurance;
import com.company.logicstic.service.DriverPiiCipher;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DriverInsuranceMapper {
    private static final String SOCIAL_INSURANCE_CONTEXT = "driver-social-insurance-number";
    private static final String HEALTH_CARD_CONTEXT = "driver-health-insurance-card";

    private final DriverPiiCipher piiCipher;
    private final com.company.logicstic.service.DriverExpiryStatusCalculator expiryCalculator;

    @Autowired
    public DriverInsuranceMapper(DriverPiiCipher piiCipher,
                                 com.company.logicstic.service.DriverExpiryStatusCalculator expiryCalculator) {
        this.piiCipher = piiCipher;
        this.expiryCalculator = expiryCalculator;
    }

    public DriverInsuranceMapper(DriverPiiCipher piiCipher) {
        this(piiCipher, new com.company.logicstic.service.DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh"));
    }

    public DriverInsuranceView toView(DriverInsurance insurance) {
        var employeeId = insurance.getEmployee().getId();
        return new DriverInsuranceView(insurance.getId(), employeeId,
                mask(clear(insurance.getSocialInsuranceNumber(), employeeId, SOCIAL_INSURANCE_CONTEXT)),
                mask(clear(insurance.getHealthInsuranceCardNumber(), employeeId, HEALTH_CARD_CONTEXT)),
                insurance.getRegisteredClinic(), insurance.getParticipationStartDate(), insurance.getParticipationEndDate(),
                insurance.getSocialInsuranceStatus(), insurance.getHealthInsuranceStatus(),
                insurance.getUnemploymentInsuranceStatus(), insurance.getInsuredSalary(),
                insurance.getHealthInsuranceExpiryDate(), insurance.getNote(), insurance.isActive(),
                insurance.getCreatedAt(), insurance.getLastModifiedAt(),
                expiryCalculator.status(insurance.getHealthInsuranceExpiryDate()));
    }

    public DriverInsuranceSensitiveView toSensitiveView(DriverInsurance insurance) {
        var employeeId = insurance.getEmployee().getId();
        return new DriverInsuranceSensitiveView(insurance.getId(), employeeId,
                clear(insurance.getSocialInsuranceNumber(), employeeId, SOCIAL_INSURANCE_CONTEXT),
                clear(insurance.getHealthInsuranceCardNumber(), employeeId, HEALTH_CARD_CONTEXT),
                insurance.getRegisteredClinic(), insurance.getParticipationStartDate(), insurance.getParticipationEndDate(),
                insurance.getSocialInsuranceStatus(), insurance.getHealthInsuranceStatus(),
                insurance.getUnemploymentInsuranceStatus(), insurance.getInsuredSalary(),
                insurance.getHealthInsuranceExpiryDate(), insurance.getNote(), insurance.isActive(),
                insurance.getCreatedAt(), insurance.getLastModifiedAt(),
                expiryCalculator.status(insurance.getHealthInsuranceExpiryDate()));
    }

    private String clear(String encrypted, java.util.UUID employeeId, String fieldContext) {
        return piiCipher.decryptSensitiveValue(encrypted, employeeId, fieldContext);
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
