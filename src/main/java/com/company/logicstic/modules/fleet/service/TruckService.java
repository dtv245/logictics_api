package com.company.logicstic.modules.fleet.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.fleet.dto.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.TruckView;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.fleet.mapper.TruckMapper;
import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class TruckService extends AbstractBaseService<Truck, TruckView, CreateTruckRequest> {

    private final TruckRepository truckRepository;
    private final EmployeeRepository employeeRepository;
    private final TruckMapper truckMapper;

    public TruckService(TruckRepository truckRepository, EmployeeRepository employeeRepository,
                        TruckMapper truckMapper) {
        super(truckRepository, truckMapper::toView, truckMapper::toEntity, truckMapper::updateEntity);
        this.truckRepository = truckRepository;
        this.employeeRepository = employeeRepository;
        this.truckMapper = truckMapper;
    }

    @Override
    protected String entityName() {
        return "Truck";
    }

    public PagedResponse<TruckView> search(String search, String status, String type,
                                            int page, int pageSize, String orderBy, boolean descending) {
        var pageable = pageRequest(page, pageSize, orderBy, descending);
        return toPagedResponse(truckRepository.search(search, status, type, pageable));
    }

    @Override
    protected void beforeCreate(Truck truck, CreateTruckRequest request) {
        if (truckRepository.existsByNumber(request.number())) {
            throw new ConflictException("Truck with number '" + request.number() + "' already exists");
        }
        resolveDrivers(truck, request);
    }

    @Override
    protected void beforeUpdate(Truck truck, CreateTruckRequest request) {
        if (!truck.getNumber().equals(request.number()) && truckRepository.existsByNumber(request.number())) {
            throw new ConflictException("Truck with number '" + request.number() + "' already exists");
        }
        resolveDrivers(truck, request);
    }

    private void resolveDrivers(Truck truck, CreateTruckRequest req) {
        if (req.mainDriverId() != null) {
            Employee driver = employeeRepository.findById(req.mainDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + req.mainDriverId()));
            truck.setMainDriver(driver);
        } else {
            truck.setMainDriver(null);
        }

        if (req.secondaryDriverId() != null) {
            Employee driver = employeeRepository.findById(req.secondaryDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + req.secondaryDriverId()));
            truck.setSecondaryDriver(driver);
        } else {
            truck.setSecondaryDriver(null);
        }
    }
}