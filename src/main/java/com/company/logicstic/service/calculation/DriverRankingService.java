package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.report.DriverRankingItem;
import com.company.logicstic.dto.report.DriverRankingReport;
import com.company.logicstic.dto.report.DriverRankingWeights;
import com.company.logicstic.dto.report.ReportPeriodDto;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.TripDriverAssignment;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import com.company.logicstic.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverRankingService {

    private final EmployeeRepository employeeRepository;
    private final TripRepository tripRepository;
    private final TripDriverAssignmentRepository tripDriverAssignmentRepository;
    private final LoadRepository loadRepository;
    private final InvoiceRepository invoiceRepository;
    private final ZoneId appZoneId;

    @Autowired
    public DriverRankingService(
            EmployeeRepository employeeRepository,
            TripRepository tripRepository,
            TripDriverAssignmentRepository tripDriverAssignmentRepository,
            LoadRepository loadRepository,
            InvoiceRepository invoiceRepository,
            ZoneId appZoneId
    ) {
        this.employeeRepository = employeeRepository;
        this.tripRepository = tripRepository;
        this.tripDriverAssignmentRepository = tripDriverAssignmentRepository;
        this.loadRepository = loadRepository;
        this.invoiceRepository = invoiceRepository;
        this.appZoneId = appZoneId != null ? appZoneId : ZoneOffset.UTC;
    }

    public DriverRankingService(
            EmployeeRepository employeeRepository,
            TripRepository tripRepository,
            TripDriverAssignmentRepository tripDriverAssignmentRepository,
            LoadRepository loadRepository,
            InvoiceRepository invoiceRepository
    ) {
        this(employeeRepository, tripRepository, tripDriverAssignmentRepository, loadRepository, invoiceRepository, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public DriverRankingReport getRanking(LocalDate from, LocalDate to, String currency, String sortBy, int limit) {
        String reportCurrency = CurrencyGuard.normalize(currency != null ? currency : "USD");
        String sortCriterion = (sortBy == null || sortBy.isBlank()) ? "score" : sortBy.trim();
        int maxLimit = Math.max(1, limit > 0 ? limit : 10);

        OffsetDateTime start = from != null ? from.atStartOfDay(appZoneId).toOffsetDateTime() : null;
        OffsetDateTime endExclusive = to != null ? to.plusDays(1).atStartOfDay(appZoneId).toOffsetDateTime() : null;

        List<Employee> drivers = employeeRepository.findAllByEmployeeType(EmployeeType.DRIVER);

        if (drivers.isEmpty()) {
            return new DriverRankingReport(
                    new ReportPeriodDto(from != null ? from.toString() : null, to != null ? to.toString() : null),
                    reportCurrency,
                    sortCriterion,
                    List.of(),
                    DriverRankingWeights.DEFAULT,
                    false
            );
        }

        List<TripDriverAssignment> assignments = tripDriverAssignmentRepository.findAll();
        Map<UUID, List<TripDriverAssignment>> assignmentsByDriver = assignments.stream()
                .filter(a -> a.getDriver() != null && a.getTrip() != null)
                .collect(Collectors.groupingBy(a -> a.getDriver().getId()));

        List<Load> allLoads = loadRepository.findAll();

        List<DriverRankingItem> rawItems = new ArrayList<>();

        for (Employee driver : drivers) {
            UUID driverId = driver.getId();
            String name = (driver.getFirstName() != null ? driver.getFirstName() : "") + " "
                    + (driver.getLastName() != null ? driver.getLastName() : "").trim();
            if (name.isBlank()) name = "Driver " + driverId.toString().substring(0, 8);

            List<TripDriverAssignment> driverAssignments = assignmentsByDriver.getOrDefault(driverId, List.of());

            // Count completed trips and miles
            BigDecimal totalMiles = BigDecimal.ZERO;
            for (TripDriverAssignment da : driverAssignments) {
                Trip trip = da.getTrip();
                if (trip == null) continue;
                OffsetDateTime tripTime = trip.getCompletedAt() != null ? trip.getCompletedAt() : trip.getDispatchedAt();
                if (tripTime != null) {
                    if (start != null && tripTime.isBefore(start)) continue;
                    if (endExclusive != null && !tripTime.isBefore(endExclusive)) continue;
                }
                if (trip.getActualDistanceMiles() != null) {
                    totalMiles = totalMiles.add(trip.getActualDistanceMiles());
                }
            }

            // Find completed loads delivered by this driver's truck
            int completedLoads = 0;
            int onTimeLoads = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            int exceptionsCount = 0;

            for (Load load : allLoads) {
                boolean matchesDriver = false;
                if (load.getAssignedTruck() != null) {
                    if ((load.getAssignedTruck().getMainDriver() != null && driverId.equals(load.getAssignedTruck().getMainDriver().getId()))
                            || (load.getAssignedTruck().getSecondaryDriver() != null && driverId.equals(load.getAssignedTruck().getSecondaryDriver().getId()))) {
                        matchesDriver = true;
                    }
                }
                if (!matchesDriver) {
                    continue;
                }

                OffsetDateTime delivered = load.getDeliveredAt();
                if (delivered != null) {
                    if (start != null && delivered.isBefore(start)) continue;
                    if (endExclusive != null && !delivered.isBefore(endExclusive)) continue;

                    completedLoads++;
                    OffsetDateTime deadline = load.getRequestedDeliveryDate();
                    if (deadline != null && !delivered.isAfter(deadline)) {
                        onTimeLoads++;
                    }

                    if (load.getExceptions() != null) {
                        exceptionsCount += load.getExceptions().size();
                    }

                    var invoices = invoiceRepository.findAllByLoadId(load.getId());
                    for (var inv : invoices) {
                        if (inv.getSubtotalAmount() != null) {
                            revenue = revenue.add(inv.getSubtotalAmount().multiply(BigDecimal.valueOf(inv.economicSign())));
                        }
                    }
                }
            }

            BigDecimal onTimePercent = completedLoads > 0
                    ? BigDecimal.valueOf(onTimeLoads * 100.0 / completedLoads).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100.0);

            BigDecimal driverPay = revenue.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalMilesRounded = totalMiles.setScale(1, RoundingMode.HALF_UP);
            BigDecimal revenueRounded = revenue.setScale(2, RoundingMode.HALF_UP);
            BigDecimal rpm = totalMiles.compareTo(BigDecimal.ZERO) > 0
                    ? revenue.divide(totalMiles, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            rawItems.add(new DriverRankingItem(
                    driverId.toString(),
                    name.trim(),
                    null,
                    0,
                    BigDecimal.ZERO,
                    completedLoads,
                    totalMilesRounded,
                    onTimePercent,
                    revenueRounded,
                    driverPay,
                    rpm,
                    exceptionsCount,
                    false
            ));
        }

        // Calculate scores
        double maxMiles = rawItems.stream().mapToDouble(i -> i.totalMiles().doubleValue()).max().orElse(15000.0);
        if (maxMiles <= 0) maxMiles = 15000.0;
        double maxRevenue = rawItems.stream().mapToDouble(i -> i.revenue().doubleValue()).max().orElse(50000.0);
        if (maxRevenue <= 0) maxRevenue = 50000.0;

        final double finalMaxMiles = maxMiles;
        final double finalMaxRevenue = maxRevenue;

        List<DriverRankingItem> scoredItems = rawItems.stream().map(item -> {
            double otdScore = Math.min(100.0, Math.max(0.0, item.onTimePercent().doubleValue())) * 0.4;
            double prodScore = Math.min(1.0, item.totalMiles().doubleValue() / finalMaxMiles) * 0.3 * 100.0;
            double revScore = Math.min(1.0, item.revenue().doubleValue() / finalMaxRevenue) * 0.3 * 100.0;
            double penalty = item.exceptionsCount() * 2.0;
            double rawScore = otdScore + prodScore + revScore - penalty;
            double clamped = Math.max(0.0, Math.min(100.0, rawScore));
            BigDecimal score = BigDecimal.valueOf(clamped).setScale(1, RoundingMode.HALF_UP);

            return new DriverRankingItem(
                    item.driverId(),
                    item.driverName(),
                    item.avatarUrl(),
                    0,
                    score,
                    item.completedLoads(),
                    item.totalMiles(),
                    item.onTimePercent(),
                    item.revenue(),
                    item.driverPay(),
                    item.revenuePerMile(),
                    item.exceptionsCount(),
                    false
            );
        }).collect(Collectors.toList());

        // Sort items
        scoredItems.sort((a, b) -> {
            return switch (sortCriterion.toLowerCase()) {
                case "revenue" -> b.revenue().compareTo(a.revenue());
                case "totalmiles" -> b.totalMiles().compareTo(a.totalMiles());
                case "ontimepercent" -> b.onTimePercent().compareTo(a.onTimePercent());
                case "score" -> b.score().compareTo(a.score());
                default -> b.score().compareTo(a.score());
            };
        });

        // Assign ranks and limit
        List<DriverRankingItem> finalDrivers = new ArrayList<>();
        int rank = 1;
        for (DriverRankingItem item : scoredItems) {
            if (finalDrivers.size() >= maxLimit) break;
            finalDrivers.add(new DriverRankingItem(
                    item.driverId(),
                    item.driverName(),
                    item.avatarUrl(),
                    rank++,
                    item.score(),
                    item.completedLoads(),
                    item.totalMiles(),
                    item.onTimePercent(),
                    item.revenue(),
                    item.driverPay(),
                    item.revenuePerMile(),
                    item.exceptionsCount(),
                    false
            ));
        }

        return new DriverRankingReport(
                new ReportPeriodDto(from != null ? from.toString() : null, to != null ? to.toString() : null),
                reportCurrency,
                sortCriterion,
                finalDrivers,
                DriverRankingWeights.DEFAULT,
                false
        );
    }
}
