package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverViolationRequest;
import com.company.logicstic.dto.driverprofile.DriverViolationView;
import com.company.logicstic.dto.driverprofile.UpdateDriverViolationRequest;
import com.company.logicstic.entity.Document;
import com.company.logicstic.entity.DriverViolation;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverViolationMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.DriverViolationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverViolationService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final Set<String> DOCUMENT_TYPES = Set.of("DRIVER_VIOLATION", "VIOLATION_REPORT");

    private final DriverViolationRepository violations;
    private final EmployeeRepository employees;
    private final DocumentRepository documents;
    private final TripDriverAssignmentRepository assignments;
    private final DriverViolationMapper mapper;

    public DriverViolationService(DriverViolationRepository violations, EmployeeRepository employees,
            DocumentRepository documents, TripDriverAssignmentRepository assignments, DriverViolationMapper mapper) {
        this.violations = violations;
        this.employees = employees;
        this.documents = documents;
        this.assignments = assignments;
        this.mapper = mapper;
    }

    public PagedResponse<DriverViolationView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        Sort sort = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty(orderBy));
        return PagedResponse.from(violations.findByEmployee_IdAndActiveTrue(driverId,
                PaginationValidator.pageRequest(page, pageSize, sort)).map(mapper::toView));
    }

    public DriverViolationView get(UUID driverId, UUID violationId) {
        requireDriver(driverId);
        return mapper.toView(requireViolation(driverId, violationId));
    }

    @Transactional
    public DriverViolationView create(UUID driverId, CreateDriverViolationRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverViolation violation = new DriverViolation();
        violation.setEmployee(driver);
        apply(violation, request.violationDate(), request.violationType(), request.description(),
                request.fineAmount(), request.fineCurrency(), request.tripId(), request.documentId(),
                request.resolutionStatus() == null ? "OPEN" : request.resolutionStatus(), true, driverId);
        return mapper.toView(violations.saveAndFlush(violation));
    }

    @Transactional
    public DriverViolationView update(UUID driverId, UUID violationId, UpdateDriverViolationRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverViolation violation = requireViolation(driverId, violationId);
        apply(violation, request.violationDate(), request.violationType(), request.description(),
                request.fineAmount(), request.fineCurrency(), request.tripId(), request.documentId(),
                request.resolutionStatus(), request.active(), driverId);
        violation.setEmployee(driver);
        return mapper.toView(violations.saveAndFlush(violation));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID violationId) {
        requireDriverForUpdate(driverId);
        DriverViolation violation = requireViolation(driverId, violationId);
        violation.setActive(false);
        violations.saveAndFlush(violation);
    }

    private void apply(DriverViolation violation, OffsetDateTime date, String type, String description,
            java.math.BigDecimal fineAmount, String currency, UUID tripId, UUID documentId,
            String resolutionStatus, boolean active, UUID driverId) {
        violation.setViolationDate(date);
        violation.setViolationType(type.trim());
        violation.setDescription(description.trim());
        violation.setFineAmount(fineAmount);
        violation.setFineCurrency(currency == null || currency.isBlank()
                ? "VND" : currency.trim().toUpperCase(Locale.ROOT));
        violation.setTrip(resolveAssignedTrip(driverId, tripId, date));
        violation.setDocument(requireDocument(driverId, documentId));
        violation.setResolutionStatus(resolutionStatus.trim().toUpperCase(Locale.ROOT));
        violation.setActive(active);
    }

    private Trip resolveAssignedTrip(UUID driverId, UUID tripId, OffsetDateTime at) {
        if (tripId == null) return null;
        return assignments.findActiveAssignmentsForDriver(driverId, at).stream()
                .filter(assignment -> assignment.getTrip().getId().equals(tripId))
                .map(assignment -> assignment.getTrip())
                .findFirst()
                .orElseThrow(() -> new BadRequestException("DRIVER_NOT_ASSIGNED_TO_TRIP",
                        "The trip must be assigned to this driver at the violation time"));
    }

    private Document requireDocument(UUID driverId, UUID documentId) {
        if (documentId == null) return null;
        Document document = documents.findByIdAndEmployee_Id(documentId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver document not found: " + documentId));
        String type = document.getType() == null ? "" : document.getType().trim().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new BadRequestException("INVALID_DRIVER_DOCUMENT_TYPE",
                    "Linked document must be a driver violation document");
        }
        return document;
    }

    private DriverViolation requireViolation(UUID driverId, UUID violationId) {
        return violations.findByIdAndEmployee_Id(violationId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver violation not found: " + violationId));
    }

    private Employee requireDriver(UUID id) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (employee.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return employee;
    }

    private Employee requireDriverForUpdate(UUID id) {
        Employee employee = employees.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (employee.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return employee;
    }

    private static String sortProperty(String field) {
        return switch (field == null ? "violationDate" : field) {
            case "id" -> "id";
            case "violationDate" -> "violationDate";
            case "violationType", "type" -> "violationType";
            case "resolutionStatus" -> "resolutionStatus";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported violation sort field");
        };
    }
}
