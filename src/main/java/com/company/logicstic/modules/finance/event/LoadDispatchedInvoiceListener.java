package com.company.logicstic.modules.finance.event;

import com.company.logicstic.modules.finance.repository.InvoiceRepository;
import com.company.logicstic.modules.load.event.LoadDispatchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Issues the draft invoice of a load when that load is dispatched.
 *
 * <p>{@code docs/docs/business-spec.md} §2.1: "Khi Load chuyển sang Dispatched, nếu Invoice đang
 * Draft → Invoice chuyển sang Issued". The rule belongs to finance, so finance owns the code — the
 * load feature only announces the transition.
 *
 * <p>This is a plain {@code @EventListener}, not {@code @TransactionalEventListener(AFTER_COMMIT)}:
 * the flip must be part of the same unit of work as the dispatch, so a failure rolls both back. The
 * after-commit phase is for notifications, which must never fire for a rolled-back change.
 */
@Slf4j
@Profile("!nodb")
@Component
@RequiredArgsConstructor
public class LoadDispatchedInvoiceListener {

  private final InvoiceRepository invoiceRepository;

  @EventListener
  public void issueDraftInvoice(LoadDispatchedEvent event) {
    invoiceRepository
        .findByLoadIdAndStatus(event.loadId(), "Draft")
        .ifPresent(
            invoice -> {
              invoice.setStatus("Issued");
              invoiceRepository.save(invoice);
              log.info(
                  "Invoice issued on dispatch load={} invoice={}", event.loadId(), invoice.getId());
            });
  }
}
