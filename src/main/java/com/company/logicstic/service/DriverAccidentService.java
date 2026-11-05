package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverAccidentRequest;
import com.company.logicstic.dto.driverprofile.DriverAccidentView;
import com.company.logicstic.dto.driverprofile.UpdateDriverAccidentRequest;
import com.company.logicstic.entity.AccidentReport;
import com.company.logicstic.entity.Document;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverAccidentMapper;
import com.company.logicstic.repository.AccidentReportProfileRepository;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import com.company.logicstic.repository.TruckRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverAccidentService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final String CANCELLED = "CANCELLED";
    private static final Set<String> DOCUMENT_TYPES = Set.of("ACCIDENT_REPORT", "ACCIDENT_PHOTO", "POLICE_REPORT");

    private final AccidentReportProfileRepository accidents;
    private final EmployeeRepository employees;
    private final TruckRepository trucks;
    private final DocumentRepository documents;
    private final TripDriverAssignmentRepository assignments;
    private final DriverAccidentMapper mapper;

    public DriverAccidentService(AccidentReportProfileRepository accidents, EmployeeRepository employees,
            TruckRepository trucks, DocumentRepository documents, TripDriverAssignmentRepository assignments,
            DriverAccidentMapper mapper) {
        this.accidents = accidents;
        this.employees = employees;
        this.trucks = trucks;
        this.documents = documents;
        this.assignments = assignments;
        this.mapper = mapper;
    }

    public PagedResponse<DriverAccidentView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        Sort sort = Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty(orderBy));
        var result = accidents.findByDriver_IdAndStatusNot(driverId, CANCELLED,
                PaginationValidator.pageRequest(page, pageSize, sort));
        Map<UUID, List<UUID>> documentIds = documentIds(result.getContent().stream().map(AccidentReport::getId).toList());
        return PagedResponse.from(result.map(accident -> mapper.toView(
                accident, documentIds.getOrDefault(accident.getId(), List.of()))));
    }

    public DriverAccidentView get(UUID driverId, UUID accidentId) {
        requireDriver(driverId);
        AccidentReport accident = requireAccident(driverId, accidentId);
        return mapper.toView(accident, documentIds(List.of(accidentId)).getOrDefault(accidentId, List.of()));
    }

    @Transactional
    public DriverAccidentView create(UUID driverId, CreateDriverAccidentRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        AccidentReport accident = new AccidentReport();
        accident.setDriver(driver);
        apply(accident, request.truckId(), request.tripId(), request.accidentType(), request.severity(),
                request.accidentDateTime(), request.latitude(), request.longitude(), request.address(),
                request.description(), request.weatherConditions(), request.roadConditions(), request.anyInjuries(),
                request.numberOfInjuries(), request.injuryDescription(), request.vehicleDamaged(),
                request.vehicleDamageDescription(), request.estimatedDamageCost(), request.vehicleDrivable(),
                request.policeReportFiled(), request.policeReportNumber(), request.insuranceNotified(),
                request.insuranceNotifiedAt(), request.insuranceClaimNumber(),
                request.status() == null ? "REPORTED" : request.status(), driverId);
        accidents.saveAndFlush(accident);
        attachDocument(accident, driverId, request.documentId());
        documents.flush();
        return mapper.toView(accident, documentIds(List.of(accident.getId()))
                .getOrDefault(accident.getId(), List.of()));
    }

    @Transactional
    public DriverAccidentView update(UUID driverId, UUID accidentId, UpdateDriverAccidentRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        AccidentReport accident = requireAccident(driverId, accidentId);
        apply(accident, request.truckId(), request.tripId(), request.accidentType(), request.severity(),
                request.accidentDateTime(), request.latitude(), request.longitude(), request.address(),
                request.description(), request.weatherConditions(), request.roadConditions(), request.anyInjuries(),
                request.numberOfInjuries(), request.injuryDescription(), request.vehicleDamaged(),
                request.vehicleDamageDescription(), request.estimatedDamageCost(), request.vehicleDrivable(),
                request.policeReportFiled(), request.policeReportNumber(), request.insuranceNotified(),
                request.insuranceNotifiedAt(), request.insuranceClaimNumber(), request.status(), driverId);
        accident.setDriver(driver);
        accidents.saveAndFlush(accident);
        attachDocument(accident, driverId, request.documentId());
        documents.flush();
        return mapper.toView(accident, documentIds(List.of(accident.getId()))
                .getOrDefault(accident.getId(), List.of()));
    }

    @Transactional
    public void cancel(UUID driverId, UUID accidentId) {
        requireDriverForUpdate(driverId);
        AccidentReport accident = requireAccident(driverId, accidentId);
        accident.setStatus(CANCELLED);
        accidents.saveAndFlush(accident);
    }

    private void apply(AccidentReport accident, UUID truckId, UUID tripId, String accidentType, String severity,
            OffsetDateTime accidentDateTime, Double latitude, Double longitude, String address, String description,
            String weatherConditions, String roadConditions, Boolean anyInjuries, Integer numberOfInjuries,
            String injuryDescription, Boolean vehicleDamaged, String vehicleDamageDescription,
            BigDecimal estimatedDamageCost, Boolean vehicleDrivable, Boolean policeReportFiled,
            String policeReportNumber, Boolean insuranceNotified, OffsetDateTime insuranceNotifiedAt,
            String insuranceClaimNumber, String status, UUID driverId) {
        validateCrossFields(anyInjuries, numberOfInjuries, vehicleDamaged, estimatedDamageCost,
                policeReportFiled, policeReportNumber, insuranceNotified, insuranceNotifiedAt);
        Truck truck = trucks.findById(truckId)
                .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + truckId));
        Trip trip = resolveAssignedTrip(driverId, tripId, accidentDateTime);
        if (trip != null && trip.getTruck() != null && !trip.getTruck().getId().equals(truckId)) {
            throw new BadRequestException("ACCIDENT_TRUCK_MISMATCH", "The truck must match the linked trip");
        }
        accident.setTruck(truck);
        accident.setTrip(trip);
        accident.setAccidentType(accidentType.trim().toUpperCase(Locale.ROOT));
        accident.setSeverity(severity.trim().toUpperCase(Locale.ROOT));
        accident.setAccidentDateTime(accidentDateTime);
        accident.setLatitude(latitude);
        accident.setLongitude(longitude);
        accident.setAddress(blankToNull(address));
        accident.setDescription(blankToNull(description));
        accident.setWeatherConditions(blankToNull(weatherConditions));
        accident.setRoadConditions(blankToNull(roadConditions));
        accident.setAnyInjuries(anyInjuries);
        accident.setNumberOfInjuries(numberOfInjuries);
        accident.setInjuryDescription(blankToNull(injuryDescription));
        accident.setVehicleDamaged(vehicleDamaged);
        accident.setVehicleDamageDescription(blankToNull(vehicleDamageDescription));
        accident.setEstimatedDamageCost(estimatedDamageCost);
        accident.setVehicleDrivable(vehicleDrivable);
        accident.setPoliceReportFiled(policeReportFiled);
        accident.setPoliceReportNumber(blankToNull(policeReportNumber));
        accident.setInsuranceNotified(insuranceNotified);
        accident.setInsuranceNotifiedAt(insuranceNotifiedAt);
        accident.setInsuranceClaimNumber(blankToNull(insuranceClaimNumber));
        accident.setStatus(status.trim().toUpperCase(Locale.ROOT));
    }

    private static void validateCrossFields(Boolean anyInjuries, Integer numberOfInjuries,
            Boolean vehicleDamaged, BigDecimal damageCost, Boolean policeReportFiled, String policeReportNumber,
            Boolean insuranceNotified, OffsetDateTime insuranceNotifiedAt) {
        if (!anyInjuries && numberOfInjuries != null && numberOfInjuries > 0) {
            throw new BadRequestException("INVALID_INJURY_DETAILS", "Injury count requires anyInjuries to be true");
        }
        if (!vehicleDamaged && damageCost != null && damageCost.signum() > 0) {
            throw new BadRequestException("INVALID_DAMAGE_DETAILS", "Damage cost requires vehicleDamaged to be true");
        }
        if (!policeReportFiled && policeReportNumber != null && !policeReportNumber.isBlank()) {
            throw new BadRequestException("INVALID_POLICE_REPORT_DETAILS", "Police report number requires a filed report");
        }
        if (!insuranceNotified && insuranceNotifiedAt != null) {
            throw new BadRequestException("INVALID_INSURANCE_DETAILS", "Insurance notification time requires insuranceNotified to be true");
        }
    }

    private Trip resolveAssignedTrip(UUID driverId, UUID tripId, OffsetDateTime at) {
        if (tripId == null) return null;
        return assignments.findActiveAssignmentsForDriver(driverId, at).stream()
                .filter(assignment -> assignment.getTrip().getId().equals(tripId))
                .map(assignment -> assignment.getTrip())
                .findFirst()
                .orElseThrow(() -> new BadRequestException("DRIVER_NOT_ASSIGNED_TO_TRIP",
                        "The trip must be assigned to this driver at the accident time"));
    }

    private void attachDocument(AccidentReport accident, UUID driverId, UUID documentId) {
        if (documentId == null) return;
        Document document = documents.findByIdAndEmployee_Id(documentId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver document not found: " + documentId));
        String type = document.getType() == null ? "" : document.getType().trim().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new BadRequestException("INVALID_DRIVER_DOCUMENT_TYPE",
                    "Linked document must be an accident report, accident photo, or police report");
        }
        if (document.getAccidentReport() != null
                && !document.getAccidentReport().getId().equals(accident.getId())) {
            throw new ConflictException("DOCUMENT_ALREADY_LINKED", "Document is already linked to another accident");
        }
        document.setAccidentReport(accident);
        documents.save(document);
    }

    private Map<UUID, List<UUID>> documentIds(Collection<UUID> accidentIds) {
        if (accidentIds.isEmpty()) return Map.of();
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (Object[] row : accidents.findDocumentIdsByAccidentReportIds(accidentIds)) {
            UUID accidentId = (UUID) row[0];
            UUID documentId = (UUID) row[1];
            result.computeIfAbsent(accidentId, ignored -> new ArrayList<>()).add(documentId);
        }
        return result;
    }

    private AccidentReport requireAccident(UUID driverId, UUID accidentId) {
        return accidents.findByIdAndDriver_Id(accidentId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver accident not found: " + accidentId));
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
        return switch (field == null ? "accidentDateTime" : field) {
            case "id" -> "id";
            case "accidentDateTime" -> "accidentDateTime";
            case "accidentType" -> "accidentType";
            case "severity" -> "severity";
            case "status" -> "status";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported accident sort field");
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
