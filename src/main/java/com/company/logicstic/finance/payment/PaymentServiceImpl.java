package com.company.logicstic.finance.payment;

import com.company.logicstic.finance.invoice.InvoiceRepository;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import com.company.logicstic.shared.persistence.AbstractBaseService;
import com.company.logicstic.shared.web.PagedResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl
    extends AbstractBaseService<Payment, PaymentResponse, CreatePaymentRequest>
    implements PaymentService {

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentMapper paymentMapper;

  public PaymentServiceImpl(
      PaymentRepository paymentRepository,
      InvoiceRepository invoiceRepository,
      PaymentMapper paymentMapper) {
    super(
        paymentRepository,
        paymentMapper::toResponse,
        paymentMapper::toEntity,
        paymentMapper::updateEntity);
    this.paymentRepository = paymentRepository;
    this.invoiceRepository = invoiceRepository;
    this.paymentMapper = paymentMapper;
  }

  @Override
  protected String entityName() {
    return "Payment";
  }

  public PagedResponse<PaymentResponse> search(
      String status, UUID invoiceId, int page, int pageSize, String orderBy, boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(paymentRepository.search(status, invoiceId, pageable));
  }

  @Override
  protected void beforeCreate(Payment payment, CreatePaymentRequest request) {
    resolveRelations(payment, request);
  }

  @Override
  protected void beforeUpdate(Payment payment, CreatePaymentRequest request) {
    resolveRelations(payment, request);
  }

  private void resolveRelations(Payment payment, CreatePaymentRequest req) {
    if (req.invoiceId() != null) {
      payment.setInvoice(
          invoiceRepository
              .findById(req.invoiceId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Invoice not found: " + req.invoiceId())));
    } else {
      payment.setInvoice(null);
    }
  }
}
