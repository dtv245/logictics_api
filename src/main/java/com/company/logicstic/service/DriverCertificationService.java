package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.driverlicense.CreateDriverCertificationRequest;
import com.company.logicstic.dto.driverlicense.DriverCertificationView;
import com.company.logicstic.dto.driverlicense.UpdateDriverCertificationRequest;
import com.company.logicstic.entity.Document;
import com.company.logicstic.entity.DriverCertification;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverCertificationMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.DriverCertificationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverCertificationService {
    private static final String DRIVER_ROLE = "DRIVER";
    private final DriverCertificationRepository certifications;
    private final EmployeeRepository employees;
    private final DocumentRepository documents;
    private final DriverCertificationMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverCertificationService(DriverCertificationRepository certifications, EmployeeRepository employees,
            DocumentRepository documents, DriverCertificationMapper mapper, CurrentUserService currentUserService) {
        this.certifications = certifications;
        this.employees = employees;
        this.documents = documents;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverCertificationView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty(orderBy)));
        return PagedResponse.from(certifications.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public DriverCertificationView get(UUID driverId, UUID certificationId) {
        requireDriver(driverId);
        return mapper.toView(requireCertification(driverId, certificationId));
    }

    public PagedResponse<DriverCertificationView> listMine(int page, int pageSize, String orderBy, boolean descending) {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        return list(identity.employeeId(), page, pageSize, orderBy, descending);
    }

    @Transactional
    public DriverCertificationView create(UUID driverId, CreateDriverCertificationRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        validateDates(request.issuedDate(), request.expiryDate());
        DriverCertification certification = new DriverCertification();
        certification.setEmployee(driver);
        apply(certification, request.type(), request.name(), request.issuedDate(), request.expiryDate(), request.issuer());
        certification.setDocument(requireDocument(request.documentId(), driverId));
        certification.setStatus("active");
        return mapper.toView(certifications.saveAndFlush(certification));
    }

    @Transactional
    public DriverCertificationView update(UUID driverId, UUID certificationId, UpdateDriverCertificationRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverCertification certification = requireCertification(driverId, certificationId);
        validateDates(request.issuedDate(), request.expiryDate());
        certification.setEmployee(driver);
        apply(certification, request.type(), request.name(), request.issuedDate(), request.expiryDate(), request.issuer());
        certification.setDocument(requireDocument(request.documentId(), driverId));
        certification.setStatus(Boolean.TRUE.equals(request.active()) ? "active" : "inactive");
        return mapper.toView(certifications.saveAndFlush(certification));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID certificationId) {
        requireDriverForUpdate(driverId);
        DriverCertification certification = requireCertification(driverId, certificationId);
        certification.setStatus("inactive");
        certifications.saveAndFlush(certification);
    }

    private void apply(DriverCertification certification, String type, String name, LocalDate issuedDate,
            LocalDate expiryDate, String issuer) {
        certification.setType(type.trim().toUpperCase(Locale.ROOT));
        certification.setName(name.trim());
        certification.setIssuedDate(issuedDate);
        certification.setExpiryDate(expiryDate);
        certification.setIssuer(issuer == null || issuer.isBlank() ? null : issuer.trim());
    }

    private Document requireDocument(UUID id, UUID driverId) {
        if (id == null) return null;
        Document document = documents.findByIdAndEmployee_Id(id, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver certification document not found: " + id));
        String type = document.getType() == null ? "" : document.getType().trim().toUpperCase(Locale.ROOT);
        if (!"CERTIFICATION".equals(type))
            throw new BadRequestException("INVALID_DRIVER_DOCUMENT_TYPE", "Linked document must be a certification document");
        return document;
    }

    private DriverCertification requireCertification(UUID driverId, UUID certificationId) {
        return certifications.findByIdAndEmployee_Id(certificationId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver certification not found: " + certificationId));
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

    private static void validateDates(LocalDate issuedDate, LocalDate expiryDate) {
        if (issuedDate.isAfter(LocalDate.now()) || (expiryDate != null && !expiryDate.isAfter(issuedDate)))
            throw new BadRequestException("INVALID_CERTIFICATION_DATES", "Expiry date must be after the issue date");
    }

    private static String sortProperty(String field) {
        return switch (field == null ? "expiryDate" : field) {
            case "id" -> "id";
            case "type" -> "type";
            case "name" -> "name";
            case "issuedDate" -> "issuedDate";
            case "expiryDate" -> "expiryDate";
            case "active" -> "status";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported certification sort field");
        };
    }
}
