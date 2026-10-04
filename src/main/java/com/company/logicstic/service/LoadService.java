package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.dto.load.LoadView;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.LoadMapper;
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
    private final LoadMapper loadMapper;
    private final com.company.logicstic.service.rating.LoadPickupBusinessDateService pickupDates;

    public LoadService(LoadRepository loadRepository,
                       CustomerRepository customerRepository,
                       TruckRepository truckRepository,
                       EmployeeRepository employeeRepository,
                       LoadMapper loadMapper,
                       com.company.logicstic.service.rating.LoadPickupBusinessDateService pickupDates) {
        this.loadRepository = loadRepository;
        this.customerRepository = customerRepository;
        this.truckRepository = truckRepository;
        this.employeeRepository = employeeRepository;
        this.loadMapper = loadMapper;
        this.pickupDates = pickupDates;
    }

    public PagedResponse<LoadView> search(String search, String status, UUID customerId,
                                           UUID truckId, UUID dispatcherId,
                                           int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(
                loadRepository.search(search, status, customerId, truckId, dispatcherId, pageable)
                        .map(loadMapper::toView)
        );
    }

    public LoadView getById(UUID id) {
        return loadRepository.findById(id)
                .map(loadMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    }

    @Transactional
    public LoadView create(CreateLoadRequest request) {
        Load load = loadMapper.toEntity(request);
        resolveRelations(load, request);
        loadRepository.saveAndFlush(load);
        pickupDates.capture(load, request.requestedPickupBusinessDate(), request.requestedPickupDateProvenance());
        return loadMapper.toView(loadRepository.saveAndFlush(load));
    }

    @Transactional
    public LoadView update(UUID id, CreateLoadRequest request) {
        Load load = loadRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
        loadMapper.updateEntity(request, load);
        resolveRelations(load, request);
        pickupDates.capture(load, request.requestedPickupBusinessDate(), request.requestedPickupDateProvenance());
        return loadMapper.toView(loadRepository.saveAndFlush(load));
    }

    @Transactional
    public void delete(UUID id) {
        loadRepository.findByIdForUpdate(id).filter(l -> l.getPickupBusinessDateChangeId() != null).ifPresent(l -> {
            throw new com.company.logicstic.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "LOAD_PICKUP_DATE_HISTORY_PROTECTED", "Audited pickup-date history must be preserved");
        });
        if (!loadRepository.existsById(id)) {
            throw new ResourceNotFoundException("Load not found: " + id);
        }
        loadRepository.deleteById(id);
    }

    private void resolveRelations(Load load, CreateLoadRequest req) {
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
