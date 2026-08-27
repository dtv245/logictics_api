package com.company.logicstic.modules.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InvoiceDispatchTransitionTest {

  @Test
  void canonicalDraftTransitionsToCanonicalIssued() {
    Invoice invoice = invoiceWithStatus("draft");

    assertThat(invoice.transitionToIssuedOnLoadDispatch()).isTrue();
    assertThat(invoice.getStatus()).isEqualTo("issued");
  }

  @Test
  void legacyDraftCasingTransitionsToCanonicalIssued() {
    Invoice invoice = invoiceWithStatus(" Draft ");

    assertThat(invoice.transitionToIssuedOnLoadDispatch()).isTrue();
    assertThat(invoice.getStatus()).isEqualTo("issued");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"issued", "partially_paid", "paid", "cancelled", "unexpected"})
  void nonDraftStatusIsUnchanged(String status) {
    Invoice invoice = invoiceWithStatus(status);

    assertThat(invoice.transitionToIssuedOnLoadDispatch()).isFalse();
    assertThat(invoice.getStatus()).isEqualTo(status);
  }

  @Test
  void replayAfterIssueIsNoOp() {
    Invoice invoice = invoiceWithStatus("draft");

    assertThat(invoice.transitionToIssuedOnLoadDispatch()).isTrue();
    assertThat(invoice.transitionToIssuedOnLoadDispatch()).isFalse();
    assertThat(invoice.getStatus()).isEqualTo("issued");
  }

  private static Invoice invoiceWithStatus(String status) {
    Invoice invoice = new Invoice();
    invoice.setStatus(status);
    return invoice;
  }
}
