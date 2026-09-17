package com.company.logicstic.finance.payment;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;
import java.util.UUID;

/** Public API of the payment feature — amounts received against an invoice. */
public interface PaymentService
    extends CrudService<Payment, PaymentResponse, CreatePaymentRequest> {

  /**
   * Searches payments with optional status and invoice filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<PaymentResponse> search(
      String status, UUID invoiceId, int page, int pageSize, String orderBy, boolean descending);
}
