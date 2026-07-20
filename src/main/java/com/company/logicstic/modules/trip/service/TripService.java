package com.company.logicstic.modules.trip.service;

import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.load.entity.TripStop;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.notification.event.TenantNotificationEvent;
import com.company.logicstic.modules.trip.dto.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.TripView;
import com.company.logicstic.modules.trip.entity.Trip;
import com.company.logicstic.modules.trip.entity.TripStatus;
import com.company.logicstic.modules.trip.mapper.TripMapper;
import com.company.logicstic.modules.trip.repository.TripRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.BadRequestException;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class TripService extends AbstractBaseService<Trip, TripView, CreateTripRequest> {

  private final TripRepository tripRepository;
  private final TruckRepository truckRepository;
  private final LoadRepository loadRepository;
  private final TripMapper tripMapper;
  private final ApplicationEventPublisher eventPublisher;

  public TripService(
      TripRepository tripRepository,
      TruckRepository truckRepository,
      LoadRepository loadRepository,
      TripMapper tripMapper,
      ApplicationEventPublisher eventPublisher) {
    super(tripRepository, tripMapper::toView, tripMapper::toEntity, tripMapper::updateEntity);
    this.tripRepository = tripRepository;
    this.truckRepository = truckRepository;
    this.loadRepository = loadRepository;
    this.tripMapper = tripMapper;
    this.eventPublisher = eventPublisher;
  }

  @Override
  protected String entityName() {
    return "Trip";
  }

  public PagedResponse<TripView> search(
      String search,
      String status,
      UUID truckId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(tripRepository.search(search, status, truckId, pageable));
  }

  @Override
  protected void beforeCreate(Trip trip, CreateTripRequest request) {
    if (parseStatus(request.status()) != TripStatus.DRAFT) {
      throw new InvalidStateTransitionException("Trip", "null", request.status());
    }
    trip.setStatusEnum(TripStatus.DRAFT);
    resolveRelations(trip, request);
    rebuildStops(trip, request);
  }

  @Override
  protected void beforeMapUpdate(Trip trip, CreateTripRequest request) {
    if (parseStatus(request.status()) != trip.getStatusEnum()) {
      throw new InvalidStateTransitionException("Trip", trip.getStatus(), request.status());
    }
  }

  @Override
  protected void beforeUpdate(Trip trip, CreateTripRequest request) {
    resolveRelations(trip, request);
    rebuildStops(trip, request);
  }

  private void resolveRelations(Trip trip, CreateTripRequest req) {
    if (req.truckId() != null) {
      trip.setTruck(
          truckRepository
              .findById(req.truckId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Truck not found: " + req.truckId())));
    } else {
      trip.setTruck(null);
    }
  }

  @Transactional
  public TripView dispatch(UUID id) {
    Trip trip = findTrip(id);
    trip.dispatch();
    return saveAndNotify(trip);
  }

  @Transactional
  public TripView complete(UUID id) {
    Trip trip = findTrip(id);
    trip.complete();
    return saveAndNotify(trip);
  }

  @Transactional
  public TripView cancel(UUID id) {
    Trip trip = findTrip(id);
    trip.cancel();
    return saveAndNotify(trip);
  }

  private TripView saveAndNotify(Trip trip) {
    Trip saved = tripRepository.save(trip);
    String status = saved.getStatus();
    eventPublisher.publishEvent(
        new TenantNotificationEvent(
            "Trip " + status,
            "Trip " + saved.getId() + " changed status to " + status + ".",
            "Trip",
            saved.getId()));
    return tripMapper.toView(saved);
  }

  private Trip findTrip(UUID id) {
    return tripRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
  }

  private void rebuildStops(Trip trip, CreateTripRequest request) {
    var orders = new HashSet<Integer>();
    trip.getStops().clear();
    request.stops().stream()
        .sorted(java.util.Comparator.comparingInt(stop -> stop.order()))
        .forEach(
            item -> {
              if (!orders.add(item.order())) {
                throw new BadRequestException("Trip stop order must be unique: " + item.order());
              }
              var load =
                  loadRepository
                      .findById(item.loadId())
                      .orElseThrow(
                          () ->
                              new ResourceNotFoundException("Load not found: " + item.loadId()));
              TripStop stop = new TripStop();
              stop.setTrip(trip);
              stop.setLoad(load);
              stop.setType(item.type());
              stop.setOrder(item.order());
              stop.setAddressLine1(item.addressLine1());
              stop.setAddressLine2(item.addressLine2());
              stop.setAddressCity(item.addressCity());
              stop.setAddressState(item.addressState());
              stop.setAddressZipCode(item.addressZipCode());
              stop.setAddressCountry(item.addressCountry());
              stop.setLocationLatitude(item.locationLatitude());
              stop.setLocationLongitude(item.locationLongitude());
              trip.getStops().add(stop);
            });
  }

  private TripStatus parseStatus(String status) {
    try {
      return TripStatus.fromDbValue(status);
    } catch (IllegalArgumentException exception) {
      throw new InvalidStateTransitionException("Trip", "unknown", status);
    }
  }
}
