package com.company.logicstic.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.company.logicstic.repository.DriverExpiringItemRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

class DriverProfileExpiryReminderJobTest {
    @Test
    void scansOnlyThresholdDatesForSingleDatabaseMode() {
        var repository = mock(DriverExpiringItemRepository.class);
        when(repository.findPage(any(), any(), isNull(), isNull(), anyString(), any(Pageable.class))).thenReturn(List.of());
        var calculator = new DriverExpiryStatusCalculator(30, "Asia/Ho_Chi_Minh") {
            @Override public LocalDate currentDate() { return LocalDate.of(2026, 10, 30); }
        };
        @SuppressWarnings("unchecked") ObjectProvider<com.company.logicstic.service.TenantRegistryService> registry = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<com.company.logicstic.service.TenantDataSourceService> tenants = mock(ObjectProvider.class);
        var job = new DriverProfileExpiryReminderJob(repository, calculator, false, registry, tenants);

        job.scanDaily();

        verify(repository).findPage(eq(LocalDate.of(2026, 11, 29)), eq(LocalDate.of(2026, 11, 29)),
                isNull(), isNull(), eq("Asia/Ho_Chi_Minh"), any(Pageable.class));
        verify(repository).findPage(eq(LocalDate.of(2026, 11, 6)), eq(LocalDate.of(2026, 11, 6)),
                isNull(), isNull(), eq("Asia/Ho_Chi_Minh"), any(Pageable.class));
        verifyNoInteractions(registry, tenants);
    }
}
