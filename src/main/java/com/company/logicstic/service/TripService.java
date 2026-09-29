package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.trip.CreateTripRequest;
import com.company.logicstic.dto.trip.TripView;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TruckRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TripService {

    private final TripRepository tripRepository;
    private final TruckRepository truckRepository;

    public TripService(TripRepository tripRepository, TruckRepository truckRepository) {
        this.tripRepository = tripRepository;
        this.truckRepository = truckRepository;
    }

    public PagedResponse<TripView> search(String search, String status, UUID truckId,
                                           int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(tripRepository.search(search, status, truckId, pageable).map(TripView::from));
    }

    public TripView getById(UUID id) {
        return tripRepository.findById(id)
                .map(TripView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
    }

    @Transactional
    public TripView create(CreateTripRequest request) {
        Trip trip = new Trip();
        applyFields(trip, request);
        return TripView.from(tripRepository.save(trip));
    }

    @Transactional
    public TripView update(UUID id, CreateTripRequest request) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
        applyFields(trip, request);
        return TripView.from(tripRepository.save(trip));
    }

    @Transactional
    public void delete(UUID id) {
        if (!tripRepository.existsById(id)) {
            throw new ResourceNotFoundException("Trip not found: " + id);
        }
        tripRepository.deleteById(id);
    }

    private void applyFields(Trip trip, CreateTripRequest req) {
        trip.setName(req.name());
        trip.setTotalDistance(req.totalDistance());
        trip.setStatus(req.status());

        if (req.truckId() != null) {
            trip.setTruck(truckRepository.findById(req.truckId())
                    .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + req.truckId())));
        } else {
            trip.setTruck(null);
        }
    }
}
