package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.dto.report.MaintenanceSummaryReport;
import com.company.logicstic.entity.MaintenanceRecord;
import com.company.logicstic.repository.MaintenanceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaintenanceReportService {
    private final MaintenanceRecordRepository maintenanceRecordRepository;

    @Transactional(readOnly = true)
    public MaintenanceSummaryReport calculate(OffsetDateTime from, OffsetDateTime to, UUID truckId, String reportCurrency) {
        List<MaintenanceRecord> records = maintenanceRecordRepository.findByFilters(truckId, from, to);
        BigDecimal labor = BigDecimal.ZERO;
        BigDecimal parts = BigDecimal.ZERO;
        for (MaintenanceRecord record : records) {
            labor = labor.add(record.getLaborCost() == null ? BigDecimal.ZERO : record.getLaborCost());
            parts = parts.add(record.getPartsCost() == null ? BigDecimal.ZERO : record.getPartsCost());
        }
        BigDecimal total = labor.add(parts);
        return new MaintenanceSummaryReport(
                labor, parts, total, null, records.size(),
                MetricAvailability.PARTIAL, "MAINTENANCE_CURRENCY_NOT_STORED_IN_LEGACY_SCHEMA");
    }
}
