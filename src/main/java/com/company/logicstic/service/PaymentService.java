package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.payment.CreatePaymentRequest;
import com.company.logicstic.dto.payment.PaymentView;
import com.company.logicstic.entity.Invoice;
import com.company.logicstic.entity.Payment;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.PaymentMapper;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.PaymentRepository;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository, PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentMapper = paymentMapper;
    }

    public PagedResponse<PaymentView> search(String status, UUID invoiceId,
                                              int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(paymentRepository.search(status, invoiceId, pageable).map(paymentMapper::toView));
    }

    public PaymentView getById(UUID id) {
        return paymentRepository.findById(id)
                .map(paymentMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    @Transactional
    public PaymentView create(CreatePaymentRequest request) {
        Payment payment = paymentMapper.toEntity(request);
        resolveRelations(payment, request);
        return paymentMapper.toView(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentView update(UUID id, CreatePaymentRequest request) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
        paymentMapper.updateEntity(request, payment);
        resolveRelations(payment, request);
        return paymentMapper.toView(paymentRepository.save(payment));
    }

    @Transactional
    public void delete(UUID id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Payment not found: " + id);
        }
        paymentRepository.deleteById(id);
    }

    private void resolveRelations(Payment payment, CreatePaymentRequest req) {
        if (req.invoiceId() != null) {
            Invoice invoice = invoiceRepository.findById(req.invoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + req.invoiceId()));
            payment.setInvoice(invoice);
        } else {
            payment.setInvoice(null);
        }
    }
}