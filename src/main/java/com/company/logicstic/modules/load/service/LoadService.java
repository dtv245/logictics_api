package com.company.logicstic.modules.load.service;

import com.company.logicstic.modules.customer.repository.CustomerRepository;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.finance.repository.InvoiceRepository;
import com.company.logicstic.modules.fleet.repository.ContainerRepository;
import com.company.logicstic.modules.fleet.repository.TerminalRepository;
import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.load.dto.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.LoadView;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.entity.LoadStatus;
import com.company.logicstic.modules.load.mapper.LoadMapper;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class LoadService extends AbstractBaseService<Load, LoadView, CreateLoadRequest> {

  private final LoadRepository loadRepository;
  private final CustomerRepository customerRepository;
  private final TruckRepository truckRepository;
  private final ContainerRepository containerRepository;
  private final TerminalRepository terminalRepository;
  private final EmployeeRepository employeeRepository;
  private final InvoiceRepository invoiceRepository;
  private final LoadMapper loadMapper;

  public LoadService(
      LoadRepository loadRepository,
      CustomerRepository customerRepository,
      TruckRepository truckRepository,
      ContainerRepository containerRepository,
      TerminalRepository terminalRepository,
      EmployeeRepository employeeRepository,
      InvoiceRepository invoiceRepository,
      LoadMapper loadMapper) {
    super(loadRepository, loadMapper::toView, loadMapper::toEntity, loadMapper::updateEntity);
    this.loadRepository = loadRepository;
    this.customerRepository = customerRepository;
    this.truckRepository = truckRepository;
    this.containerRepository = containerRepository;
    this.terminalRepository = terminalRepository;
    this.employeeRepository = employeeRepository;
    this.invoiceRepository = invoiceRepository;
    this.loadMapper = loadMapper;
  }

  @Override
  protected String entityName() {
    return "Load";
  }

  public PagedResponse<LoadView> search(
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
    load.setCustomer(
        customerRepository
            .findById(req.customerId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Customer not found: " + req.customerId())));

    if (req.assignedTruckId() != null) {
      load.setAssignedTruck(
          truckRepository
              .findById(req.assignedTruckId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("Truck not found: " + req.assignedTruckId())));
    } else {
      load.setAssignedTruck(null);
    }

    if (req.assignedDispatcherId() != null) {
      load.setAssignedDispatcher(
          employeeRepository
              .findById(req.assignedDispatcherId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Employee not found: " + req.assignedDispatcherId())));
    } else {
      load.setAssignedDispatcher(null);
    }

    if (req.containerId() != null) {
      load.setContainer(
          containerRepository
              .findById(req.containerId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Container not found: " + req.containerId())));
    } else {
      load.setContainer(null);
    }

    if (req.originTerminalId() != null) {
      load.setOriginTerminal(
          terminalRepository
              .findById(req.originTerminalId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Origin terminal not found: " + req.originTerminalId())));
    } else {
      load.setOriginTerminal(null);
    }

    if (req.destinationTerminalId() != null) {
      load.setDestinationTerminal(
          terminalRepository
              .findById(req.destinationTerminalId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Destination terminal not found: " + req.destinationTerminalId())));
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
  public LoadView dispatch(UUID id) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    load.dispatch();
    autoFlipInvoice(load);
    return loadMapper.toView(loadRepository.save(load));
  }

  /** Marks a Load as Picked Up (Dispatched → PickedUp). */
  @Transactional
  public LoadView pickUp(UUID id) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    load.pickUp();
    return loadMapper.toView(loadRepository.save(load));
  }

  /** Marks a Load as Delivered (PickedUp → Delivered). */
  @Transactional
  public LoadView deliver(UUID id) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    load.deliver();
    return loadMapper.toView(loadRepository.save(load));
  }

  /** Cancels a Load from any non-terminal state. */
  @Transactional
  public LoadView cancel(UUID id) {
    Load load =
        loadRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + id));
    load.cancel();
    return loadMapper.toView(loadRepository.save(load));
  }

  /**
   * Business spec: "Khi Load chuyển sang Dispatched, nếu Invoice đang Draft → Invoice chuyển sang
   * Issued". Invoice is linked via {@code Invoice.load.id == load.id} (OneToOne).
   */
  private void autoFlipInvoice(Load load) {
    invoiceRepository
        .findByLoadIdAndStatus(load.getId(), "Draft")
        .ifPresent(
            invoice -> {
              invoice.setStatus("Issued");
              invoiceRepository.save(invoice);
            });
  }
}
