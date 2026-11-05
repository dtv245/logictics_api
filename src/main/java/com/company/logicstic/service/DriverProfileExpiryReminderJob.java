package com.company.logicstic.service;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.dto.TenantRegistryRecord;
import com.company.logicstic.repository.DriverExpiringItemRepository;
import com.company.logicstic.repository.DriverExpiringItemRepository.DriverExpiringItemRow;
import com.company.logicstic.service.TenantDataSourceService;
import com.company.logicstic.service.TenantRegistryService;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

/** Logs threshold crossings because the current notification module has no recipient/create API. */
@Slf4j
@Component
public class DriverProfileExpiryReminderJob {
    private static final List<Integer> THRESHOLDS = List.of(30, 7);
    private static final int PAGE_SIZE = 100;

    private final DriverExpiringItemRepository repository;
    private final DriverExpiryStatusCalculator statusCalculator;
    private final boolean tenancyEnabled;
    private final ObjectProvider<TenantRegistryService> tenantRegistry;
    private final ObjectProvider<TenantDataSourceService> tenantDataSources;

    public DriverProfileExpiryReminderJob(DriverExpiringItemRepository repository,
                                         DriverExpiryStatusCalculator statusCalculator,
                                         @Value("${app.tenancy.enabled:false}") boolean tenancyEnabled,
                                         ObjectProvider<TenantRegistryService> tenantRegistry,
                                         ObjectProvider<TenantDataSourceService> tenantDataSources) {
        this.repository = repository;
        this.statusCalculator = statusCalculator;
        this.tenancyEnabled = tenancyEnabled;
        this.tenantRegistry = tenantRegistry;
        this.tenantDataSources = tenantDataSources;
    }

    @Scheduled(cron = "${app.driver-profile.expiry-scan-cron:0 0 8 * * *}",
            zone = "${app.driver-profile.time-zone:Asia/Ho_Chi_Minh}")
    public void scanDaily() {
        if (!tenancyEnabled) {
            scan(repository, null);
            return;
        }
        TenantRegistryService registry = tenantRegistry.getIfAvailable();
        TenantDataSourceService dataSources = tenantDataSources.getIfAvailable();
        if (registry == null || dataSources == null) {
            log.error("Driver profile expiry scan is unavailable because tenant services are not configured");
            return;
        }
        for (TenantRegistryRecord tenant : registry.findAllActiveTenants()) {
            scanTenant(tenant, dataSources);
        }
    }

    private void scanTenant(TenantRegistryRecord tenant, TenantDataSourceService dataSources) {
        DataSource dataSource = null;
        try {
            dataSource = dataSources.buildTemporaryDataSource(tenant);
            scan(new DriverExpiringItemRepository(new NamedParameterJdbcTemplate(dataSource)), tenant.tenantId());
        } catch (RuntimeException ex) {
            // Database URLs, registry credentials, and exception messages are deliberately omitted.
            log.error("Driver profile expiry scan failed for tenantId={}, errorType={}",
                    tenant.tenantId(), ex.getClass().getSimpleName());
        } finally {
            if (dataSource instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    log.warn("Driver profile expiry scan datasource close failed for tenantId={}", tenant.tenantId());
                }
            }
        }
    }

    private void scan(DriverExpiringItemRepository target, String tenantId) {
        LocalDate today = statusCalculator.currentDate();
        for (int threshold : THRESHOLDS) {
            LocalDate expiryDate = today.plusDays(threshold);
            int page = 0;
            List<DriverExpiringItemRow> items;
            do {
                items = target.findPage(expiryDate, expiryDate, null, null,
                        statusCalculator.timeZoneId(), PageRequest.of(page++, PAGE_SIZE));
                for (DriverExpiringItemRow item : items) {
                    log.info("Driver compliance expiry threshold reached: tenantId={}, type={}, driverId={}, itemId={}, daysUntilExpiry={}",
                            tenantId, item.type(), item.driverId(), item.itemId(), threshold);
                }
            } while (items.size() == PAGE_SIZE);
        }
    }
}
