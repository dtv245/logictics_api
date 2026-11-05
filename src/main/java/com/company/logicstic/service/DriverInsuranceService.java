package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverInsuranceRequest;
import com.company.logicstic.dto.driverprofile.DriverInsuranceSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverInsuranceView;
import com.company.logicstic.dto.driverprofile.UpdateDriverInsuranceRequest;
import com.company.logicstic.entity.DriverInsurance;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverInsuranceMapper;
import com.company.logicstic.repository.DriverInsuranceRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DriverInsuranceService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final String SOCIAL_INSURANCE_CONTEXT = "driver-social-insurance-number";
    private static final String HEALTH_CARD_CONTEXT = "driver-health-insurance-card";

    private final DriverInsuranceRepository insurances;
    private final EmployeeRepository employees;
    private final DriverPiiCipher piiCipher;
    private final DriverInsuranceMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverInsuranceService(DriverInsuranceRepository insurances, EmployeeRepository employees,
            DriverPiiCipher piiCipher, DriverInsuranceMapper mapper, CurrentUserService currentUserService) {
        this.insurances = insurances;
        this.employees = employees;
        this.piiCipher = piiCipher;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverInsuranceView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        String property = switch (orderBy == null ? "participationStartDate" : orderBy) {
            case "participationStartDate" -> "participationStartDate";
            case "healthInsuranceExpiryDate" -> "healthInsuranceExpiryDate";
            case "insuredSalary" -> "insuredSalary";
            case "createdAt" -> "createdAt";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported insurance sort field");
        };
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property));
        return PagedResponse.from(insurances.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public PagedResponse<DriverInsuranceView> listMine(int page, int pageSize,
            String orderBy, boolean descending) {
        return list(requireDriverIdentity().employeeId(), page, pageSize, orderBy, descending);
    }

    public DriverInsuranceView get(UUID driverId, UUID insuranceId) {
        requireDriver(driverId);
        return mapper.toView(requireInsurance(driverId, insuranceId));
    }

    public DriverInsuranceSensitiveView getSensitive(UUID driverId, UUID insuranceId) {
        requireDriver(driverId);
        return mapper.toSensitiveView(requireInsurance(driverId, insuranceId));
    }

    @Transactional
    public DriverInsuranceView create(UUID driverId, CreateDriverInsuranceRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverInsurance insurance = new DriverInsurance();
        insurance.setEmployee(driver);
        apply(insurance, request.socialInsuranceNumber(), request.healthInsuranceCardNumber(),
                request.registeredClinic(), request.participationStartDate(), request.participationEndDate(),
                request.socialInsuranceStatus(), request.healthInsuranceStatus(),
                request.unemploymentInsuranceStatus(), request.insuredSalary(),
                request.healthInsuranceExpiryDate(), request.note(), driverId);
        insurance.setActive(true);
        return mapper.toView(save(insurance));
    }

    @Transactional
    public DriverInsuranceView update(UUID driverId, UUID insuranceId, UpdateDriverInsuranceRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverInsurance insurance = requireInsurance(driverId, insuranceId);
        apply(insurance, request.socialInsuranceNumber(), request.healthInsuranceCardNumber(),
                request.registeredClinic(), request.participationStartDate(), request.participationEndDate(),
                request.socialInsuranceStatus(), request.healthInsuranceStatus(),
                request.unemploymentInsuranceStatus(), request.insuredSalary(),
                request.healthInsuranceExpiryDate(), request.note(), driverId);
        insurance.setActive(Boolean.TRUE.equals(request.active()));
        insurance.setEmployee(driver);
        return mapper.toView(save(insurance));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID insuranceId) {
        requireDriverForUpdate(driverId);
        DriverInsurance insurance = requireInsurance(driverId, insuranceId);
        insurance.setActive(false);
        save(insurance);
    }

    private void apply(DriverInsurance insurance, String socialInsuranceNumber, String healthInsuranceCardNumber,
            String registeredClinic, LocalDate startDate, LocalDate endDate, String socialStatus, String healthStatus,
            String unemploymentStatus, java.math.BigDecimal insuredSalary, LocalDate healthExpiry, String note,
            UUID driverId) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("INVALID_INSURANCE_DATES", "Participation end date cannot precede its start date");
        }
        if (insuredSalary != null && insuredSalary.signum() < 0) {
            throw new BadRequestException("INVALID_INSURED_SALARY", "Insured salary cannot be negative");
        }
        insurance.setRegisteredClinic(blankToNull(registeredClinic));
        insurance.setParticipationStartDate(startDate);
        insurance.setParticipationEndDate(endDate);
        insurance.setSocialInsuranceStatus(blankToNull(socialStatus));
        insurance.setHealthInsuranceStatus(blankToNull(healthStatus));
        insurance.setUnemploymentInsuranceStatus(blankToNull(unemploymentStatus));
        insurance.setInsuredSalary(insuredSalary);
        insurance.setHealthInsuranceExpiryDate(healthExpiry);
        insurance.setNote(blankToNull(note));
        applyIdentifier(insurance, driverId, socialInsuranceNumber, true);
        applyIdentifier(insurance, driverId, healthInsuranceCardNumber, false);
    }

    private void applyIdentifier(DriverInsurance insurance, UUID driverId, String submitted, boolean social) {
        if (!StringUtils.hasText(submitted)) return;
        String oldEncrypted = social ? insurance.getSocialInsuranceNumber() : insurance.getHealthInsuranceCardNumber();
        String context = social ? SOCIAL_INSURANCE_CONTEXT : HEALTH_CARD_CONTEXT;
        String normalized = submitted.trim();
        if (normalized.matches("\\*{4}[A-Za-z0-9]{4}")) {
            String oldClear = piiCipher.decryptSensitiveValue(oldEncrypted, driverId, context);
            if (!StringUtils.hasText(oldClear) || !mask(oldClear).equals(normalized.toUpperCase(Locale.ROOT))) {
                throw new BadRequestException("MASKED_IDENTIFIER_CANNOT_BE_CHANGED",
                        "A masked insurance identifier can only be sent back unchanged");
            }
            return;
        }
        if (!normalized.matches("[A-Za-z0-9 -]{8,20}")) {
            throw new BadRequestException("INVALID_INSURANCE_IDENTIFIER", "The supplied insurance identifier is invalid");
        }
        String canonical = normalized.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
        String contextKey = social ? "driver-social-insurance-number" : "driver-health-insurance-card";
        String encrypted = piiCipher.encryptSensitiveValue(canonical, driverId, context);
        String hmac = piiCipher.sensitiveValueHmac(canonical, contextKey);
        if (social) {
            insurance.setSocialInsuranceNumber(encrypted);
            insurance.setSocialInsuranceNumberHmac(hmac);
        } else {
            insurance.setHealthInsuranceCardNumber(encrypted);
            insurance.setHealthInsuranceCardNumberHmac(hmac);
        }
    }

    private DriverInsurance save(DriverInsurance insurance) {
        try {
            return insurances.saveAndFlush(insurance);
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException("DRIVER_INSURANCE_CONFLICT", "The insurance record conflicts with stored data");
        }
    }

    private DriverInsurance requireInsurance(UUID driverId, UUID insuranceId) {
        return insurances.findByIdAndEmployee_Id(insuranceId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver insurance record not found: " + insuranceId));
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
