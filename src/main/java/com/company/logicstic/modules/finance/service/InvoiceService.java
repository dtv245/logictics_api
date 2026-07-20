package com.company.logicstic.modules.finance.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.customer.repository.CustomerRepository;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.finance.dto.CreateInvoiceRequest;
import com.company.logicstic.modules.finance.dto.InvoiceView;
import com.company.logicstic.modules.finance.entity.Invoice;
import com.company.logicstic.modules.finance.mapper.InvoiceMapper;
import com.company.logicstic.modules.finance.repository.InvoiceRepository;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class InvoiceService extends AbstractBaseService<Invoice, InvoiceView, CreateInvoiceRequest> {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final LoadRepository loadRepository;
    private final InvoiceMapper invoiceMapper;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          EmployeeRepository employeeRepository,
                          LoadRepository loadRepository,
                          InvoiceMapper invoiceMapper) {
        super(invoiceRepository, invoiceMapper::toView, invoiceMapper::toEntity, invoiceMapper::updateEntity);
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.employeeRepository = employeeRepository;
        this.loadRepository = loadRepository;
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    protected String entityName() {
        return "Invoice";
    }

    public PagedResponse<InvoiceView> search(String status, String type, UUID customerId, UUID employeeId,
                                              int page, int pageSize, String orderBy, boolean descending) {
        var pageable = pageRequest(page, pageSize, orderBy, descending);
        return toPagedResponse(invoiceRepository.search(status, type, customerId, employeeId, pageable));
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
            Load load = loadRepository.findById(req.loadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + req.loadId()));
            invoice.setLoad(load);
        } else {
            invoice.setLoad(null);
        }

        if (req.customerId() != null) {
            invoice.setCustomer(customerRepository.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.customerId())));
        } else {
            invoice.setCustomer(null);
        }

        if (req.employeeId() != null) {
            invoice.setEmployee(employeeRepository.findById(req.employeeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.employeeId())));
        } else {
            invoice.setEmployee(null);
        }
    }
}