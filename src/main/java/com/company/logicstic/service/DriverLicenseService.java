package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverlicense.CreateDriverLicenseRequest;
import com.company.logicstic.dto.driverlicense.DriverLicenseSensitiveView;
import com.company.logicstic.dto.driverlicense.DriverLicenseView;
import com.company.logicstic.dto.driverlicense.UpdateDriverLicenseRequest;
import com.company.logicstic.entity.Document;
import com.company.logicstic.entity.DriverLicense;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverLicenseMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.DriverLicenseRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DriverLicenseService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final Set<String> LEGACY_LICENSE_DOCUMENT_TYPES = Set.of("GPLX", "DRIVER_LICENSE");
    private static final Set<String> IMAGE_DOCUMENT_TYPES = Set.of(
            "GPLX", "DRIVER_LICENSE", "DRIVER_LICENSE_FRONT", "DRIVER_LICENSE_BACK");

    private final DriverLicenseRepository licenses;
    private final EmployeeRepository employees;
    private final DocumentRepository documents;
    private final DriverPiiCipher piiCipher;
    private final DriverLicenseMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverLicenseService(DriverLicenseRepository licenses, EmployeeRepository employees,
            DocumentRepository documents, DriverPiiCipher piiCipher, DriverLicenseMapper mapper,
            CurrentUserService currentUserService) {
        this.licenses = licenses;
        this.employees = employees;
        this.documents = documents;
        this.piiCipher = piiCipher;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverLicenseView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty(orderBy)));
        return PagedResponse.from(licenses.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public DriverLicenseView get(UUID driverId, UUID licenseId) {
        requireDriver(driverId);
        return mapper.toView(requireLicense(driverId, licenseId));
    }

    public DriverLicenseSensitiveView getSensitive(UUID driverId, UUID licenseId) {
        requireDriver(driverId);
        return mapper.toSensitiveView(requireLicense(driverId, licenseId));
    }

    public PagedResponse<DriverLicenseView> listMine(int page, int pageSize, String orderBy, boolean descending) {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        return list(identity.employeeId(), page, pageSize, orderBy, descending);
    }

    @Transactional
    public DriverLicenseView create(UUID driverId, CreateDriverLicenseRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        String country = normalizeCountry(request.issuingCountry());
        String licenseNumber = request.licenseNumber().trim();
        String hmac = piiCipher.licenseNumberHmac(licenseNumber, driverId, country);
        ensureUnique(driverId, country, hmac, null);

        DriverLicense license = new DriverLicense();
        license.setEmployee(driver);
        apply(license, request.licenseClass(), request.endorsements(), country, request.issuingRegion(),
                request.issuedDate(), request.expiryDate(), request.issuedPlace(), request.remainingPoints(),
                request.medicalCertificateExpiryDate(), request.documentId(), request.frontImageDocumentId(),
                request.backImageDocumentId());
        license.setLicenseNumber(piiCipher.encryptLicenseNumber(licenseNumber, driverId, country));
        license.setLicenseNumberHmac(hmac);
        license.setStatus("active");
        return mapper.toView(save(license));
    }

    @Transactional
    public DriverLicenseView update(UUID driverId, UUID licenseId, UpdateDriverLicenseRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverLicense license = requireLicense(driverId, licenseId);
        String country = normalizeCountry(request.issuingCountry());
        String clearNumber = StringUtils.hasText(request.licenseNumber())
                ? request.licenseNumber().trim()
                : piiCipher.decryptLicenseNumber(license.getLicenseNumber(), driverId, license.getIssuingCountry());
        String hmac = piiCipher.licenseNumberHmac(clearNumber, driverId, country);
        ensureUnique(driverId, country, hmac, licenseId);

        apply(license, request.licenseClass(), request.endorsements(), country, request.issuingRegion(),
                request.issuedDate(), request.expiryDate(), request.issuedPlace(), request.remainingPoints(),
                request.medicalCertificateExpiryDate(), request.documentId(), request.frontImageDocumentId(),
                request.backImageDocumentId());
        license.setLicenseNumber(piiCipher.encryptLicenseNumber(clearNumber, driverId, country));
        license.setLicenseNumberHmac(hmac);
        license.setStatus(Boolean.TRUE.equals(request.active()) ? "active" : "inactive");
        license.setEmployee(driver);
        return mapper.toView(save(license));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID licenseId) {
        requireDriverForUpdate(driverId);
        DriverLicense license = requireLicense(driverId, licenseId);
        license.setStatus("inactive");
        save(license);
    }

    private void apply(DriverLicense license, String licenseClass, String endorsements, String country,
            String issuingRegion, OffsetDateTime issuedDate, OffsetDateTime expiryDate, String issuedPlace,
            Integer remainingPoints, OffsetDateTime medicalCertificateExpiryDate, UUID documentId,
            UUID frontImageDocumentId, UUID backImageDocumentId) {
        if (!expiryDate.isAfter(issuedDate)) {
            throw new BadRequestException("INVALID_LICENSE_DATES", "Expiry date must be after the issue date");
        }
        if (issuedDate.isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("INVALID_LICENSE_DATES", "Issue date cannot be in the future");
        }
        if (remainingPoints != null && (remainingPoints < 0 || remainingPoints > 12)) {
            throw new BadRequestException("INVALID_LICENSE_POINTS", "Remaining licence points must be between 0 and 12");
        }
        license.setLicenseClass(licenseClass.trim());
        license.setEndorsements(endorsements == null ? "" : endorsements.trim());
        license.setIssuingCountry(country);
        license.setIssuingRegion(blankToNull(issuingRegion));
        license.setIssuedDate(issuedDate);
        license.setExpiresAt(expiryDate);
        license.setMedicalCertExpiresAt(medicalCertificateExpiryDate);
        license.setIssuedPlace(blankToNull(issuedPlace));
        license.setRemainingPoints(remainingPoints == null ? null : remainingPoints.shortValue());
        license.setDocument(requireDocument(documentId, license.getEmployee().getId(), LEGACY_LICENSE_DOCUMENT_TYPES));
        license.setFrontImageDocument(requireDocument(frontImageDocumentId, license.getEmployee().getId(), IMAGE_DOCUMENT_TYPES));
        license.setBackImageDocument(requireDocument(backImageDocumentId, license.getEmployee().getId(), IMAGE_DOCUMENT_TYPES));
    }

    private Document requireDocument(UUID documentId, UUID driverId, Set<String> allowedTypes) {
        if (documentId == null) return null;
        Document document = documents.findByIdAndEmployee_Id(documentId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver document not found: " + documentId));
        String type = document.getType() == null ? "" : document.getType().trim().toUpperCase(Locale.ROOT);
        if (!allowedTypes.contains(type)) {
            throw new BadRequestException("INVALID_DRIVER_DOCUMENT_TYPE", "Linked document must be a driving licence document");
        }
        return document;
    }

    private void ensureUnique(UUID driverId, String country, String hmac, UUID currentId) {
        boolean duplicate = currentId == null
                ? licenses.existsByEmployee_IdAndLicenseNumberHmacAndIssuingCountry(driverId, hmac, country)
                : licenses.existsByEmployee_IdAndLicenseNumberHmacAndIssuingCountryAndIdNot(driverId, hmac, country, currentId);
        if (duplicate) throw duplicateLicense();
    }

    private DriverLicense save(DriverLicense license) {
        try {
            return licenses.saveAndFlush(license);
        } catch (DataIntegrityViolationException ex) {
            // Do not include the license number, SQL value, or document contents in the response.
            throw duplicateLicense();
        }
    }

    private static ConflictException duplicateLicense() {
        return new ConflictException("DRIVER_LICENSE_CONFLICT", "A driving licence with these details already exists");
    }

    private DriverLicense requireLicense(UUID driverId, UUID licenseId) {
        return licenses.findByIdAndEmployee_Id(licenseId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver license not found: " + licenseId));
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

    private static String sortProperty(String field) {
        return switch (field == null ? "expiryDate" : field) {
            case "id" -> "id";
            case "licenseClass" -> "licenseClass";
            case "issuedDate" -> "issuedDate";
            case "expiryDate" -> "expiresAt";
            case "active" -> "status";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported driving licence sort field");
        };
    }

    private static String normalizeCountry(String country) {
        return country.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
