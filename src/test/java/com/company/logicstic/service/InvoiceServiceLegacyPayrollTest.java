package com.company.logicstic.service;

import com.company.logicstic.dto.invoice.CreateInvoiceRequest;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.mapper.InvoiceMapper;
import com.company.logicstic.repository.CustomerRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceLegacyPayrollTest {
    @Mock InvoiceRepository invoices;
    @Mock CustomerRepository customers;
    @Mock LoadRepository loads;
    @Mock InvoiceMapper mapper;
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoices, customers, loads, mapper);
    }

    @Test
    void createRejectsLegacyPayrollFieldsBeforePersistence() {
        var error = assertThrows(BadRequestException.class, () -> service.create(request(UUID.randomUUID(), null, null, null)));
        assertEquals("INVOICE_LEGACY_PAYROLL_FIELD", error.getCode());
        verifyNoInteractions(invoices, customers, loads, mapper);
    }

    @Test
    void updateRejectsLegacyPayrollFieldsBeforeLoadingInvoice() {
        var error = assertThrows(BadRequestException.class, () -> service.update(UUID.randomUUID(), request(null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), null, null)));
        assertEquals("INVOICE_LEGACY_PAYROLL_FIELD", error.getCode());
        verifyNoInteractions(invoices, customers, loads, mapper);
    }

    @Test
    void employeeLegacySearchFilterIsRejectedInsteadOfSilentlyIgnored() {
        var error = assertThrows(BadRequestException.class, () -> service.search(null, null, null,
                UUID.randomUUID(), 1, 20, "number", true));
        assertEquals("INVOICE_LEGACY_PAYROLL_FIELD", error.getCode());
        verifyNoInteractions(invoices, customers, loads, mapper);
    }

    private CreateInvoiceRequest request(UUID employeeId, OffsetDateTime periodStart,
                                         OffsetDateTime periodEnd, Double distance) {
        return new CreateInvoiceRequest("CUSTOMER", "DRAFT", null, null, null, null, null, employeeId,
                BigDecimal.ZERO, "USD", BigDecimal.ZERO, "USD", BigDecimal.ZERO, "USD",
                periodStart, periodEnd, distance);
    }
}
