package com.company.logicstic.modules.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PaymentTenantMigrationTest {

  @Test
  void paymentUsesDatabaseIsolationInsteadOfLegacyTenantColumn() throws IOException {
    assertThatThrownBy(() -> Payment.class.getDeclaredField("tenantId"))
        .isInstanceOf(NoSuchFieldException.class);

    String migration =
        new String(
            getClass()
                .getResourceAsStream(
                    "/db/migration/tenant/V3__remove_redundant_payment_tenant_id.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(migration).contains("ALTER TABLE payments DROP COLUMN tenant_id;");
  }
}
