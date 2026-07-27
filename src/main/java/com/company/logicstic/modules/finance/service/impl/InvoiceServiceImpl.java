package com.company.logicstic.modules.finance.service.impl;

import com.company.logicstic.modules.customer.service.CustomerService;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.finance.dto.request.CreateInvoiceRequest;
import com.company.logicstic.modules.finance.dto.response.InvoiceResponse;
import com.company.logicstic.modules.finance.entity.Invoice;
import com.company.logicstic.modules.finance.mapper.InvoiceMapper;
import com.company.logicstic.modules.finance.repository.InvoiceRepository;
import com.company.logicstic.modules.finance.service.InvoiceService;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class InvoiceServiceImpl
    extends AbstractBaseService<Invoice, InvoiceResponse, CreateInvoiceRequest>
    implements InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final CustomerService customerService;
  private final EmployeeService employeeService;
  private final LoadService loadService;
  private final InvoiceMapper invoiceMapper;

  public InvoiceServiceImpl(
      InvoiceRepository invoiceRepository,
      CustomerService customerService,
      EmployeeService employeeService,
      LoadService loadService,
      InvoiceMapper invoiceMapper) {
    super(
        invoiceRepository,
        invoiceMapper::toResponse,
        invoiceMapper::toEntity,
        invoiceMapper::updateEntity);
    this.invoiceRepository = invoiceRepository;
    this.customerService = customerService;
    this.employeeService = employeeService;
    this.loadService = loadService;
    this.invoiceMapper = invoiceMapper;
  }

  @Override
  protected String entityName() {
    return "Invoice";
  }

  public PagedResponse<InvoiceResponse> search(
      String status,
      String type,
      UUID customerId,
      UUID employeeId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(
        invoiceRepository.search(status, type, customerId, employeeId, pageable));
  }

  @Override
  protected void beforeCreate(Invoice invoice, CreateInvoiceRequest request) {
    resolveRelations(invoice, request);
  }

  @Override
  protected void beforeUpdate(Invoice invoice, CreateInvoiceRequest request) {
    resolveRelations(invoice, request);
  }

  /** Resolves FK relations that MapStruct ignores by design. */
  private void resolveRelations(Invoice invoice, CreateInvoiceRequest req) {
    if (req.loadId() != null) {
      Load load = loadService.getEntityById(req.loadId());
      invoice.setLoad(load);
    } else {
      invoice.setLoad(null);
    }

    if (req.customerId() != null) {
      invoice.setCustomer(customerService.getEntityById(req.customerId()));
    } else {
      invoice.setCustomer(null);
    }

    if (req.employeeId() != null) {
      invoice.setEmployee(employeeService.getEntityById(req.employeeId()));
    } else {
      invoice.setEmployee(null);
    }
  }
}
