package com.company.logicstic.modules.finance.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.finance.dto.CreatePaymentRequest;
import com.company.logicstic.modules.finance.dto.PaymentView;
import com.company.logicstic.modules.finance.entity.Payment;
import com.company.logicstic.modules.finance.mapper.PaymentMapper;
import com.company.logicstic.modules.finance.repository.InvoiceRepository;
import com.company.logicstic.modules.finance.repository.PaymentRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class PaymentService extends AbstractBaseService<Payment, PaymentView, CreatePaymentRequest> {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                          PaymentMapper paymentMapper) {
        super(paymentRepository, paymentMapper::toView, paymentMapper::toEntity, paymentMapper::updateEntity);
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentMapper = paymentMapper;
    }

    @Override
    protected String entityName() {
        return "Payment";
    }

    public PagedResponse<PaymentView> search(String status, UUID invoiceId,
                                              int page, int pageSize, String orderBy, boolean descending) {
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
            payment.setInvoice(invoiceRepository.findById(req.invoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + req.invoiceId())));
        } else {
            payment.setInvoice(null);
        }
    }
}