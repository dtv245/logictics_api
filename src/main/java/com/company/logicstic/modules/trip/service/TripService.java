package com.company.logicstic.modules.trip.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.trip.dto.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.TripView;
import com.company.logicstic.modules.trip.entity.Trip;
import com.company.logicstic.modules.trip.mapper.TripMapper;
import com.company.logicstic.modules.trip.repository.TripRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class TripService extends AbstractBaseService<Trip, TripView, CreateTripRequest> {

    private final TripRepository tripRepository;
    private final TruckRepository truckRepository;
    private final TripMapper tripMapper;

    public TripService(TripRepository tripRepository, TruckRepository truckRepository, TripMapper tripMapper) {
        super(tripRepository, tripMapper::toView, tripMapper::toEntity, tripMapper::updateEntity);
        this.tripRepository = tripRepository;
        this.truckRepository = truckRepository;
        this.tripMapper = tripMapper;
    }

    @Override
    protected String entityName() {
        return "Trip";
    }

    public PagedResponse<TripView> search(String search, String status, UUID truckId,
                                           int page, int pageSize, String orderBy, boolean descending) {
        var pageable = pageRequest(page, pageSize, orderBy, descending);
        return toPagedResponse(tripRepository.search(search, status, truckId, pageable));
    }

    @Override
    protected void beforeCreate(Trip trip, CreateTripRequest request) {
        resolveRelations(trip, request);
    }

    @Override
    protected void beforeUpdate(Trip trip, CreateTripRequest request) {
        resolveRelations(trip, request);
    }

    private void resolveRelations(Trip trip, CreateTripRequest req) {
        if (req.truckId() != null) {
            trip.setTruck(truckRepository.findById(req.truckId())
                    .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + req.truckId())));
        } else {
            trip.setTruck(null);
        }
    }
}