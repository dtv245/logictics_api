package com.company.logicstic.modules.load.service.impl;

import com.company.logicstic.modules.customer.service.CustomerService;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.fleet.service.ContainerService;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.entity.LoadStatus;
import com.company.logicstic.modules.load.event.LoadDispatchedEvent;
import com.company.logicstic.modules.load.mapper.LoadMapper;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.modules.role.entity.TenantRoleClaim;
import com.company.logicstic.modules.terminal.service.TerminalService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class LoadServiceImpl extends AbstractBaseService<Load, LoadResponse, CreateLoadRequest>
    implements LoadService {

  private static final String CONFIRM_STATUS_PERMISSION = "load.confirm_status";

  private final LoadRepository loadRepository;
  private final CustomerService customerService;
  private final TruckService truckService;
  private final ContainerService containerService;
  private final TerminalService terminalService;
  private final EmployeeService employeeService;
  private final ApplicationEventPublisher eventPublisher;
  private final LoadMapper loadMapper;

  public LoadServiceImpl(
      LoadRepository loadRepository,
      CustomerService customerService,
      TruckService truckService,
      ContainerService containerService,
      TerminalService terminalService,
      EmployeeService employeeService,
      ApplicationEventPublisher eventPublisher,
      LoadMapper loadMapper) {
    super(loadRepository, loadMapper::toResponse, loadMapper::toEntity, loadMapper::updateEntity);
    this.loadRepository = loadRepository;
    this.customerService = customerService;
    this.truckService = truckService;
    this.containerService = containerService;
    this.terminalService = terminalService;
    this.employeeService = employeeService;
    this.eventPublisher = eventPublisher;
    this.loadMapper = loadMapper;
  }

  @Override
  protected String entityName() {
    return "Load";
  }

  public PagedResponse<LoadResponse> search(
      String search,
      String status,
      UUID customerId,
      UUID truckId,
      UUID dispatcherId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(
        loadRepository.search(search, status, customerId, truckId, dispatcherId, pageable));
  }

  @Override
  protected void beforeCreate(Load load, CreateLoadRequest request) {
    LoadStatus requestedStatus = parseStatus(request.status());
    if (requestedStatus != LoadStatus.DRAFT) {
      throw new InvalidStateTransitionException("Load", "null", request.status());
    }
    load.setStatusEnum(LoadStatus.DRAFT);
    resolveRelations(load, request);
  }

  @Override
  protected void beforeMapUpdate(Load load, CreateLoadRequest request) {
    LoadStatus requestedStatus = parseStatus(request.status());
    if (requestedStatus != load.getStatusEnum()) {
      throw new InvalidStateTransitionException("Load", load.getStatus(), request.status());
    }
  }

  @Override
  protected void beforeUpdate(Load load, CreateLoadRequest request) {
    resolveRelations(load, request);
  }

  /** Resolves FK relations that MapStruct ignores by design. */
  private void resolveRelations(Load load, CreateLoadRequest req) {
    load.setCustomer(customerService.getEntityById(req.customerId()));

    if (req.assignedTruckId() != null) {
      load.setAssignedTruck(truckService.getEntityById(req.assignedTruckId()));
    } else {
      load.setAssignedTruck(null);
    }

    if (req.assignedDispatcherId() != null) {
      load.setAssignedDispatcher(employeeService.getEntityById(req.assignedDispatcherId()));
    } else {
      load.setAssignedDispatcher(null);
    }

    if (req.containerId() != null) {
      load.setContainer(containerService.getEntityById(req.containerId()));
    } else {
      load.setContainer(null);
    }

    if (req.originTerminalId() != null) {
      load.setOriginTerminal(terminalService.getEntityById(req.originTerminalId()));
    } else {
      load.setOriginTerminal(null);
    }

    if (req.destinationTerminalId() != null) {
      load.setDestinationTerminal(terminalService.getEntityById(req.destinationTerminalId()));
    } else {
      load.setDestinationTerminal(null);
    }
  }

  private LoadStatus parseStatus(String status) {
    try {
      return LoadStatus.fromDbValue(status);
    } catch (IllegalArgumentException exception) {
      throw new InvalidStateTransitionException("Load", "unknown", status);
    }
  }

  // ── State machine operations ───────────────────────────────────────

  /**
   * Dispatches a Load (Draft → Dispatched). If the Load has an associated Invoice in Draft status,
   * the Invoice is flipped to Issued.
   */
  @Transactional
  public LoadResponse dispatch(UUID id) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    load.dispatch();
    LoadResponse response = loadMapper.toResponse(loadRepository.save(load));
    eventPublisher.publishEvent(new LoadDispatchedEvent(load.getId()));
    return response;
  }

  /** Marks a Load as Picked Up (Dispatched → PickedUp). */
  @Transactional
  public LoadResponse pickUp(UUID id, UUID actorEmployeeId) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    authorizeAction(load, actorEmployeeId);
    load.pickUp();
    return loadMapper.toResponse(loadRepository.save(load));
  }

  /** Marks a Load as Delivered (PickedUp → Delivered). */
  @Transactional
  public LoadResponse deliver(UUID id, UUID actorEmployeeId) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    authorizeAction(load, actorEmployeeId);
    load.deliver();
    return loadMapper.toResponse(loadRepository.save(load));
  }

  /** Cancels a Load from any non-terminal state. */
  @Transactional
  public LoadResponse cancel(UUID id) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    load.cancel();
    return loadMapper.toResponse(loadRepository.save(load));
  }

  private void authorizeAction(Load load, UUID actorEmployeeId) {
    if (actorEmployeeId == null) {
      throw new ApiException(ErrorCode.ACCESS_DENIED, "A tenant Employee is required");
    }

    var truck = load.getAssignedTruck();
    if (truck == null || (truck.getMainDriver() == null && truck.getSecondaryDriver() == null)) {
      throw new ApiException(ErrorCode.ACCESS_DENIED, "Load has no assigned driver");
    }

    Employee actor;
    try {
      actor = employeeService.getEntityById(actorEmployeeId);
    } catch (ResourceNotFoundException exception) {
      throw new ApiException(ErrorCode.ACCESS_DENIED, "Authenticated Employee is unavailable");
    }

    boolean assignedDriver =
        (truck.getMainDriver() != null && actorEmployeeId.equals(truck.getMainDriver().getId()))
            || (truck.getSecondaryDriver() != null
                && actorEmployeeId.equals(truck.getSecondaryDriver().getId()));
    boolean operatorBypass = hasConfirmStatusPermission(actor);
    if (!assignedDriver && !operatorBypass) {
      throw new ApiException(ErrorCode.ACCESS_DENIED, "Employee is not authorized for this Load");
    }
  }

  private boolean hasConfirmStatusPermission(Employee employee) {
    if (employee.getRole() == null || employee.getRole().getClaims() == null) {
      return false;
    }
    return employee.getRole().getClaims().stream().anyMatch(this::isConfirmStatusPermission);
  }

  private boolean isConfirmStatusPermission(TenantRoleClaim claim) {
    return "permission".equalsIgnoreCase(claim.getClaimType())
        && CONFIRM_STATUS_PERMISSION.equals(claim.getClaimValue());
  }
}
