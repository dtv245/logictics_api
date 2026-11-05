package com.company.logicstic.service;

import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.driverprofile.DriverProfileSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverProfileView;
import com.company.logicstic.dto.driverprofile.UpsertDriverProfileRequest;
import com.company.logicstic.entity.DriverProfile;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverProfileMapper;
import com.company.logicstic.repository.DriverProfileRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DriverProfileService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final String NATIONAL_ID_CONTEXT = "driver-national-id";
    private static final String TAX_CODE_CONTEXT = "driver-personal-tax-code";

    private final DriverProfileRepository profiles;
    private final EmployeeRepository employees;
    private final DriverPiiCipher piiCipher;
    private final DriverProfileMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverProfileService(DriverProfileRepository profiles, EmployeeRepository employees,
            DriverPiiCipher piiCipher, DriverProfileMapper mapper, CurrentUserService currentUserService) {
        this.profiles = profiles;
        this.employees = employees;
        this.piiCipher = piiCipher;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public DriverProfileView get(UUID driverId) {
        requireDriver(driverId);
        return mapper.toView(requireProfile(driverId));
    }

    public DriverProfileSensitiveView getSensitive(UUID driverId) {
        requireDriver(driverId);
        return mapper.toSensitiveView(requireProfile(driverId));
    }

    public DriverProfileView getMine() {
        return get(requireDriverIdentity().employeeId());
    }

    @Transactional
    public DriverProfileView upsert(UUID driverId, UpsertDriverProfileRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverProfile profile = profiles.findByEmployee_Id(driverId).orElseGet(() -> {
            DriverProfile created = new DriverProfile();
            created.setEmployee(driver);
            return created;
        });
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setGender(blankToNull(request.gender()));
        profile.setNationality(blankToNull(request.nationality()));
        profile.setMaritalStatus(blankToNull(request.maritalStatus()));
        profile.setEducationLevel(blankToNull(request.educationLevel()));
        profile.setNationalIdIssuedDate(request.nationalIdIssuedDate());
        if (request.nationalIdIssuedDate() != null && request.nationalIdIssuedDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("INVALID_NATIONAL_ID_ISSUED_DATE", "National ID issue date cannot be in the future");
        }
        profile.setNationalIdIssuedPlace(blankToNull(request.nationalIdIssuedPlace()));
        profile.setAvatarUrl(blankToNull(request.avatarUrl()));
        profile.setPermanentAddress(blankToNull(request.permanentAddress()));
        profile.setTemporaryAddress(blankToNull(request.temporaryAddress()));
        profile.setHometown(blankToNull(request.hometown()));

        applySensitiveIdentifier(profile, driverId, request.nationalIdNumber(), true);
        applySensitiveIdentifier(profile, driverId, request.personalTaxCode(), false);
        profile.setEmployee(driver);
        try {
            return mapper.toView(profiles.saveAndFlush(profile));
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException("DRIVER_PROFILE_IDENTIFIER_CONFLICT",
                    "A driver profile with these identifiers already exists");
        }
    }

    private void applySensitiveIdentifier(DriverProfile profile, UUID driverId, String submitted, boolean nationalId) {
        String oldCiphertext = nationalId ? profile.getNationalIdNumber() : profile.getPersonalTaxCode();
        String field = nationalId ? "nationalIdNumber" : "personalTaxCode";
        String context = nationalId ? NATIONAL_ID_CONTEXT : TAX_CODE_CONTEXT;
        if (!StringUtils.hasText(submitted)) return; // Omission preserves existing encrypted PII.
        String normalized = submitted.trim();
        if (normalized.matches("\\*{4}[0-9]{4}")) {
            String oldClear = piiCipher.decryptSensitiveValue(oldCiphertext, driverId, context);
            if (!StringUtils.hasText(oldClear) || !mask(oldClear).equals(normalized)) {
                throw new BadRequestException("MASKED_IDENTIFIER_CANNOT_BE_CHANGED",
                        "A masked identifier can only be sent back unchanged");
            }
            return;
        }
        String expectedPattern = nationalId ? "[0-9]{9}|[0-9]{12}" : "[0-9]{10}|[0-9]{13}";
        if (!normalized.matches(expectedPattern)) {
            throw new BadRequestException("INVALID_DRIVER_IDENTIFIER", "The supplied driver identifier is invalid");
        }
        String contextKey = nationalId ? "driver-national-id" : "driver-personal-tax-code";
        String hmac = piiCipher.sensitiveValueHmac(normalized, contextKey);
        UUID currentId = profile.getId();
        boolean exists = nationalId
                ? currentId == null ? profiles.existsByNationalIdNumberHmac(hmac)
                        : profiles.existsByNationalIdNumberHmacAndIdNot(hmac, currentId)
                : currentId == null ? profiles.existsByPersonalTaxCodeHmac(hmac)
                        : profiles.existsByPersonalTaxCodeHmacAndIdNot(hmac, currentId);
        if (exists) throw ConflictException.forUniqueField(field, "This driver identifier is already in use");
        String encrypted = piiCipher.encryptSensitiveValue(normalized, driverId, context);
        if (nationalId) {
            profile.setNationalIdNumber(encrypted);
            profile.setNationalIdNumberHmac(hmac);
        } else {
            profile.setPersonalTaxCode(encrypted);
            profile.setPersonalTaxCodeHmac(hmac);
        }
    }

    private DriverProfile requireProfile(UUID driverId) {
        return profiles.findByEmployee_Id(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found: " + driverId));
    }

    private Employee requireDriver(UUID id) {
        Employee driver = employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private Employee requireDriverForUpdate(UUID id) {
        Employee driver = employees.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private CurrentUserResponse requireDriverIdentity() {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) {
            throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        }
        return identity;
    }

    private static String mask(String value) {
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
