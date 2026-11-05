package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverDependentSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverDependentView;
import com.company.logicstic.entity.DriverDependent;
import com.company.logicstic.service.DriverPiiCipher;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DriverDependentMapper {
    private static final String NATIONAL_ID_CONTEXT = "driver-dependent-national-id";
    private static final String TAX_CODE_CONTEXT = "driver-dependent-tax-code";
    private final DriverPiiCipher piiCipher;

    public DriverDependentMapper(DriverPiiCipher piiCipher) {
        this.piiCipher = piiCipher;
    }

    public DriverDependentView toView(DriverDependent dependent) {
        UUID employeeId = dependent.getEmployee().getId();
        return new DriverDependentView(dependent.getId(), employeeId, dependent.getFullName(),
                dependent.getRelationship(), dependent.getDateOfBirth(),
                mask(clear(dependent.getNationalIdNumber(), employeeId, NATIONAL_ID_CONTEXT)),
                mask(clear(dependent.getTaxCode(), employeeId, TAX_CODE_CONTEXT)),
                dependent.getDeductionStartDate(), dependent.getDeductionEndDate(), dependent.isActive(),
                dependent.getCreatedAt(), dependent.getLastModifiedAt());
    }

    public DriverDependentSensitiveView toSensitiveView(DriverDependent dependent) {
        UUID employeeId = dependent.getEmployee().getId();
        return new DriverDependentSensitiveView(dependent.getId(), employeeId, dependent.getFullName(),
                dependent.getRelationship(), dependent.getDateOfBirth(),
                clear(dependent.getNationalIdNumber(), employeeId, NATIONAL_ID_CONTEXT),
                clear(dependent.getTaxCode(), employeeId, TAX_CODE_CONTEXT),
                dependent.getDeductionStartDate(), dependent.getDeductionEndDate(), dependent.isActive(),
                dependent.getCreatedAt(), dependent.getLastModifiedAt());
    }

    private String clear(String encrypted, UUID employeeId, String fieldContext) {
        return piiCipher.decryptSensitiveValue(encrypted, employeeId, fieldContext);
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
