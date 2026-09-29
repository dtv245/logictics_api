package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.dto.load.LoadView;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LoadService {

    private final LoadRepository loadRepository;
    private final CustomerRepository customerRepository;
    private final TruckRepository truckRepository;
    private final EmployeeRepository employeeRepository;

    public LoadService(LoadRepository loadRepository,
                       CustomerRepository customerRepository,
                       TruckRepository truckRepository,
                       EmployeeRepository employeeRepository) {
        this.loadRepository = loadRepository;
        this.customerRepository = customerRepository;
        this.truckRepository = truckRepository;
        this.employeeRepository = employeeRepository;
    }

    public PagedResponse<LoadView> search(String search, String status, UUID customerId,
                                           UUID truckId, UUID dispatcherId,
                                           int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(
                loadRepository.search(search, status, customerId, truckId, dispatcherId, pageable)
                        .map(LoadView::from)
        );
    }

    public LoadView getById(UUID id) {
        return loadRepository.findById(id)
                .map(LoadView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    }

    @Transactional
    public LoadView create(CreateLoadRequest request) {
        Load load = new Load();
        applyFields(load, request);
        return LoadView.from(loadRepository.save(load));
    }

    @Transactional
    public LoadView update(UUID id, CreateLoadRequest request) {
        Load load = loadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
        applyFields(load, request);
        return LoadView.from(loadRepository.save(load));
    }

    @Transactional
    public void delete(UUID id) {
        if (!loadRepository.existsById(id)) {
            throw new ResourceNotFoundException("Load not found: " + id);
        }
        loadRepository.deleteById(id);
    }

    private void applyFields(Load load, CreateLoadRequest req) {
        load.setName(req.name());
        load.setType(req.type());
        load.setStatus(req.status());
        load.setDistance(req.distance());
        load.setIsInProximity(req.isInProximity() != null ? req.isInProximity() : false);
        load.setSource(req.source());
        load.setRequestedPickupDate(req.requestedPickupDate());
        load.setRequestedDeliveryDate(req.requestedDeliveryDate());
        load.setNotes(req.notes());
        load.setIsHazmat(req.isHazmat() != null ? req.isHazmat() : false);
        load.setHazmatClass(req.hazmatClass());
        load.setUnNumber(req.unNumber());
        load.setExternalSourceProvider(req.externalSourceProvider());
        load.setExternalSourceId(req.externalSourceId());
        load.setExternalBrokerReference(req.externalBrokerReference());
        load.setDeliveryCostAmount(req.deliveryCostAmount());
        load.setDeliveryCostCurrency(req.deliveryCostCurrency());
        // Origin
        load.setOriginAddressLine1(req.originAddressLine1());
        load.setOriginAddressLine2(req.originAddressLine2());
        load.setOriginAddressCity(req.originAddressCity());
        load.setOriginAddressState(req.originAddressState());
        load.setOriginAddressZipCode(req.originAddressZipCode());
        load.setOriginAddressCountry(req.originAddressCountry());
        load.setOriginLocationLatitude(req.originLocationLatitude());
        load.setOriginLocationLongitude(req.originLocationLongitude());
        // Destination
        load.setDestinationAddressLine1(req.destinationAddressLine1());
        load.setDestinationAddressLine2(req.destinationAddressLine2());
        load.setDestinationAddressCity(req.destinationAddressCity());
        load.setDestinationAddressState(req.destinationAddressState());
        load.setDestinationAddressZipCode(req.destinationAddressZipCode());
        load.setDestinationAddressCountry(req.destinationAddressCountry());
        load.setDestinationLocationLatitude(req.destinationLocationLatitude());
        load.setDestinationLocationLongitude(req.destinationLocationLongitude());

        // Relations
        load.setCustomer(customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.customerId())));

        if (req.assignedTruckId() != null) {
            load.setAssignedTruck(truckRepository.findById(req.assignedTruckId())
                    .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + req.assignedTruckId())));
        } else {
            load.setAssignedTruck(null);
        }

        if (req.assignedDispatcherId() != null) {
            load.setAssignedDispatcher(employeeRepository.findById(req.assignedDispatcherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.assignedDispatcherId())));
        } else {
            load.setAssignedDispatcher(null);
        }
    }
}
