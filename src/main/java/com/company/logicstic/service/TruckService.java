package com.company.logicstic.service;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.truck.CreateTruckRequest;
import com.company.logicstic.dto.truck.TruckView;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TruckRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TruckService {

    private final TruckRepository truckRepository;
    private final EmployeeRepository employeeRepository;

    public TruckService(TruckRepository truckRepository, EmployeeRepository employeeRepository) {
        this.truckRepository = truckRepository;
        this.employeeRepository = employeeRepository;
    }

    public PagedResponse<TruckView> search(String search, String status, String type,
                                            int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(truckRepository.search(search, status, type, pageable).map(TruckView::from));
    }

    public TruckView getById(UUID id) {
        return truckRepository.findById(id)
                .map(TruckView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + id));
    }

    @Transactional
    public TruckView create(CreateTruckRequest request) {
        if (truckRepository.existsByNumber(request.number())) {
            throw new ConflictException("Truck with number '" + request.number() + "' already exists");
        }
        Truck truck = new Truck();
        applyFields(truck, request);
        return TruckView.from(truckRepository.save(truck));
    }

    @Transactional
    public TruckView update(UUID id, CreateTruckRequest request) {
        Truck truck = truckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + id));
        if (!truck.getNumber().equals(request.number()) && truckRepository.existsByNumber(request.number())) {
            throw new ConflictException("Truck with number '" + request.number() + "' already exists");
        }
        applyFields(truck, request);
        return TruckView.from(truckRepository.save(truck));
    }

    @Transactional
    public void delete(UUID id) {
        if (!truckRepository.existsById(id)) {
            throw new ResourceNotFoundException("Truck not found: " + id);
        }
        truckRepository.deleteById(id);
    }

    private void applyFields(Truck truck, CreateTruckRequest req) {
        truck.setNumber(req.number());
        truck.setType(req.type());
        truck.setVehicleCapacity(req.vehicleCapacity());
        truck.setStatus(req.status());
        truck.setMake(req.make());
        truck.setModel(req.model());
        truck.setYear(req.year());
        truck.setVin(req.vin());
        truck.setLicensePlate(req.licensePlate());
        truck.setLicensePlateState(req.licensePlateState());
        truck.setIsHazmatPlacarded(req.isHazmatPlacarded() != null ? req.isHazmatPlacarded() : false);
        truck.setAdrEquipmentIsAdrCertified(req.adrEquipmentIsAdrCertified() != null ? req.adrEquipmentIsAdrCertified() : false);
        truck.setAdrEquipmentAllowedClasses(req.adrEquipmentAllowedClasses() != null ? req.adrEquipmentAllowedClasses() : "");
        truck.setAdrEquipmentOrangePlateNumber(req.adrEquipmentOrangePlateNumber());

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
