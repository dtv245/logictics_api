package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverDependentRequest;
import com.company.logicstic.dto.driverprofile.DriverDependentSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverDependentView;
import com.company.logicstic.dto.driverprofile.UpdateDriverDependentRequest;
import com.company.logicstic.entity.DriverDependent;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverDependentMapper;
import com.company.logicstic.repository.DriverDependentRepository;
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
public class DriverDependentService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final String NATIONAL_ID_CONTEXT = "driver-dependent-national-id";
    private static final String TAX_CODE_CONTEXT = "driver-dependent-tax-code";
    private final DriverDependentRepository dependents;
    private final EmployeeRepository employees;
    private final DriverPiiCipher piiCipher;
    private final DriverDependentMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverDependentService(DriverDependentRepository dependents, EmployeeRepository employees,
            DriverPiiCipher piiCipher, DriverDependentMapper mapper, CurrentUserService currentUserService) {
        this.dependents = dependents;
        this.employees = employees;
        this.piiCipher = piiCipher;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverDependentView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        String property = switch (orderBy == null ? "fullName" : orderBy) {
            case "fullName" -> "fullName";
            case "dateOfBirth" -> "dateOfBirth";
            case "deductionStartDate" -> "deductionStartDate";
            case "createdAt" -> "createdAt";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported dependent sort field");
        };
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property));
        return PagedResponse.from(dependents.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public PagedResponse<DriverDependentView> listMine(int page, int pageSize, String orderBy, boolean descending) {
        return list(requireDriverIdentity().employeeId(), page, pageSize, orderBy, descending);
    }

    public DriverDependentView get(UUID driverId, UUID dependentId) {
        requireDriver(driverId);
        return mapper.toView(requireDependent(driverId, dependentId));
    }

    public DriverDependentSensitiveView getSensitive(UUID driverId, UUID dependentId) {
        requireDriver(driverId);
        return mapper.toSensitiveView(requireDependent(driverId, dependentId));
    }

    @Transactional
    public DriverDependentView create(UUID driverId, CreateDriverDependentRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverDependent dependent = new DriverDependent();
        dependent.setEmployee(driver);
        apply(dependent, request.fullName(), request.relationship(), request.dateOfBirth(),
                request.nationalIdNumber(), request.taxCode(), request.deductionStartDate(),
                request.deductionEndDate(), driverId);
        dependent.setActive(true);
        return mapper.toView(save(dependent));
    }

    @Transactional
    public DriverDependentView update(UUID driverId, UUID dependentId, UpdateDriverDependentRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverDependent dependent = requireDependent(driverId, dependentId);
        apply(dependent, request.fullName(), request.relationship(), request.dateOfBirth(),
                request.nationalIdNumber(), request.taxCode(), request.deductionStartDate(),
                request.deductionEndDate(), driverId);
        dependent.setActive(Boolean.TRUE.equals(request.active()));
        dependent.setEmployee(driver);
        return mapper.toView(save(dependent));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID dependentId) {
        requireDriverForUpdate(driverId);
        DriverDependent dependent = requireDependent(driverId, dependentId);
        dependent.setActive(false);
        save(dependent);
    }

    private void apply(DriverDependent dependent, String fullName, String relationship, LocalDate dateOfBirth,
            String nationalId, String taxCode, LocalDate deductionStart, LocalDate deductionEnd, UUID driverId) {
        if (deductionEnd != null && (deductionStart == null || deductionEnd.isBefore(deductionStart))) {
            throw new BadRequestException("INVALID_DEDUCTION_DATES", "Deduction end date requires a start date and cannot precede it");
        }
        dependent.setFullName(fullName.trim());
        dependent.setRelationship(relationship.trim());
        dependent.setDateOfBirth(dateOfBirth);
        dependent.setDeductionStartDate(deductionStart);
        dependent.setDeductionEndDate(deductionEnd);
        applyIdentifier(dependent, driverId, nationalId, true);
        applyIdentifier(dependent, driverId, taxCode, false);
    }

    private void applyIdentifier(DriverDependent dependent, UUID driverId, String submitted, boolean nationalId) {
        if (!StringUtils.hasText(submitted)) return;
        String oldEncrypted = nationalId ? dependent.getNationalIdNumber() : dependent.getTaxCode();
        String fieldContext = nationalId ? NATIONAL_ID_CONTEXT : TAX_CODE_CONTEXT;
        String expected = nationalId ? "[0-9]{9}|[0-9]{12}" : "[0-9]{10}|[0-9]{13}";
        String normalized = submitted.trim();
        if (normalized.matches("\\*{4}[0-9]{4}")) {
            String oldClear = piiCipher.decryptSensitiveValue(oldEncrypted, driverId, fieldContext);
            if (!StringUtils.hasText(oldClear) || !mask(oldClear).equals(normalized)) {
                throw new BadRequestException("MASKED_IDENTIFIER_CANNOT_BE_CHANGED",
                        "A masked dependent identifier can only be sent back unchanged");
            }
            return;
        }
        if (!normalized.matches(expected)) {
            throw new BadRequestException("INVALID_DEPENDENT_IDENTIFIER", "The supplied dependent identifier is invalid");
        }
        String contextKey = nationalId ? "driver-dependent-national-id" : "driver-dependent-tax-code";
        String ciphertext = piiCipher.encryptSensitiveValue(normalized, driverId, fieldContext);
        String hmac = piiCipher.sensitiveValueHmac(normalized, contextKey);
        if (nationalId) {
            dependent.setNationalIdNumber(ciphertext);
            dependent.setNationalIdNumberHmac(hmac);
        } else {
            dependent.setTaxCode(ciphertext);
            dependent.setTaxCodeHmac(hmac);
        }
    }

    private DriverDependent save(DriverDependent dependent) {
        try {
            return dependents.saveAndFlush(dependent);
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException("DRIVER_DEPENDENT_CONFLICT", "The dependent record conflicts with stored data");
        }
    }

    private DriverDependent requireDependent(UUID driverId, UUID dependentId) {
        return dependents.findByIdAndEmployee_Id(dependentId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver dependent not found: " + dependentId));
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
}
