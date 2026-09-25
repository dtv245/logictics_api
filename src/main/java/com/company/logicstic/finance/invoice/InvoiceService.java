package com.company.logicstic.finance.invoice;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;
import java.util.UUID;

/**
 * Public API of the invoice feature.
 *
 * <p>{@link Invoice} is mapped table-per-hierarchy over the three invoice types (load, payroll,
 * subscription) described in {@code docs/docs/invoices.md}; the {@code type} filter of {@link
 * #search} selects between them.
 */
public interface InvoiceService
    extends CrudService<Invoice, InvoiceResponse, CreateInvoiceRequest> {

  /**
   * Searches invoices with optional status, type and counterparty filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<InvoiceResponse> search(
      String status,
      String type,
      UUID customerId,
      UUID employeeId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);
}
