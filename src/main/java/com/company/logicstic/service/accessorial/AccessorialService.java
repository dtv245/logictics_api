package com.company.logicstic.service.accessorial;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.common.enums.AccessorialStatus;
import com.company.logicstic.common.enums.AccessorialType;
import com.company.logicstic.common.enums.CostBasis;
import com.company.logicstic.common.enums.CostSourceType;
import com.company.logicstic.common.enums.ShipmentCostCategory;
import com.company.logicstic.common.enums.ShipmentCostStatus;
import com.company.logicstic.dto.accessorial.AccessorialChargeView;
import com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest;
import com.company.logicstic.dto.accessorial.DetentionCalculationRequest;
import com.company.logicstic.dto.accessorial.DetentionCalculationResult;
import com.company.logicstic.entity.AccessorialCharge;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.entity.TripStop;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.AccessorialChargeRepository;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.ShipmentCostRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessorialService {

    private final AccessorialChargeRepository accessorialChargeRepository;
    private final LoadRepository loadRepository;
    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final DocumentRepository documentRepository;
    private final ShipmentCostRepository shipmentCostRepository;
    private final DetentionCalculator detentionCalculator;
    private final FinancialRoundingPolicy rounding;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public AccessorialChargeView createAccessorial(UUID loadId, CreateAccessorialChargeRequest req) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        AccessorialCharge charge = new AccessorialCharge();
        charge.setLoad(load);
        if (req.tripId() != null) {
            charge.setTrip(tripRepository.findById(req.tripId()).orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + req.tripId())));
            if (tripStopRepository.findByLoadId(loadId).stream().noneMatch(s -> s.getTrip() != null && req.tripId().equals(s.getTrip().getId()))) {
                throw new BadRequestException("ACCESSORIAL_ATTRIBUTION_INVALID", "Trip must be associated with this load");
            }
        }
        if (req.tripStopId() != null) {
            TripStop stop = tripStopRepository.findById(req.tripStopId()).orElseThrow(() -> new ResourceNotFoundException("Trip stop not found: " + req.tripStopId()));
            if (!loadId.equals(stop.getLoad().getId()) || (req.tripId() != null && !req.tripId().equals(stop.getTrip().getId()))) {
                throw new BadRequestException("ACCESSORIAL_ATTRIBUTION_INVALID", "Stop must belong to the specified load and trip");
            }
            charge.setTripStop(stop);
            charge.setTrip(stop.getTrip());
        }
        if (req.documentId() != null) {
            charge.setDocument(documentRepository.findById(req.documentId()).orElseThrow(() -> new ResourceNotFoundException("Document not found: " + req.documentId())));
        }

        try { charge.setType(AccessorialType.valueOf(req.type().trim().toUpperCase(java.util.Locale.ROOT)).name()); }
        catch (RuntimeException invalid) { throw new BadRequestException("ACCESSORIAL_TYPE_INVALID", "Unknown accessorial type"); }
        charge.setStatus(AccessorialStatus.PENDING_APPROVAL.name());
        charge.setQuantity(req.quantity());
        charge.setUnit(req.unit());
        charge.setRate(req.rate());
        charge.setFreeQuantity(req.freeQuantity() != null ? req.freeQuantity() : BigDecimal.ZERO);
        charge.setCurrency(CurrencyGuard.canonical(req.currency()));
        if (req.customerAmount() == null) throw new BadRequestException("Customer amount is required");
        charge.setCustomerAmount(amount(req.customerAmount(), charge.getCurrency()));
        charge.setCompanyCostAmount(amount(req.companyCostAmount(), charge.getCurrency()));
        charge.setDriverPayAmount(amount(req.driverPayAmount(), charge.getCurrency()));
        for (BigDecimal input : new BigDecimal[]{req.quantity(), req.rate(), req.freeQuantity()}) {
            if (input != null && input.signum() < 0) throw new BadRequestException("Quantity, rate and free quantity cannot be negative");
        }
        charge.setOccurredAt(req.occurredAt() != null ? req.occurredAt() : OffsetDateTime.now());
        charge.setNote(req.note());
        charge.setCreatedAt(OffsetDateTime.now());

        AccessorialCharge saved = accessorialChargeRepository.save(charge);
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public DetentionCalculationResult calculateStopDetention(UUID stopId, DetentionCalculationRequest req) {
        TripStop stop = tripStopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("TripStop not found: " + stopId));

        return detentionCalculator.calculate(stopId, stop.getArrivedAt(), stop.getDepartedAt(), req);
    }

    @Transactional
    public AccessorialChargeView approveAccessorial(UUID id, String authenticatedEmail) {
        var actor = employeeRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ForbiddenException("Authenticated tenant employee is required for approval"));
        AccessorialCharge charge = accessorialChargeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("AccessorialCharge not found: " + id));
        if (!AccessorialStatus.APPROVED.name().equals(charge.getStatus()) && !AccessorialStatus.INVOICED.name().equals(charge.getStatus())) {
            if (!AccessorialStatus.PENDING_APPROVAL.name().equals(charge.getStatus())) {
                throw new BadRequestException("ACCESSORIAL_STATE_INVALID", "Only pending accessorial charges can be approved");
            }
            charge.setStatus(AccessorialStatus.APPROVED.name());
            charge.setApprovedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            charge.setApprovedBy(actor.getId());
            accessorialChargeRepository.save(charge);
        }
        AccessorialCharge saved = charge;

        // Project company cost to shipment_costs if company incurred cost > 0
        if (saved.getCompanyCostAmount() != null && saved.getCompanyCostAmount().compareTo(BigDecimal.ZERO) > 0) {
            var existingCost = shipmentCostRepository.findBySourceTypeAndSourceId(CostSourceType.ACCESSORIAL.name(), saved.getId());
            if (existingCost.isPresent()) {
                ShipmentCost cost = existingCost.get();
                if (cost.getAmount().compareTo(saved.getCompanyCostAmount()) != 0 || !cost.getCurrency().equals(saved.getCurrency())
                        || !cost.getLoad().getId().equals(saved.getLoad().getId()) || !"ACTUAL".equals(cost.getCostBasis())) {
                    throw new BadRequestException("ACCESSORIAL_COST_SOURCE_CONFLICT", "Existing cost differs from approved source");
                }
            }
            if (existingCost.isEmpty()) {
                ShipmentCost cost = new ShipmentCost();
                cost.setLoad(saved.getLoad());
                cost.setTrip(saved.getTrip());
                cost.setCategory(ShipmentCostCategory.ACCESSORIAL.name());
                cost.setCostBasis(CostBasis.ACTUAL.name());
                cost.setStatus(ShipmentCostStatus.APPROVED.name());
                cost.setSourceType(CostSourceType.ACCESSORIAL.name());
                cost.setSourceId(saved.getId());
                cost.setAmount(saved.getCompanyCostAmount());
                cost.setCurrency(saved.getCurrency());
                cost.setIncurredAt(saved.getOccurredAt());
                cost.setApprovedAt(saved.getApprovedAt());
                cost.setApprovedBy(saved.getApprovedBy());
                cost.setNote("Approved Accessorial: " + saved.getType());
                shipmentCostRepository.save(cost);
            }
        }

        return toView(saved);
    }

    private BigDecimal amount(BigDecimal value, String currency) {
        if (value != null && value.signum() < 0) throw new BadRequestException("Accessorial amounts must be non-negative");
        return rounding.money(value == null ? BigDecimal.ZERO : value, currency, FinancialRoundingPolicy.Boundary.INVOICE);
    }

    @Transactional(readOnly = true)
    public List<AccessorialChargeView> getAccessorialsForLoad(UUID loadId) {
        if (!loadRepository.existsById(loadId)) {
            throw new ResourceNotFoundException("Load not found: " + loadId);
        }
        return accessorialChargeRepository.findByLoadId(loadId).stream()
                .map(this::toView)
                .toList();
    }

    public AccessorialChargeView toView(AccessorialCharge c) {
        return new AccessorialChargeView(
                c.getId(),
                c.getLoad().getId(),
                c.getTrip() != null ? c.getTrip().getId() : null,
                c.getTripStop() != null ? c.getTripStop().getId() : null,
                c.getType(),
                c.getStatus(),
                c.getQuantity(),
                c.getUnit(),
                c.getRate(),
                c.getFreeQuantity(),
                c.getCustomerAmount(),
                c.getCompanyCostAmount(),
                c.getDriverPayAmount(),
                c.getCurrency(),
                c.getOccurredAt(),
                c.getApprovedAt(),
                c.getApprovedBy(),
                c.getDocument() != null ? c.getDocument().getId() : null,
                c.getNote(),
                c.getVersion()
        );
    }
}
