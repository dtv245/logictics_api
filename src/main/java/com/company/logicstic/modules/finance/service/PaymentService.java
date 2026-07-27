package com.company.logicstic.modules.finance.service;

import com.company.logicstic.modules.finance.dto.request.CreatePaymentRequest;
import com.company.logicstic.modules.finance.dto.response.PaymentResponse;
import com.company.logicstic.modules.finance.entity.Payment;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;
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
