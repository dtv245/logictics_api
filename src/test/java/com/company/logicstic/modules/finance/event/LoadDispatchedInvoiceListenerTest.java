package com.company.logicstic.modules.finance.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.company.logicstic.modules.finance.entity.Invoice;
import com.company.logicstic.modules.finance.repository.InvoiceRepository;
import com.company.logicstic.modules.load.event.LoadDispatchedEvent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadDispatchedInvoiceListenerTest {

  @Mock private InvoiceRepository invoiceRepository;

  private LoadDispatchedInvoiceListener listener;

  @BeforeEach
  void setUp() {
    listener = new LoadDispatchedInvoiceListener(invoiceRepository);
  }

  @Test
  void issuesAndSavesDraftInvoice() {
    UUID loadId = UUID.randomUUID();
    Invoice invoice = invoiceWithStatus("draft");
    given(invoiceRepository.findByLoadId(loadId)).willReturn(Optional.of(invoice));

    listener.issueDraftInvoice(new LoadDispatchedEvent(loadId));

    verify(invoiceRepository).save(invoice);
  }

  @Test
  void missingInvoiceIsNoOp() {
    UUID loadId = UUID.randomUUID();
    given(invoiceRepository.findByLoadId(loadId)).willReturn(Optional.empty());

    listener.issueDraftInvoice(new LoadDispatchedEvent(loadId));

    verify(invoiceRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void nonDraftInvoiceIsNotSaved() {
    UUID loadId = UUID.randomUUID();
    Invoice invoice = invoiceWithStatus("paid");
    given(invoiceRepository.findByLoadId(loadId)).willReturn(Optional.of(invoice));

    listener.issueDraftInvoice(new LoadDispatchedEvent(loadId));

    verify(invoiceRepository, never()).save(invoice);
  }

  @Test
  void replaySavesOnlyTheFirstTransition() {
    UUID loadId = UUID.randomUUID();
    Invoice invoice = invoiceWithStatus("Draft");
    given(invoiceRepository.findByLoadId(loadId)).willReturn(Optional.of(invoice));

    listener.issueDraftInvoice(new LoadDispatchedEvent(loadId));
    listener.issueDraftInvoice(new LoadDispatchedEvent(loadId));

    verify(invoiceRepository).save(invoice);
  }

  private static Invoice invoiceWithStatus(String status) {
    Invoice invoice = new Invoice();
    invoice.setStatus(status);
    return invoice;
  }
}
