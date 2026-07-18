package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.invoice.CreateInvoiceRequest;
import com.company.logicstic.dto.invoice.InvoiceView;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.CustomerRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final LoadRepository loadRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          EmployeeRepository employeeRepository,
                          LoadRepository loadRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.employeeRepository = employeeRepository;
        this.loadRepository = loadRepository;
    }

    public PagedResponse<InvoiceView> search(String status, String type, UUID customerId, UUID employeeId,
                                              int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(invoiceRepository.search(status, type, customerId, employeeId, pageable)
                .map(InvoiceView::from));
    }

    public InvoiceView getById(UUID id) {
        return invoiceRepository.findById(id)
                .map(InvoiceView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
    }

    @Transactional
    public InvoiceView create(CreateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        applyFields(invoice, request);
        return InvoiceView.from(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceView update(UUID id, CreateInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        applyFields(invoice, request);
        return InvoiceView.from(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(UUID id) {
        if (!invoiceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Invoice not found: " + id);
        }
        invoiceRepository.deleteById(id);
    }

    private void applyFields(Invoice invoice, CreateInvoiceRequest req) {
        invoice.setType(req.type());
        invoice.setStatus(req.status());
        invoice.setTaxBehavior(req.taxBehavior() != null ? req.taxBehavior() : "exclusive");
        invoice.setNotes(req.notes());
        invoice.setDueDate(req.dueDate());
        invoice.setSubtotalAmount(req.subtotalAmount());
        invoice.setSubtotalCurrency(req.subtotalCurrency());
        invoice.setTaxTotalAmount(req.taxTotalAmount());
        invoice.setTaxTotalCurrency(req.taxTotalCurrency());
        invoice.setTotalAmount(req.totalAmount());
        invoice.setTotalCurrency(req.totalCurrency());
        invoice.setPeriodStart(req.periodStart());
        invoice.setPeriodEnd(req.periodEnd());
        invoice.setTotalDistanceDriven(req.totalDistanceDriven());

        if (req.loadId() != null) {
            invoice.setLoad(loadRepository.findById(req.loadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + req.loadId())));
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