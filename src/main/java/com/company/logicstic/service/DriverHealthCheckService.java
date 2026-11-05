package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverlicense.CreateDriverHealthCheckRequest;
import com.company.logicstic.dto.driverlicense.DriverHealthCheckView;
import com.company.logicstic.dto.driverlicense.UpdateDriverHealthCheckRequest;
import com.company.logicstic.entity.Document;
import com.company.logicstic.entity.DriverHealthCheck;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverHealthCheckMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.DriverHealthCheckRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DriverHealthCheckService {
    private static final String DRIVER_ROLE = "DRIVER";
    private final DriverHealthCheckRepository checks;
    private final EmployeeRepository employees;
    private final DocumentRepository documents;
    private final DriverHealthCheckMapper mapper;
    private final DriverPiiCipher piiCipher;
    private final CurrentUserService currentUserService;

    public DriverHealthCheckService(DriverHealthCheckRepository checks, EmployeeRepository employees,
            DocumentRepository documents, DriverHealthCheckMapper mapper, DriverPiiCipher piiCipher,
            CurrentUserService currentUserService) {
        this.checks = checks;
        this.employees = employees;
        this.documents = documents;
        this.mapper = mapper;
        this.piiCipher = piiCipher;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverHealthCheckView> list(UUID driverId, int page, int pageSize, String orderBy, boolean descending) {
        requireDriver(driverId);
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty(orderBy)));
        return PagedResponse.from(checks.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public DriverHealthCheckView get(UUID driverId, UUID checkId) {
        requireDriver(driverId);
        return mapper.toView(requireCheck(driverId, checkId));
    }

    public PagedResponse<DriverHealthCheckView> listMine(int page, int pageSize, String orderBy, boolean descending) {
        var identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        return list(identity.employeeId(), page, pageSize, orderBy, descending);
    }

    @Transactional
    public DriverHealthCheckView create(UUID driverId, CreateDriverHealthCheckRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        validateDates(request.checkedDate(), request.expiryDate());
        DriverHealthCheck check = new DriverHealthCheck();
        check.setEmployee(driver);
        check.setCheckedDate(request.checkedDate());
        check.setExpiryDate(request.expiryDate());
        check.setFacility(request.facility().trim());
        check.setConclusion(piiCipher.encryptSensitiveValue(request.conclusion(), driverId, "health-check-conclusion"));
        check.setDocument(requireDocument(request.documentId(), driverId));
        check.setStatus("active");
        return mapper.toView(checks.saveAndFlush(check));
    }

    @Transactional
    public DriverHealthCheckView update(UUID driverId, UUID checkId, UpdateDriverHealthCheckRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverHealthCheck check = requireCheck(driverId, checkId);
        validateDates(request.checkedDate(), request.expiryDate());
        check.setEmployee(driver);
        check.setCheckedDate(request.checkedDate());
        check.setExpiryDate(request.expiryDate());
        check.setFacility(request.facility().trim());
        check.setConclusion(piiCipher.encryptSensitiveValue(request.conclusion(), driverId, "health-check-conclusion"));
        check.setDocument(requireDocument(request.documentId(), driverId));
        check.setStatus(Boolean.TRUE.equals(request.active()) ? "active" : "inactive");
        return mapper.toView(checks.saveAndFlush(check));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID checkId) {
        requireDriverForUpdate(driverId);
        DriverHealthCheck check = requireCheck(driverId, checkId);
        check.setStatus("inactive");
        checks.saveAndFlush(check);
    }

    private Document requireDocument(UUID id, UUID driverId) {
        if (id == null) return null;
        Document document = documents.findByIdAndEmployee_Id(id, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver health document not found: " + id));
        String type = document.getType() == null ? "" : document.getType().trim().toUpperCase(Locale.ROOT);
        if (!"HEALTH_CHECK".equals(type)) {
            throw new BadRequestException("INVALID_DRIVER_DOCUMENT_TYPE", "Linked document must be a health check document");
        }
        return document;
    }

    private DriverHealthCheck requireCheck(UUID driverId, UUID checkId) {
        return checks.findByIdAndEmployee_Id(checkId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver health check not found: " + checkId));
    }

    private Employee requireDriver(UUID id) {
        Employee driver = employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER)
            throw new ResourceNotFoundException("Driver not found: " + id);
        return driver;
    }

    private Employee requireDriverForUpdate(UUID id) {
        Employee driver = employees.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER)
            throw new ResourceNotFoundException("Driver not found: " + id);
        return driver;
    }

    private static void validateDates(LocalDate checkedDate, LocalDate expiryDate) {
        if (checkedDate.isAfter(LocalDate.now()) || !expiryDate.isAfter(checkedDate))
            throw new BadRequestException("INVALID_HEALTH_CHECK_DATES", "Expiry date must be after a non-future check date");
    }

    private static String sortProperty(String field) {
        return switch (field == null ? "expiryDate" : field) {
            case "id" -> "id";
            case "checkedDate" -> "checkedDate";
            case "expiryDate" -> "expiryDate";
            case "facility" -> "facility";
            case "active" -> "status";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported health check sort field");
        };
    }
}
