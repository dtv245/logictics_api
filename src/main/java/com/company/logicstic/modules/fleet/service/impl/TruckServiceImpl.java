package com.company.logicstic.modules.fleet.service.impl;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.fleet.dto.request.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.response.TruckResponse;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.fleet.mapper.TruckMapper;
import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.shared.common.CacheNames;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class TruckServiceImpl extends AbstractBaseService<Truck, TruckResponse, CreateTruckRequest>
    implements TruckService {

  private final TruckRepository truckRepository;
  private final EmployeeService employeeService;
  private final TruckMapper truckMapper;

  public TruckServiceImpl(
      TruckRepository truckRepository, EmployeeService employeeService, TruckMapper truckMapper) {
    super(
        truckRepository, truckMapper::toResponse, truckMapper::toEntity, truckMapper::updateEntity);
    this.truckRepository = truckRepository;
    this.employeeService = employeeService;
    this.truckMapper = truckMapper;
  }

  @Override
  protected String entityName() {
    return "Truck";
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = CacheNames.TRUCK, key = "#id")
  public TruckResponse getById(UUID id) {
    return super.getById(id);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.TRUCK, allEntries = true)
  public TruckResponse create(CreateTruckRequest request) {
    return super.create(request);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.TRUCK, allEntries = true)
  public TruckResponse update(UUID id, CreateTruckRequest request) {
    return super.update(id, request);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.TRUCK, allEntries = true)
  public void delete(UUID id) {
    super.delete(id);
  }

  public PagedResponse<TruckResponse> search(
      String search,
      String status,
      String type,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
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
  protected void beforeMapUpdate(Truck truck, CreateTruckRequest request) {
    if (!Objects.equals(truck.getNumber(), request.number())
        && truckRepository.existsByNumber(request.number())) {
      throw new ConflictException("Truck with number '" + request.number() + "' already exists");
    }
  }

  @Override
  protected void beforeUpdate(Truck truck, CreateTruckRequest request) {
    resolveDrivers(truck, request);
  }

  private void resolveDrivers(Truck truck, CreateTruckRequest req) {
    if (req.mainDriverId() != null) {
      Employee driver = employeeService.getEntityById(req.mainDriverId());
      truck.setMainDriver(driver);
    } else {
      truck.setMainDriver(null);
    }

    if (req.secondaryDriverId() != null) {
      Employee driver = employeeService.getEntityById(req.secondaryDriverId());
      truck.setSecondaryDriver(driver);
    } else {
      truck.setSecondaryDriver(null);
    }
  }
}
