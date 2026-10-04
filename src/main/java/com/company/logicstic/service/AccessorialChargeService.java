package com.company.logicstic.service;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.dto.accessorial.AccessorialChargeView;
import com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest;
import com.company.logicstic.entity.AccessorialCharge;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.AccessorialChargeRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Legacy implementation retained for source compatibility, not registered as an active service.
// The canonical accessorial.AccessorialService owns approval/projection invariants.
@Deprecated @RequiredArgsConstructor
public class AccessorialChargeService {
    private final AccessorialChargeRepository repository; private final LoadRepository loadRepository;
    private final TripRepository tripRepository; private final TripStopRepository tripStopRepository;
    @Transactional public AccessorialChargeView create(UUID loadId, CreateAccessorialChargeRequest req) {
        AccessorialCharge charge = new AccessorialCharge();
        charge.setLoad(loadRepository.findById(loadId).orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId)));
        if (req.tripId() != null) charge.setTrip(tripRepository.findById(req.tripId()).orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + req.tripId())));
        if (req.tripStopId() != null) charge.setTripStop(tripStopRepository.findById(req.tripStopId()).orElseThrow(() -> new ResourceNotFoundException("TripStop not found: " + req.tripStopId())));
        String currency = CurrencyGuard.normalize(req.currency()); charge.setType(req.type().trim().toUpperCase()); charge.setCurrency(currency);
        charge.setQuantity(req.quantity()); charge.setUnit(req.unit()); charge.setRate(req.rate()); charge.setFreeQuantity(req.freeQuantity());
        charge.setCustomerAmount(MoneyRoundingPolicy.roundMoney(req.customerAmount(), currency)); charge.setCompanyCostAmount(MoneyRoundingPolicy.roundMoney(req.companyCostAmount(), currency)); charge.setDriverPayAmount(MoneyRoundingPolicy.roundMoney(req.driverPayAmount(), currency));
        charge.setOccurredAt(req.occurredAt()); charge.setNote(req.note()); return view(repository.save(charge));
    }
    @Transactional public AccessorialChargeView approve(UUID id, UUID actorId) {
        AccessorialCharge charge = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Accessorial charge not found: " + id));
        if (!"PENDING_APPROVAL".equals(charge.getStatus())) throw new BadRequestException("INVALID_ACCESSORIAL_TRANSITION", "Only pending accessorial charges can be approved");
        charge.setStatus("APPROVED"); charge.setApprovedAt(OffsetDateTime.now()); charge.setApprovedBy(actorId); return view(repository.save(charge));
    }
    private AccessorialChargeView view(AccessorialCharge c) { return new AccessorialChargeView(c.getId(), c.getLoad().getId(), c.getTrip() == null ? null : c.getTrip().getId(), c.getTripStop() == null ? null : c.getTripStop().getId(), c.getType(), c.getStatus(), c.getQuantity(), c.getUnit(), c.getRate(), c.getFreeQuantity(), c.getCustomerAmount(), c.getCompanyCostAmount(), c.getDriverPayAmount(), c.getCurrency(), c.getOccurredAt(), c.getApprovedAt(), c.getApprovedBy(), c.getDocument() == null ? null : c.getDocument().getId(), c.getNote(), c.getVersion()); }
}
