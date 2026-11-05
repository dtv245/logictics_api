package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.dto.load.UpdateLoadRequest;
import com.company.logicstic.common.ExpectedVersionGuard;
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
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public LoadService(LoadRepository loadRepository,
                       CustomerRepository customerRepository,
                       TruckRepository truckRepository,
                       EmployeeRepository employeeRepository,
                       LoadMapper loadMapper,
                       com.company.logicstic.service.rating.LoadPickupBusinessDateService pickupDates) {
        this(loadRepository, customerRepository, truckRepository, employeeRepository, loadMapper, pickupDates, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LoadService(LoadRepository loadRepository,
                       CustomerRepository customerRepository,
                       TruckRepository truckRepository,
                       EmployeeRepository employeeRepository,
                       LoadMapper loadMapper,
                       com.company.logicstic.service.rating.LoadPickupBusinessDateService pickupDates,
                       org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.loadRepository = loadRepository;
        this.customerRepository = customerRepository;
        this.truckRepository = truckRepository;
        this.employeeRepository = employeeRepository;
        this.loadMapper = loadMapper;
        this.pickupDates = pickupDates;
        this.eventPublisher = eventPublisher;
    }

    public PagedResponse<LoadView> search(String search, String status, UUID customerId,
                                           UUID truckId, UUID dispatcherId,
                                           int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = com.company.logicstic.common.QuerySortPolicy.sort("loads", orderBy, descending);
        var pageable = com.company.logicstic.common.PaginationValidator.pageRequest(page, pageSize, sort);
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
        LoadRequestValidator.validate(request);
        Load load = loadMapper.toEntity(request);
        resolveRelations(load, request);
        // With a non-null version Spring Data may merge a new entity. Continue
        // with the managed result so the generated ID is available to its audit.
        load = loadRepository.saveAndFlush(load);
        pickupDates.capture(load, request.requestedPickupBusinessDate(), request.requestedPickupDateProvenance());
        Load saved = loadRepository.saveAndFlush(load);
        publishLoadChanged(saved.getId(), java.util.Set.of("status", "origin", "destination", "dates", "weight"));
        return loadMapper.toView(saved);
    }

    @Transactional
    public LoadView update(UUID id, UpdateLoadRequest command) {
        Load load = loadRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
        ExpectedVersionGuard.require(command == null ? null : command.expectedVersion(), load.getVersion());
        CreateLoadRequest request = command.toCreateRequest();
        LoadRequestValidator.validate(request);
        loadMapper.updateEntity(request, load);
        resolveRelations(load, request);
        pickupDates.capture(load, request.requestedPickupBusinessDate(), request.requestedPickupDateProvenance());
        Load saved = loadRepository.saveAndFlush(load);
        publishLoadChanged(saved.getId(), java.util.Set.of("status", "origin", "destination", "dates", "weight", "assignedTruckId"));
        return loadMapper.toView(saved);
    }

    @Transactional
    public LoadView assignTruck(UUID id, UUID truckId) {
        Load load = loadRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
        Truck truck = truckRepository.findById(truckId)
                .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + truckId));
        load.setAssignedTruck(truck);
        load.setStatus("ASSIGNED");
        Load saved = loadRepository.saveAndFlush(load);
        publishLoadChanged(saved.getId(), java.util.Set.of("assignedTruckId", "status"));
        return loadMapper.toView(saved);
    }

    private void publishLoadChanged(UUID loadId, java.util.Set<String> fields) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new com.company.logicstic.ai.event.LoadChangedInternalEvent(
                    loadId,
                    com.company.logicstic.config.TenantContext.getTenantId().orElse("default"),
                    "TMS",
                    fields,
                    java.time.Instant.now()
            ));
        }
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
