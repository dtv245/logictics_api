package com.company.logicstic.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.common.enums.InvoiceStatus;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.invoice.CreateInvoiceRequest;
import com.company.logicstic.dto.invoice.InvoiceView;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.InvoiceMapper;
import com.company.logicstic.repository.CustomerRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    /** Canonical statuses accepted from the legacy CRUD surface. */
    private static final Set<String> ALLOWED_STATUSES = java.util.Arrays.stream(InvoiceStatus.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final LoadRepository loadRepository;
    private final InvoiceMapper invoiceMapper;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CustomerRepository customerRepository,
                          LoadRepository loadRepository,
                          InvoiceMapper invoiceMapper) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.loadRepository = loadRepository;
        this.invoiceMapper = invoiceMapper;
    }

    public PagedResponse<InvoiceView> search(String status, String type, UUID customerId,
                                              String invoicePurpose, UUID billingChainId, UUID ratingSnapshotId,
                                              UUID employeeId,
                                              int page, int pageSize, String orderBy, boolean descending) {
        if (employeeId != null) {
            throw new BadRequestException("INVOICE_LEGACY_PAYROLL_FIELD", "Filtering invoices by deprecated employee payroll field is no longer supported");
        }
        Sort sort = com.company.logicstic.common.QuerySortPolicy.sort("invoices", orderBy, descending);
        var pageable = com.company.logicstic.common.PaginationValidator.pageRequest(page, pageSize, sort);
        return PagedResponse.from(invoiceRepository.search(status, type, customerId,
                        invoicePurpose, billingChainId, ratingSnapshotId, pageable)
                .map(invoiceMapper::toView));
    }

    public InvoiceView getById(UUID id) {
        return invoiceRepository.findById(id)
                .map(invoiceMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
    }

    @Transactional
    public InvoiceView create(CreateInvoiceRequest request) {
        rejectLegacyPayrollFields(request);
        requireValidStatus(request);
        Invoice invoice = invoiceMapper.toEntity(request);
        resolveRelations(invoice, request);
        return invoiceMapper.toView(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceView update(UUID id, CreateInvoiceRequest request) {
        rejectLegacyPayrollFields(request);
        requireValidStatus(request);
        Invoice invoice = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        requireEditable(invoice);
        invoiceMapper.updateEntity(request, invoice);
        resolveRelations(invoice, request);
        return invoiceMapper.toView(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(UUID id) {
        requireEditable(invoiceRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id)));
        invoiceRepository.deleteById(id);
    }

    private void resolveRelations(Invoice invoice, CreateInvoiceRequest req) {
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

    }

    private void requireEditable(Invoice invoice) {
        if(invoice.getInvoicePurpose()!=null || com.company.logicstic.common.enums.InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue())
            throw new com.company.logicstic.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "INVOICE_HISTORY_IMMUTABLE","Rated documents require billing commands; issued financial history cannot be edited/deleted");
    }

    private void rejectLegacyPayrollFields(CreateInvoiceRequest request) {
        if (request == null || request.hasLegacyPayrollFields()) {
            throw new BadRequestException("INVOICE_LEGACY_PAYROLL_FIELD", "Invoice employee, payroll period and distance fields are deprecated and cannot be supplied");
        }
    }

    /**
     * Legacy CRUD must not persist a status the billing guard cannot interpret.
     * {@link InvoiceStatus#fromString} falls back to DRAFT for unknown values, which would
     * silently make an issued invoice editable again, so unknown statuses are rejected here
     * instead of being stored and misread later.
     */
    private void requireValidStatus(CreateInvoiceRequest request) {
        String status = request.status();
        if (status == null || !ALLOWED_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
            throw new BadRequestException("INVOICE_STATUS_INVALID",
                    "Invoice status must be one of " + ALLOWED_STATUSES);
        }
    }
}
