package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverProfileSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverProfileView;
import com.company.logicstic.entity.DriverProfile;
import com.company.logicstic.service.DriverPiiCipher;
import org.springframework.stereotype.Component;

@Component
public class DriverProfileMapper {
    private final DriverPiiCipher piiCipher;

    public DriverProfileMapper(DriverPiiCipher piiCipher) {
        this.piiCipher = piiCipher;
    }

    public DriverProfileView toView(DriverProfile profile) {
        String nationalId = clear(profile.getNationalIdNumber(), profile.getEmployee().getId(), "driver-national-id");
        String taxCode = clear(profile.getPersonalTaxCode(), profile.getEmployee().getId(), "driver-personal-tax-code");
        return new DriverProfileView(profile.getId(), profile.getEmployee().getId(), profile.getDateOfBirth(),
                profile.getGender(), profile.getNationality(), profile.getMaritalStatus(), profile.getEducationLevel(),
                mask(nationalId), profile.getNationalIdIssuedDate(), profile.getNationalIdIssuedPlace(), mask(taxCode),
                profile.getAvatarUrl(), profile.getPermanentAddress(), profile.getTemporaryAddress(), profile.getHometown(),
                profile.getCreatedAt(), profile.getLastModifiedAt());
    }

    public DriverProfileSensitiveView toSensitiveView(DriverProfile profile) {
        var employeeId = profile.getEmployee().getId();
        return new DriverProfileSensitiveView(profile.getId(), employeeId, profile.getDateOfBirth(), profile.getGender(),
                profile.getNationality(), profile.getMaritalStatus(), profile.getEducationLevel(),
                clear(profile.getNationalIdNumber(), employeeId, "driver-national-id"),
                profile.getNationalIdIssuedDate(), profile.getNationalIdIssuedPlace(),
                clear(profile.getPersonalTaxCode(), employeeId, "driver-personal-tax-code"), profile.getAvatarUrl(),
                profile.getPermanentAddress(), profile.getTemporaryAddress(), profile.getHometown(),
                profile.getCreatedAt(), profile.getLastModifiedAt());
    }

    private String clear(String encrypted, java.util.UUID employeeId, String fieldContext) {
        return piiCipher.decryptSensitiveValue(encrypted, employeeId, fieldContext);
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
