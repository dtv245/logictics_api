package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.entity.*;
import com.company.logicstic.dto.payroll.DriverSettlementView;
import com.company.logicstic.dto.payroll.SettlementLineView;
import com.company.logicstic.dto.payroll.SettlementAdjustmentRequest;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.calculation.CalculationSnapshotService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class DriverPayEngine {
    private final DriverPayPolicyRepository policyRepository;
    private final DriverPayPolicyResolver policyResolver;
    private final MileagePayCalculator mileageCalculator;
    private final TripDriverAssignmentRepository assignmentRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final AccessorialChargeRepository accessorialRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmployeeRepository employeeRepository;
    private final PayPeriodRepository periodRepository;
    private final DriverSettlementRepository settlementRepository;
    private final SettlementLineRepository lineRepository;
    private final CalculationSnapshotService snapshotService;
    private final ShipmentCostRepository shipmentCostRepository;
    private final LoadRepository loadRepository;
    private final TripRepository tripRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DriverSettlementView calculate(UUID driverId, UUID payPeriodId) {
        Employee driver = employeeRepository.findById(driverId).orElseThrow(() -> new BadRequestException("Driver not found"));
        PayPeriod period = periodRepository.findById(payPeriodId).orElseThrow(() -> new BadRequestException("Pay period not found"));
        if (!"OPEN".equalsIgnoreCase(period.getStatus())) throw new BadRequestException("Pay period is not open");
        Optional<DriverSettlement> prior = settlementRepository.findByDriverIdAndPayPeriodIdAndSettlementType(driverId, payPeriodId, "ORIGINAL");
        if (prior.isPresent()) return view(prior.get()); // idempotent retry for original settlement creation

        LocalDate from = period.getStartDate(), through = period.getEndDate();
        OffsetDateTime fromTime = from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime toTime = through.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        List<TripDriverAssignment> assignments = assignmentRepository.findAssignmentsForDriverPeriod(driverId, fromTime, toTime);
        List<TimeEntry> timeEntries = timeEntryRepository.findByEmployeeIdAndDateGreaterThanEqualAndDateLessThan(driverId, fromTime, toTime);
        List<Expense> expenses = expenseRepository.findApprovedByEmployeeAndPeriod(driverId, fromTime, toTime);
        List<SettlementLine> lines = new ArrayList<>();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("driverId", driverId); input.put("payPeriodId", payPeriodId); input.put("assignments", assignments.size());
        input.put("timeEntries", timeEntries.size()); input.put("approvedExpenses", expenses.size());
        BigDecimal miles = BigDecimal.ZERO, hours = BigDecimal.ZERO, mileagePay = BigDecimal.ZERO, loadPay = BigDecimal.ZERO;
        BigDecimal percentPay = BigDecimal.ZERO, hourlyPay = BigDecimal.ZERO, accessorialPay = BigDecimal.ZERO;
        BigDecimal reimbursement = BigDecimal.ZERO, deduction = BigDecimal.ZERO;
        Map<UUID, DriverPayPolicy> policiesUsed = new LinkedHashMap<>();
        List<MileagePayCalculator.Result> mileageCalculations = new ArrayList<>();
        DriverPayPolicy headerPolicy = resolveHeaderPolicy(driverId, from, through, assignments, timeEntries);
        policiesUsed.put(headerPolicy.getId(), headerPolicy);
        String currency = CurrencyGuard.canonical(headerPolicy.getCurrency());

        Set<String> handledAccessorials = new HashSet<>();
        Set<String> dailyPaid = new HashSet<>();
        for (TripDriverAssignment assignment : assignments) {
            LocalDate workDate = tripWorkDate(assignment, from, through);
            if (workDate.isBefore(from)) workDate = from;
            if (workDate.isAfter(through)) continue;
            DriverPayPolicy policy = resolvePolicy(driverId, workDate);
            policiesUsed.put(policy.getId(), policy);
            CurrencyGuard.requireSameCurrency(currency, policy.getCurrency());
            Trip trip = assignment.getTrip();
            List<Load> loads = trip.getStops().stream().map(TripStop::getLoad).filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(Load::getId, x -> x, (a,b) -> a, LinkedHashMap::new)).values().stream().toList();
            if ("PER_MILE".equals(policy.getPayMethod())) {
                var calculation = mileageCalculator.calculate(assignment,policy);
                mileageCalculations.add(calculation);
                BigDecimal basisMiles = calculation.eligibleMiles(), amount = calculation.amount();
                mileagePay = mileagePay.add(amount); miles = miles.add(basisMiles);
                lines.add(line("MILEAGE", "EARNING", trip, uniqueLoad(loads), "Mileage pay", basisMiles, "MILE", policy.getPerMileRate(), amount, currency, "TRIP_ASSIGNMENT", assignment.getId()));
            } else if ("PER_LOAD".equals(policy.getPayMethod())) {
                if (loads.isEmpty()) throw new BadRequestException("Trip has no load attribution for per-load pay: " + trip.getId());
                for (Load load : loads) {
                    BigDecimal amount = policy.getPerLoadRate(); loadPay = loadPay.add(amount);
                    lines.add(line("LOAD", "EARNING", trip, load, "Per-load pay", BigDecimal.ONE, "LOAD", amount, amount, currency, "TRIP_ASSIGNMENT", assignment.getId()));
                }
            } else if ("PERCENT_REVENUE".equals(policy.getPayMethod())) {
                for (Load load : loads) {
                    Invoice invoice = invoiceRepository.findByLoadId(load.getId()).orElse(null);
                    if (invoice == null || invoice.getSubtotalAmount() == null || !com.company.logicstic.common.enums.InvoiceStatus.fromString(invoice.getStatus()).countsAsRevenue()) continue;
                    CurrencyGuard.requireSameCurrency(currency, invoice.getSubtotalCurrency());
                    BigDecimal amount = invoice.getSubtotalAmount().multiply(policy.getRevenuePercentage());
                    percentPay = percentPay.add(amount);
                    lines.add(line("REVENUE_PERCENT", "EARNING", trip, load, "Percentage of invoiced revenue", invoice.getSubtotalAmount(), currency, policy.getRevenuePercentage(), amount, currency, "INVOICE", invoice.getId()));
                }
            } else if ("FLAT_RATE".equals(policy.getPayMethod())) {
                // A flat period rate is applied once using the policy effective on period start.
            } else if ("HOURLY".equals(policy.getPayMethod()) || "DAILY".equals(policy.getPayMethod())) {
                // Time-entry pay is calculated below, using the policy effective on each entry date.
            }
            for (AccessorialCharge charge : accessorialRepository.findByTripId(trip.getId())) {
                if (!"APPROVED".equalsIgnoreCase(charge.getStatus()) || !handledAccessorials.add(charge.getId().toString())) continue;
                if (charge.getOccurredAt() != null && (charge.getOccurredAt().toLocalDate().isBefore(from) || charge.getOccurredAt().toLocalDate().isAfter(through))) continue;
                CurrencyGuard.requireSameCurrency(currency, charge.getCurrency());
                BigDecimal amount = charge.getDriverPayAmount();
                accessorialPay = accessorialPay.add(amount);
                lines.add(line("ACCESSORIAL", "EARNING", trip, charge.getLoad(), "Approved driver accessorial", BigDecimal.ONE, "CHARGE", amount, amount, currency, "ACCESSORIAL", charge.getId()));
            }
        }

        if ("FLAT_RATE".equals(headerPolicy.getPayMethod())) {
            BigDecimal amount = headerPolicy.getFlatRate();
            loadPay = loadPay.add(amount);
            lines.add(line("FLAT_RATE", "EARNING", null, null, "Period flat pay", BigDecimal.ONE, "PERIOD", amount, amount, currency, "PAY_POLICY", headerPolicy.getId()));
        }
        for (TimeEntry entry : timeEntries) {
            if (entry.getDate() == null || entry.getDate().toLocalDate().isBefore(from) || entry.getDate().toLocalDate().isAfter(through)) continue;
            DriverPayPolicy policy = resolvePolicy(driverId, entry.getDate().toLocalDate());
            policiesUsed.put(policy.getId(), policy);
            CurrencyGuard.requireSameCurrency(currency, policy.getCurrency());
            if ("HOURLY".equals(policy.getPayMethod())) {
                BigDecimal qty = entry.getTotalHours();
                if (qty == null || qty.signum() <= 0) throw new BadRequestException("Time entry hours must be positive: " + entry.getId());
                BigDecimal amount = policy.getHourlyRate().multiply(qty);
                hourlyPay = hourlyPay.add(amount); hours = hours.add(qty);
                lines.add(line("HOURLY", "EARNING", null, null, "Time entry " + entry.getId(), qty, "HOUR", policy.getHourlyRate(), amount, currency, "TIME_ENTRY", entry.getId()));
            } else if ("DAILY".equals(policy.getPayMethod())) {
                String dayKey = entry.getDate().toLocalDate() + ":" + policy.getId();
                if (dailyPaid.add(dayKey)) {
                    BigDecimal amount = policy.getDailyRate(); hourlyPay = hourlyPay.add(amount);
                    lines.add(line("DAILY", "EARNING", null, null, "Daily pay " + entry.getDate().toLocalDate(), BigDecimal.ONE, "DAY", amount, amount, currency, "TIME_ENTRY", entry.getId()));
                }
            }
        }
        for (Expense expense : expenses) {
            String kind = Objects.toString(expense.getType(), "").toUpperCase(Locale.ROOT);
            String category = Objects.toString(expense.getCategory(), "").toUpperCase(Locale.ROOT);
            boolean isReimbursement = "REIMBURSEMENT".equals(kind) || "REIMBURSEMENT".equals(category);
            boolean isDeduction = Set.of("DEDUCTION", "ADVANCE", "PENALTY").contains(kind) || Set.of("DEDUCTION", "ADVANCE", "PENALTY").contains(category);
            if (!isReimbursement && !isDeduction) continue;
            CurrencyGuard.requireSameCurrency(currency, expense.getAmountCurrency());
            BigDecimal amount = expense.getAmountAmount().abs();
            SettlementLine adjustmentLine = line(isReimbursement ? "REIMBURSEMENT" : "DEDUCTION", isReimbursement ? "REIMBURSEMENT" : "DEDUCTION",
                    expense.getTrip(), expense.getLoad(), expense.getNotes() == null ? kind : expense.getNotes(), BigDecimal.ONE, "ITEM", amount, amount, currency, "EXPENSE", expense.getId());
            adjustmentLine.setExpense(expense);
            lines.add(adjustmentLine);
            if (isReimbursement) reimbursement = reimbursement.add(amount); else deduction = deduction.add(amount);
        }
        mileagePay = sum(lines, "MILEAGE", "EARNING");
        loadPay = sum(lines, "LOAD", "EARNING").add(sum(lines, "FLAT_RATE", "EARNING"));
        percentPay = sum(lines, "REVENUE_PERCENT", "EARNING");
        hourlyPay = sum(lines, "HOURLY", "EARNING").add(sum(lines, "DAILY", "EARNING"));
        accessorialPay = sum(lines, "ACCESSORIAL", "EARNING");
        reimbursement = sum(lines, "REIMBURSEMENT", "REIMBURSEMENT");
        deduction = sum(lines, "DEDUCTION", "DEDUCTION");
        BigDecimal gross = MoneyRoundingPolicy.round(mileagePay.add(loadPay).add(percentPay).add(hourlyPay).add(accessorialPay), currency);
        BigDecimal net = MoneyRoundingPolicy.round(gross.subtract(deduction).add(reimbursement), currency);
        input.put("policies", policiesUsed.values().stream().map(p -> Map.of("id", p.getId(), "version", p.getPolicyVersion(),
                "effectiveFrom", p.getEffectiveFrom(), "effectiveTo", Objects.toString(p.getEffectiveTo(), ""), "method", p.getPayMethod())).toList());
        input.put("mileageCalculations", mileageCalculations);
        input.put("lines", lines.stream().map(l -> Map.of("type", l.getLineType(), "class", l.getLineClass(), "amount", l.getAmount(),
                "currency", l.getCurrency(), "sourceType", Objects.toString(l.getSourceType(), ""),
                "sourceId", Objects.toString(l.getSourceId(), ""), "loadId", l.getLoad() == null ? "" : l.getLoad().getId().toString(),
                "tripId", l.getTrip() == null ? "" : l.getTrip().getId().toString())).toList());
        UUID settlementId = UUID.randomUUID();
        Map<String, Object> result = Map.of("grossEarnings", gross, "deductions", deduction, "reimbursements", reimbursement,
                "settlementNet", net, "lineCount", lines.size(), "mileagePay", mileagePay, "loadPay", loadPay,
                "percentagePay", percentPay, "hourlyPay", hourlyPay, "accessorialPay", accessorialPay);
        CalculationSnapshot snapshot = snapshotService.recordSnapshot("DRIVER_SETTLEMENT", settlementId, "DRIVER_PAY",
                "DriverPayEngine", "1", "DRIVER_PAY_POLICY", headerPolicy.getId(), String.valueOf(headerPolicy.getPolicyVersion()),
                json(input), json(result), currency, null, null);

        DriverSettlement settlement = new DriverSettlement();
        settlement.setId(settlementId); settlement.setSettlementNumber("DS-" + settlementId);
        settlement.setDriver(driver); settlement.setPayPeriod(period); settlement.setPayPolicy(headerPolicy);
        settlement.setPayPolicyVersion(headerPolicy.getPolicyVersion()); settlement.setCurrency(currency);
        settlement.setMileagePay(MoneyRoundingPolicy.round(mileagePay,currency)); settlement.setLoadPay(MoneyRoundingPolicy.round(loadPay,currency));
        settlement.setPercentagePay(MoneyRoundingPolicy.round(percentPay,currency)); settlement.setHourlyPay(MoneyRoundingPolicy.round(hourlyPay,currency));
        settlement.setAccessorialPay(MoneyRoundingPolicy.round(accessorialPay,currency)); settlement.setReimbursementAmount(MoneyRoundingPolicy.round(reimbursement,currency));
        settlement.setDeductionAmount(MoneyRoundingPolicy.round(deduction,currency)); settlement.setGrossEarnings(gross); settlement.setSettlementNet(net);
        settlement.setEligibleMiles(miles); settlement.setEligibleHours(hours); settlement.setCalculationSnapshot(snapshot);
        settlement.setCalculatedAt(OffsetDateTime.now()); settlement.setStatus("CALCULATED");
        settlementRepository.save(settlement);
        lines.forEach(l -> { l.setSettlement(settlement); l.setCalculationSnapshot(snapshot); });
        lineRepository.saveAll(lines);
        return view(settlement);
    }

    @Transactional
    public DriverSettlementView transition(UUID id, String target, UUID actor) {
        DriverSettlement s = settlementRepository.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Settlement not found"));
        String state = s.getStatus();
        if (target.equals(state)) return view(s); // retry-safe command response
        if ("IN_REVIEW".equals(target) && "CALCULATED".equals(state)) { s.setStatus(target); s.setReviewedAt(OffsetDateTime.now()); s.setReviewedBy(actor); }
        else if ("APPROVED".equals(target) && "IN_REVIEW".equals(state)) { s.setStatus(target); s.setApprovedAt(OffsetDateTime.now()); s.setApprovedBy(actor); }
        else if ("LOCKED".equals(target) && "APPROVED".equals(state)) { s.setLockedAt(OffsetDateTime.now()); s.setLockedBy(actor); projectAttributedLines(s); s.setStatus(target); }
        else throw new BadRequestException("INVALID_SETTLEMENT_TRANSITION", "Cannot transition settlement from " + state + " to " + target);
        return view(settlementRepository.save(s));
    }

    @Transactional(readOnly = true)
    public DriverSettlementView get(UUID id) {
        DriverSettlement settlement = settlementRepository.findById(id).orElseThrow(() -> new BadRequestException("Settlement not found"));
        return view(settlement);
    }

    @Transactional
    public DriverSettlementView createAdjustment(UUID parentId, SettlementAdjustmentRequest request) {
        DriverSettlement parent = settlementRepository.findByIdForUpdate(parentId).orElseThrow(() -> new BadRequestException("Parent settlement not found"));
        if (!"LOCKED".equals(parent.getStatus())) throw new BadRequestException("Only locked settlements may be adjusted");
        List<SettlementLine> lines = new ArrayList<>();
        for (SettlementAdjustmentRequest.Line item : request.lines()) {
            Load load = item.loadId() == null ? null : loadRepository.findById(item.loadId()).orElseThrow(() -> new BadRequestException("Adjustment load not found"));
            Trip trip = item.tripId() == null ? null : tripRepository.findById(item.tripId()).orElseThrow(() -> new BadRequestException("Adjustment trip not found"));
            if (load == null && trip != null) {
                List<Load> tripLoads = trip.getStops().stream().map(TripStop::getLoad).filter(Objects::nonNull).distinct().toList();
                if (tripLoads.size() == 1) load = tripLoads.getFirst();
            }
            if (trip != null && load != null) {
                UUID adjustmentLoadId = load.getId();
                if (trip.getStops().stream().noneMatch(stop -> adjustmentLoadId.equals(stop.getLoad().getId())))
                    throw new BadRequestException("Adjustment load is not attributed to the supplied trip");
            }
            SettlementLine line = line(item.lineType().trim().toUpperCase(Locale.ROOT), item.lineClass().trim().toUpperCase(Locale.ROOT),
                    trip, load, item.description(), BigDecimal.ONE, "ADJUSTMENT", item.amount(), item.amount(), parent.getCurrency(),
                    "DRIVER_SETTLEMENT_ADJUSTMENT", UUID.randomUUID());
            lines.add(line);
        }
        return createChild(parent, "ADJUSTMENT", request.reason(), lines);
    }

    @Transactional
    public DriverSettlementView reverse(UUID parentId, String reason) {
        DriverSettlement parent = settlementRepository.findByIdForUpdate(parentId).orElseThrow(() -> new BadRequestException("Parent settlement not found"));
        if (!"LOCKED".equals(parent.getStatus())) throw new BadRequestException("Only locked settlements may be reversed");
        List<SettlementLine> reversalLines = new ArrayList<>();
        for (SettlementLine original : lineRepository.findBySettlementIdOrderById(parentId)) {
            String reversedClass = switch (original.getLineClass()) {
                case "EARNING" -> "DEDUCTION";
                case "DEDUCTION" -> "EARNING";
                case "REIMBURSEMENT" -> "DEDUCTION";
                default -> throw new BadRequestException("Unsupported settlement line class " + original.getLineClass());
            };
            String source = "EARNING".equals(original.getLineClass()) ? "DRIVER_SETTLEMENT_REVERSAL" : "SETTLEMENT_REVERSAL_NON_COST";
            SettlementLine line = line("REVERSAL_" + original.getLineType(), reversedClass, original.getTrip(), original.getLoad(),
                    "Reverse " + original.getId() + ": " + reason, BigDecimal.ONE, "REVERSAL", original.getAmount(),
                    original.getAmount(), parent.getCurrency(), source, original.getId());
            reversalLines.add(line);
        }
        return createChild(parent, "REVERSAL", reason, reversalLines);
    }

    private DriverSettlementView createChild(DriverSettlement parent, String type, String reason, List<SettlementLine> lines) {
        Integer maxSequence = settlementRepository.findMaxSequenceForParent(parent.getId());
        int sequence = maxSequence == null ? 1 : maxSequence + 1;
        BigDecimal gross = sumClass(lines, "EARNING", parent.getCurrency());
        BigDecimal deductions = sumClass(lines, "DEDUCTION", parent.getCurrency());
        BigDecimal reimbursements = sumClass(lines, "REIMBURSEMENT", parent.getCurrency());
        BigDecimal net = MoneyRoundingPolicy.round(gross.subtract(deductions).add(reimbursements), parent.getCurrency());
        UUID id = UUID.randomUUID();
        Map<String,Object> input = Map.of("parentSettlementId", parent.getId(), "type", type, "sequence", sequence, "reason", reason,
                "lines", lines.stream().map(l -> Map.of("amount", l.getAmount(), "class", l.getLineClass(), "loadId", l.getLoad() == null ? "" : l.getLoad().getId().toString(),
                        "sourceId", Objects.toString(l.getSourceId(), ""))).toList());
        Map<String,Object> result = Map.of("grossEarnings", gross, "deductions", deductions, "reimbursements", reimbursements, "settlementNet", net);
        CalculationSnapshot snapshot = snapshotService.recordSnapshot("DRIVER_SETTLEMENT", id, type, "DriverPayEngine", "1",
                "DRIVER_PAY_POLICY", parent.getPayPolicy().getId(), String.valueOf(parent.getPayPolicyVersion()), json(input), json(result),
                parent.getCurrency(), null, null);
        DriverSettlement child = new DriverSettlement();
        child.setId(id); child.setSettlementNumber("DS-" + id); child.setDriver(parent.getDriver()); child.setPayPeriod(parent.getPayPeriod());
        child.setSettlementType(type); child.setParentSettlement(parent); child.setSequenceNumber(sequence); child.setPayPolicy(parent.getPayPolicy());
        child.setPayPolicyVersion(parent.getPayPolicyVersion()); child.setStatus("CALCULATED"); child.setCurrency(parent.getCurrency());
        child.setGrossEarnings(gross); child.setDeductionAmount(deductions); child.setReimbursementAmount(reimbursements);
        child.setSettlementNet(net); child.setCalculationSnapshot(snapshot); child.setCalculatedAt(OffsetDateTime.now());
        settlementRepository.save(child);
        lines.forEach(l -> { l.setSettlement(child); l.setCalculationSnapshot(snapshot); });
        lineRepository.saveAll(lines);
        return view(child);
    }

    private BigDecimal sumClass(List<SettlementLine> lines, String lineClass, String currency) {
        return MoneyRoundingPolicy.round(lines.stream().filter(l -> lineClass.equals(l.getLineClass())).map(SettlementLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), currency);
    }

    private DriverSettlementView view(DriverSettlement s) {
        List<SettlementLineView> lines = lineRepository.findBySettlementIdOrderById(s.getId()).stream().map(SettlementLineView::from).toList();
        return DriverSettlementView.from(s, lines);
    }

    private BigDecimal sum(List<SettlementLine> lines, String type, String lineClass) {
        return lines.stream().filter(l -> type.equals(l.getLineType()) && lineClass.equals(l.getLineClass()))
                .map(SettlementLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate tripWorkDate(TripDriverAssignment assignment, LocalDate from, LocalDate through) {
        if (assignment.getTrip().getCompletedAt() != null) {
            LocalDate completed = assignment.getTrip().getCompletedAt().toLocalDate();
            if (!completed.isBefore(from) && !completed.isAfter(through)) return completed;
        }
        LocalDate date = assignment.getEffectiveFrom().toLocalDate();
        return date.isBefore(from) ? from : date.isAfter(through) ? through : date;
    }

    private void projectAttributedLines(DriverSettlement settlement) {
        for (SettlementLine line : lineRepository.findBySettlementIdOrderById(settlement.getId())) {
            if (line.getLoad() == null) continue;
            BigDecimal projectedAmount = line.getAmount();
            String source;
            if ("ORIGINAL".equals(settlement.getSettlementType()) && "EARNING".equals(line.getLineClass())) source = "DRIVER_SETTLEMENT";
            else if ("REVERSAL".equals(settlement.getSettlementType()) && "DRIVER_SETTLEMENT_REVERSAL".equals(line.getSourceType())) {
                source = "DRIVER_SETTLEMENT_REVERSAL"; projectedAmount = projectedAmount.negate();
            } else if ("ADJUSTMENT".equals(settlement.getSettlementType()) && "DRIVER_SETTLEMENT_ADJUSTMENT".equals(line.getSourceType()) &&
                    Set.of("EARNING", "DEDUCTION").contains(line.getLineClass())) {
                source = "DRIVER_SETTLEMENT_ADJUSTMENT";
                if ("DEDUCTION".equals(line.getLineClass())) projectedAmount = projectedAmount.negate();
            } else continue;
            if (projectedAmount.signum() == 0) continue;
            if (shipmentCostRepository.existsBySourceTypeAndSourceId(source, line.getId())) continue;
            ShipmentCost cost = new ShipmentCost(); cost.setLoad(line.getLoad()); cost.setTrip(line.getTrip());
            cost.setDriver(settlement.getDriver()); cost.setCategory("DRIVER"); cost.setCostBasis("ACTUAL"); cost.setStatus("VERIFIED");
            cost.setSourceType(source); cost.setSourceId(line.getId()); cost.setAmount(projectedAmount); cost.setCurrency(line.getCurrency());
            cost.setCalculationSnapshot(line.getCalculationSnapshot()); cost.setNote("Locked settlement " + settlement.getSettlementNumber() + " / " + line.getDescription());
            cost.setIncurredAt(settlement.getLockedAt() == null ? OffsetDateTime.now() : settlement.getLockedAt());
            shipmentCostRepository.save(cost);
        }
    }

    private DriverPayPolicy resolvePolicy(UUID driverId, LocalDate date) {
        return policyResolver.resolve(driverId, date);
    }

    private DriverPayPolicy resolveHeaderPolicy(UUID driverId, LocalDate from, LocalDate through,
            List<TripDriverAssignment> assignments, List<TimeEntry> entries) {
        try { return resolvePolicy(driverId, from); }
        catch (BadRequestException noPolicyAtPeriodStart) {
            for (TripDriverAssignment assignment : assignments) {
                LocalDate date = tripWorkDate(assignment, from, through);
                try { return resolvePolicy(driverId, date); } catch (BadRequestException ignored) { /* try next work date */ }
            }
            for (TimeEntry entry : entries) {
                if (entry.getDate() == null) continue;
                try { return resolvePolicy(driverId, entry.getDate().toLocalDate()); } catch (BadRequestException ignored) { /* try next work date */ }
            }
            throw noPolicyAtPeriodStart;
        }
    }

    private SettlementLine line(String type, String lineClass, Trip trip, Load load, String description,
            BigDecimal quantity, String unit, BigDecimal rate, BigDecimal amount, String currency, String sourceType, UUID sourceId) {
        SettlementLine l = new SettlementLine(); l.setLineType(type); l.setLineClass(lineClass); l.setTrip(trip); l.setLoad(load);
        l.setDescription(description); l.setQuantity(quantity); l.setUnit(unit); l.setRate(rate); l.setAmount(MoneyRoundingPolicy.round(amount,currency));
        l.setCurrency(currency); l.setSourceType(sourceType); l.setSourceId(sourceId); return l;
    }
    private Load uniqueLoad(List<Load> loads) { return loads.size() == 1 ? loads.getFirst() : null; }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (tools.jackson.core.JacksonException e) { throw new IllegalStateException("Cannot snapshot payroll calculation", e); }
    }
}
