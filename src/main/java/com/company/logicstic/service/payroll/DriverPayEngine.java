package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
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
    private final WorkPayCalculator workCalculator;
    private final PercentagePayCalculator percentageCalculator;
    private final SettlementRevenueGuard revenueGuard;
    private final AccessorialDriverPayCalculator accessorialCalculator;
    private final SettlementReconciliationService settlementReconciliation;
    private final FinancialRoundingPolicy rounding;
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
        PayPeriod period = periodRepository.findByIdForUpdate(payPeriodId).orElseThrow(() -> new BadRequestException("Pay period not found"));
        Employee driver = employeeRepository.findByIdForUpdate(driverId).orElseThrow(() -> new BadRequestException("Driver not found"));
        Optional<DriverSettlement> prior = settlementRepository.findByDriverIdAndPayPeriodIdAndSettlementType(driverId, payPeriodId, "ORIGINAL");
        if (prior.isPresent()) return view(prior.get()); // idempotent retry for original settlement creation
        if (!"OPEN".equalsIgnoreCase(period.getStatus())) throw new BadRequestException("Pay period is not open");
        List<SettlementLine> previouslySettled = lineRepository.findOriginalLinesByDriver(driverId);

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
        List<WorkPayCalculator.Result> workCalculations = new ArrayList<>();
        List<PercentagePayCalculator.Result> percentageCalculations = new ArrayList<>();
        List<AccessorialDriverPayCalculator.Result> accessorialCalculations = new ArrayList<>();
        DriverPayPolicy headerPolicy = resolveHeaderPolicy(driverId, from, through, assignments, timeEntries);
        policiesUsed.put(headerPolicy.getId(), headerPolicy);
        String currency = CurrencyGuard.canonical(headerPolicy.getCurrency());

        Set<String> handledAccessorials = new HashSet<>();
        Set<String> dailyPaid = new HashSet<>();
        Set<UUID> paidLoads = new HashSet<>();
        Set<UUID> revenueLoads = new HashSet<>();
        for (TripDriverAssignment assignment : assignments) {
            LocalDate workDate = tripWorkDate(assignment, from, through);
            boolean eligibleWork = workDate != null && !workDate.isBefore(from) && !workDate.isAfter(through);
            DriverPayPolicy policy = eligibleWork ? resolvePolicy(driverId, workDate) : headerPolicy;
            policiesUsed.put(policy.getId(), policy);
            CurrencyGuard.requireSameCurrency(currency, policy.getCurrency());
            Trip trip = assignment.getTrip();
            List<Load> loads = trip.getStops().stream().map(TripStop::getLoad).filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(Load::getId, x -> x, (a,b) -> a, LinkedHashMap::new)).values().stream().toList();
            int firstWorkLine = lines.size();
            if (eligibleWork && "PER_MILE".equals(policy.getPayMethod())) {
                var calculation = mileageCalculator.calculate(assignment,policy);
                mileageCalculations.add(calculation);
                BigDecimal basisMiles = calculation.eligibleMiles(), amount = calculation.amount();
                mileagePay = mileagePay.add(amount); miles = miles.add(basisMiles);
                lines.add(line("MILEAGE", "EARNING", trip, uniqueLoad(loads), "Mileage pay", basisMiles, "MILE", policy.getPerMileRate(), amount, currency, "TRIP_ASSIGNMENT", assignment.getId()));
            } else if (eligibleWork && "PER_LOAD".equals(policy.getPayMethod())) {
                if (loads.isEmpty()) throw new BadRequestException("Trip has no load attribution for per-load pay: " + trip.getId());
                for (Load load : loads) {
                    if (!paidLoads.add(load.getId())) continue;
                    var calculation = workCalculator.perLoad(load.getId(),policy); workCalculations.add(calculation);
                    BigDecimal amount = calculation.amount(); loadPay = loadPay.add(amount);
                    lines.add(line("LOAD", "EARNING", trip, load, "Per-load pay", BigDecimal.ONE, "LOAD", amount, amount, currency, "TRIP_ASSIGNMENT", assignment.getId()));
                }
            } else if (eligibleWork && "PERCENT_REVENUE".equals(policy.getPayMethod())) {
                if (loads.isEmpty()) throw new BadRequestException("REVENUE_PAY_VALIDATION_REQUIRED", "Trip has no attributable eligible load revenue");
                for (Load load : loads) {
                    if (!revenueLoads.add(load.getId())) continue;
                    var calculation = percentageCalculator.calculateBillingChain(invoiceRepository.findAllByLoadId(load.getId()),policy); percentageCalculations.add(calculation);
                    BigDecimal amount = calculation.amount();
                    percentPay = percentPay.add(amount);
                    lines.add(line("REVENUE_PERCENT", "EARNING", trip, load, "Percentage of eligible economic revenue", calculation.eligibleRevenue(), currency, policy.getRevenuePercentage(), amount, currency, "INVOICE", calculation.invoiceId()));
                }
            } else if ("FLAT_RATE".equals(policy.getPayMethod())) {
                // A flat period rate is applied once using the policy effective on period start.
            } else if ("HOURLY".equals(policy.getPayMethod()) || "DAILY".equals(policy.getPayMethod())) {
                // Time-entry pay is calculated below, using the policy effective on each entry date.
            }
            for (int i=firstWorkLine; i<lines.size(); i++) lines.get(i).setBusinessDate(workDate);
            List<AccessorialCharge> charges = new ArrayList<>(accessorialRepository.findByTripId(trip.getId()));
            for (Load load : loads) charges.addAll(accessorialRepository.findByLoadId(load.getId()).stream().filter(c -> c.getTrip() == null).toList());
            for (AccessorialCharge charge : charges) {
                if (!handledAccessorials.add(charge.getId().toString())) continue;
                var eligible = accessorialCalculator.calculate(charge,driverId,from,through,currency);
                if (eligible.isEmpty()) continue;
                var calculation = eligible.get(); accessorialCalculations.add(calculation);
                BigDecimal amount = calculation.amount();
                accessorialPay = accessorialPay.add(amount);
                Trip attributedTrip = calculation.tripId() == null ? null : tripRepository.findById(calculation.tripId()).orElseThrow(() -> new BadRequestException("Accessorial trip missing"));
                var payLine = line("ACCESSORIAL", "EARNING", attributedTrip, charge.getLoad(), "Approved driver accessorial", BigDecimal.ONE, "CHARGE", amount, amount, currency, "ACCESSORIAL", charge.getId());
                payLine.setAccessorialCharge(charge); payLine.setBusinessDate(utcDate(charge.getOccurredAt())); lines.add(payLine);
            }
        }

        if ("FLAT_RATE".equals(headerPolicy.getPayMethod())) {
            if (headerPolicy.getEffectiveFrom().isAfter(from)) throw new BadRequestException("FLAT_PAY_VALIDATION_REQUIRED", "Flat period policy must be effective at period start");
            var calculation = workCalculator.flat(payPeriodId,headerPolicy); workCalculations.add(calculation);
            BigDecimal amount = calculation.amount();
            loadPay = loadPay.add(amount);
            var flat = line("FLAT_RATE", "EARNING", null, null, "Period flat pay", BigDecimal.ONE, "PERIOD", amount, amount, currency, "PAY_PERIOD", period.getId());
            flat.setBusinessDate(from); lines.add(flat);
        }
        for (TimeEntry entry : timeEntries) {
            if (entry.getDate() == null || utcDate(entry.getDate()).isBefore(from) || utcDate(entry.getDate()).isAfter(through)) continue;
            DriverPayPolicy policy = resolvePolicy(driverId, utcDate(entry.getDate()));
            policiesUsed.put(policy.getId(), policy);
            CurrencyGuard.requireSameCurrency(currency, policy.getCurrency());
            if ("HOURLY".equals(policy.getPayMethod())) {
                var calculation = workCalculator.hourly(entry,policy); workCalculations.add(calculation);
                BigDecimal qty = calculation.eligibleQuantity(), amount = calculation.amount();
                hourlyPay = hourlyPay.add(amount); hours = hours.add(qty);
                var hourly = line("HOURLY", "EARNING", null, null, "Time entry " + entry.getId(), qty, "HOUR", policy.getHourlyRate(), amount, currency, "TIME_ENTRY", entry.getId());
                hourly.setBusinessDate(utcDate(entry.getDate())); lines.add(hourly);
            } else if ("DAILY".equals(policy.getPayMethod())) {
                String dayKey = utcDate(entry.getDate()) + ":" + policy.getId();
                if (dailyPaid.add(dayKey)) {
                    var calculation = workCalculator.daily(entry.getId(),policy); workCalculations.add(calculation);
                    BigDecimal amount = calculation.amount(); hourlyPay = hourlyPay.add(amount);
                    var daily = line("DAILY", "EARNING", null, null, "Daily pay " + utcDate(entry.getDate()), BigDecimal.ONE, "DAY", amount, amount, currency, "TIME_ENTRY", entry.getId());
                    daily.setBusinessDate(utcDate(entry.getDate())); lines.add(daily);
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
            BigDecimal amount = expense.getAmountAmount();
            if (amount == null || amount.signum()<0) throw new BadRequestException("EXPENSE_PAY_VALIDATION_REQUIRED", "Explicit non-negative expense amount required");
            SettlementLine adjustmentLine = line(isReimbursement ? "REIMBURSEMENT" : "DEDUCTION", isReimbursement ? "REIMBURSEMENT" : "DEDUCTION",
                    expense.getTrip(), expense.getLoad(), expense.getNotes() == null ? kind : expense.getNotes(), BigDecimal.ONE, "ITEM", amount, amount, currency, "EXPENSE", expense.getId());
            adjustmentLine.setExpense(expense);
            adjustmentLine.setBusinessDate(utcDate(expense.getExpenseDate()));
            lines.add(adjustmentLine);
            if (isReimbursement) reimbursement = reimbursement.add(amount); else deduction = deduction.add(amount);
        }
        preventSourceReuse(lines,previouslySettled);
        mileagePay = sum(lines, "MILEAGE", "EARNING");
        loadPay = sum(lines, "LOAD", "EARNING").add(sum(lines, "FLAT_RATE", "EARNING"));
        percentPay = sum(lines, "REVENUE_PERCENT", "EARNING");
        hourlyPay = sum(lines, "HOURLY", "EARNING").add(sum(lines, "DAILY", "EARNING"));
        accessorialPay = sum(lines, "ACCESSORIAL", "EARNING");
        reimbursement = sum(lines, "REIMBURSEMENT", "REIMBURSEMENT");
        deduction = sum(lines, "DEDUCTION", "DEDUCTION");
        BigDecimal gross = money(mileagePay.add(loadPay).add(percentPay).add(hourlyPay).add(accessorialPay), currency);
        BigDecimal net = money(gross.subtract(deduction).add(reimbursement), currency);
        input.put("roundingPolicyVersion",rounding.version());
        input.put("policies", policiesUsed.values().stream().map(p -> Map.of("id", p.getId(), "version", p.getPolicyVersion(),
                "effectiveFrom", p.getEffectiveFrom(), "effectiveTo", Objects.toString(p.getEffectiveTo(), ""), "method", p.getPayMethod())).toList());
        input.put("mileageCalculations", mileageCalculations);
        input.put("workCalculations", workCalculations);
        input.put("percentageCalculations", percentageCalculations);
        input.put("accessorialCalculations", accessorialCalculations);
        input.put("lines", lines.stream().map(l -> Map.of("type", l.getLineType(), "class", l.getLineClass(), "amount", l.getAmount(),
                "currency", l.getCurrency(), "sourceType", Objects.toString(l.getSourceType(), ""),
                "sourceId", Objects.toString(l.getSourceId(), ""), "loadId", l.getLoad() == null ? "" : l.getLoad().getId().toString(),
                "tripId", l.getTrip() == null ? "" : l.getTrip().getId().toString(), "businessDate", Objects.toString(l.getBusinessDate(), ""))).toList());
        UUID settlementId = UUID.randomUUID();
        Map<String, Object> result = Map.of("grossEarnings", gross, "deductions", deduction, "reimbursements", reimbursement,
                "settlementNet", net, "lineCount", lines.size(), "mileagePay", mileagePay, "loadPay", loadPay,
                "percentagePay", percentPay, "hourlyPay", hourlyPay, "accessorialPay", accessorialPay);
        CalculationSnapshot snapshot = snapshotService.recordSnapshot("DRIVER_SETTLEMENT", settlementId, "DRIVER_PAY",
                "DriverPayEngine", "2", "DRIVER_PAY_POLICY", headerPolicy.getId(), String.valueOf(headerPolicy.getPolicyVersion()),
                json(input), json(result), currency, null, null);

        DriverSettlement settlement = new DriverSettlement();
        settlement.setId(settlementId); settlement.setSettlementNumber("DS-" + settlementId);
        settlement.setDriver(driver); settlement.setPayPeriod(period); settlement.setPayPolicy(headerPolicy);
        settlement.setPayPolicyVersion(headerPolicy.getPolicyVersion()); settlement.setCurrency(currency);
        settlement.setMileagePay(money(mileagePay,currency)); settlement.setLoadPay(money(loadPay,currency));
        settlement.setPercentagePay(money(percentPay,currency)); settlement.setHourlyPay(money(hourlyPay,currency));
        settlement.setAccessorialPay(money(accessorialPay,currency)); settlement.setReimbursementAmount(money(reimbursement,currency));
        settlement.setDeductionAmount(money(deduction,currency)); settlement.setGrossEarnings(gross); settlement.setSettlementNet(net);
        settlement.setEligibleMiles(miles); settlement.setEligibleHours(hours); settlement.setCalculationSnapshot(snapshot);
        settlement.setCalculatedAt(now()); settlement.setStatus("CALCULATED");
        settlementReconciliation.reconcile(settlement,lines);
        settlementRepository.save(settlement);
        lines.forEach(l -> { l.setSettlement(settlement); l.setCalculationSnapshot(snapshot); });
        lineRepository.saveAll(lines);
        return view(settlement);
    }

    @Transactional
    public DriverSettlementView transition(UUID id, String target, UUID actor) {
        if (actor == null || !employeeRepository.existsById(actor)) throw new BadRequestException("SETTLEMENT_ACTOR_REQUIRED", "Persisted approval/lock actor required");
        if (Set.of("APPROVED","LOCKED").contains(target)) revenueGuard.lockSources(id);
        DriverSettlement s = settlementRepository.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Settlement not found"));
        String state = s.getStatus();
        if (target.equals(state)) return view(s); // retry-safe command response
        if (Set.of("APPROVED","LOCKED").contains(target)) revenueGuard.requireFresh(s,lineRepository.findBySettlementIdOrderById(id));
        settlementReconciliation.reconcile(s,lineRepository.findBySettlementIdOrderById(id));
        if ("CALCULATED".equals(target) && "VALIDATION_REQUIRED".equals(state)) { s.setStatus(target); s.setValidationReason(null); }
        else if ("IN_REVIEW".equals(target) && "CALCULATED".equals(state)) { s.setStatus(target); s.setReviewedAt(now()); s.setReviewedBy(actor); }
        else if ("APPROVED".equals(target) && "IN_REVIEW".equals(state)) { s.setStatus(target); s.setApprovedAt(now()); s.setApprovedBy(actor); }
        else if ("LOCKED".equals(target) && "APPROVED".equals(state)) { s.setLockedAt(now()); s.setLockedBy(actor); projectAttributedLines(s); s.setStatus(target); }
        else throw new BadRequestException("INVALID_SETTLEMENT_TRANSITION", "Cannot transition settlement from " + state + " to " + target);
        return view(settlementRepository.save(s));
    }

    @Transactional
    public DriverSettlementView requireValidation(UUID id, String reason, UUID actor) {
        if (actor==null || !employeeRepository.existsById(actor) || reason==null || reason.isBlank() || reason.length()>1000)
            throw new BadRequestException("SETTLEMENT_VALIDATION_INPUT_REQUIRED", "Persisted actor and validation reason required");
        var s=settlementRepository.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Settlement not found"));
        if (!Set.of("CALCULATED","VALIDATION_REQUIRED","IN_REVIEW","APPROVED").contains(s.getStatus()))
            throw new BadRequestException("INVALID_SETTLEMENT_TRANSITION", "Finalized settlements cannot be reopened for validation");
        s.setStatus("VALIDATION_REQUIRED"); s.setValidationReason(reason); s.setReviewedAt(null); s.setReviewedBy(null); s.setApprovedAt(null); s.setApprovedBy(null);
        return view(settlementRepository.save(s));
    }

    @Transactional(readOnly = true)
    public List<DriverSettlementView> list(UUID payPeriodId, UUID driverId, String status, String settlementType) {
        List<DriverSettlement> settlements = settlementRepository.findSettlements(payPeriodId, driverId, status, settlementType);
        return settlements.stream().map(s -> DriverSettlementView.from(s, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public DriverSettlementView get(UUID id) {
        DriverSettlement settlement = settlementRepository.findById(id).orElseThrow(() -> new BadRequestException("Settlement not found"));
        return view(settlement);
    }

    @Transactional
    public DriverSettlementView createAdjustment(UUID parentId, SettlementAdjustmentRequest request) {
        DriverSettlement parent = settlementRepository.findByIdForUpdate(parentId).orElseThrow(() -> new BadRequestException("Parent settlement not found"));
        requireFinalizedParent(parent);
        if (request.idempotencyKey()==null || request.idempotencyKey().isBlank() || request.idempotencyKey().length()>120
                || request.reason()==null || request.reason().isBlank() || request.reason().length()>300 || request.lines()==null || request.lines().isEmpty())
            throw new BadRequestException("ADJUSTMENT_INPUT_REQUIRED", "Idempotency key, reason and explicit lines required");
        var prior = settlementRepository.findByParentSettlementIdAndRequestKey(parentId,request.idempotencyKey());
        if (prior.isPresent()) {
            var old = prior.get();
            if (!objectMapper.readTree(old.getCalculationSnapshot().getInputJson()).get("request").equals(objectMapper.readTree(json(request))))
                throw new BadRequestException("ADJUSTMENT_IDEMPOTENCY_CONFLICT", "Idempotency key already has different financial inputs");
            return view(old);
        }
        List<SettlementLine> lines = new ArrayList<>();
        for (SettlementAdjustmentRequest.Line item : request.lines()) {
            if (item.amount()==null || item.amount().signum()<=0 || item.lineClass()==null || !Set.of("EARNING","DEDUCTION","REIMBURSEMENT").contains(item.lineClass())
                    || item.lineType()==null || item.lineType().isBlank() || item.lineType().length()>50 || item.description()==null || item.description().isBlank() || item.description().length()>300)
                throw new BadRequestException("ADJUSTMENT_LINE_INVALID", "Explicit valid line class/type/description/positive amount required");
            Load load = item.loadId() == null ? null : loadRepository.findById(item.loadId()).orElseThrow(() -> new BadRequestException("Adjustment load not found"));
            Trip trip = item.tripId() == null ? null : tripRepository.findById(item.tripId()).orElseThrow(() -> new BadRequestException("Adjustment trip not found"));
            if (load == null && trip != null) {
                List<Load> tripLoads = trip.getStops().stream().map(TripStop::getLoad).filter(Objects::nonNull).distinct().toList();
                if (tripLoads.size() == 1) load = tripLoads.getFirst();
            }
            if (trip != null && load != null) {
                UUID adjustmentLoadId = load.getId();
                if (trip.getStops().stream().noneMatch(stop -> stop.getLoad()!=null && adjustmentLoadId.equals(stop.getLoad().getId())))
                    throw new BadRequestException("Adjustment load is not attributed to the supplied trip");
            }
            String costSource = "EARNING".equals(item.lineClass()) || ("DEDUCTION".equals(item.lineClass()) && "DRIVER_COST_CORRECTION".equals(item.lineType()))
                    ? "DRIVER_SETTLEMENT_ADJUSTMENT" : "SETTLEMENT_ADJUSTMENT_NON_COST";
            SettlementLine line = line(item.lineType().trim().toUpperCase(Locale.ROOT), item.lineClass().trim().toUpperCase(Locale.ROOT),
                    trip, load, item.description(), BigDecimal.ONE, "ADJUSTMENT", item.amount(), item.amount(), parent.getCurrency(),
                    costSource, UUID.randomUUID());
            lines.add(line);
        }
        return createChild(parent, "ADJUSTMENT", request.reason(), lines, request.idempotencyKey(), request);
    }

    @Transactional
    public DriverSettlementView reverse(UUID parentId, String reason) {
        DriverSettlement parent = settlementRepository.findByIdForUpdate(parentId).orElseThrow(() -> new BadRequestException("Parent settlement not found"));
        requireFinalizedParent(parent);
        if (reason==null || reason.isBlank() || reason.length()>300) throw new BadRequestException("REVERSAL_REASON_REQUIRED", "Explicit reversal reason required");
        var prior = settlementRepository.findByParentSettlementIdAndSettlementType(parentId,"REVERSAL");
        if (prior.isPresent()) return view(prior.get());
        settlementReconciliation.reconcile(parent,lineRepository.findBySettlementIdOrderById(parentId));
        List<SettlementLine> reversalLines = new ArrayList<>();
        for (SettlementLine original : lineRepository.findBySettlementIdOrderById(parentId)) {
            String reversedClass = switch (original.getLineClass()) {
                case "EARNING" -> "DEDUCTION";
                case "DEDUCTION" -> "EARNING";
                case "REIMBURSEMENT" -> "DEDUCTION";
                default -> throw new BadRequestException("Unsupported settlement line class " + original.getLineClass());
            };
            String source = hasCostImpact(parent,original) ? "DRIVER_SETTLEMENT_REVERSAL" : "SETTLEMENT_REVERSAL_NON_COST";
            SettlementLine line = line("REVERSAL", reversedClass, original.getTrip(), original.getLoad(),
                    reason, BigDecimal.ONE, "REVERSAL", original.getAmount(),
                    original.getAmount(), parent.getCurrency(), source, original.getId());
            reversalLines.add(line);
        }
        return createChild(parent, "REVERSAL", reason, reversalLines, null, null);
    }

    private void requireFinalizedParent(DriverSettlement parent) {
        if (!Set.of("LOCKED","PAYMENT_SCHEDULED","PAID").contains(parent.getStatus()))
            throw new BadRequestException("SETTLEMENT_NOT_FINALIZED", "Corrections require a finalized parent; never unlock history");
    }

    private boolean hasCostImpact(DriverSettlement parent, SettlementLine line) {
        return "ORIGINAL".equals(parent.getSettlementType()) && "EARNING".equals(line.getLineClass())
                || "ADJUSTMENT".equals(parent.getSettlementType()) && "DRIVER_SETTLEMENT_ADJUSTMENT".equals(line.getSourceType())
                || "REVERSAL".equals(parent.getSettlementType()) && "DRIVER_SETTLEMENT_REVERSAL".equals(line.getSourceType());
    }

    private DriverSettlementView createChild(DriverSettlement parent, String type, String reason, List<SettlementLine> lines, String requestKey, Object request) {
        Integer maxSequence = settlementRepository.findMaxSequenceForParent(parent.getId());
        int sequence = maxSequence == null ? 1 : maxSequence + 1;
        BigDecimal gross = sumClass(lines, "EARNING", parent.getCurrency());
        BigDecimal deductions = sumClass(lines, "DEDUCTION", parent.getCurrency());
        BigDecimal reimbursements = sumClass(lines, "REIMBURSEMENT", parent.getCurrency());
        BigDecimal net = money(gross.subtract(deductions).add(reimbursements), parent.getCurrency());
        UUID id = UUID.randomUUID();
        Map<String,Object> input = new LinkedHashMap<>(Map.of("parentSettlementId", parent.getId(), "type", type, "sequence", sequence, "reason", reason,
                "lines", lines.stream().map(l -> Map.of("amount", l.getAmount(), "class", l.getLineClass(), "loadId", l.getLoad() == null ? "" : l.getLoad().getId().toString(),
                        "sourceId", Objects.toString(l.getSourceId(), ""))).toList()));
        if (request!=null) input.put("request",request);
        Map<String,Object> result = Map.of("grossEarnings", gross, "deductions", deductions, "reimbursements", reimbursements, "settlementNet", net);
        CalculationSnapshot snapshot = snapshotService.recordSnapshot("DRIVER_SETTLEMENT", id, type, "DriverPayEngine", "2",
                "DRIVER_PAY_POLICY", parent.getPayPolicy().getId(), String.valueOf(parent.getPayPolicyVersion()), json(input), json(result),
                parent.getCurrency(), null, null);
        DriverSettlement child = new DriverSettlement();
        child.setId(id); child.setSettlementNumber("DS-" + id); child.setDriver(parent.getDriver()); child.setPayPeriod(parent.getPayPeriod());
        child.setSettlementType(type); child.setParentSettlement(parent); child.setSequenceNumber(sequence); child.setPayPolicy(parent.getPayPolicy());
        child.setPayPolicyVersion(parent.getPayPolicyVersion()); child.setStatus("CALCULATED"); child.setCurrency(parent.getCurrency());
        child.setGrossEarnings(gross); child.setDeductionAmount(deductions); child.setReimbursementAmount(reimbursements);
        child.setBonusAmount(gross); child.setRequestKey(requestKey);
        child.setSettlementNet(net); child.setCalculationSnapshot(snapshot); child.setCalculatedAt(now());
        settlementReconciliation.reconcile(child,lines);
        settlementRepository.save(child);
        lines.forEach(l -> { l.setSettlement(child); l.setCalculationSnapshot(snapshot); });
        lineRepository.saveAll(lines);
        return view(child);
    }

    private BigDecimal sumClass(List<SettlementLine> lines, String lineClass, String currency) {
        return money(lines.stream().filter(l -> lineClass.equals(l.getLineClass())).map(SettlementLine::getAmount)
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
        OffsetDateTime end = assignment.getEffectiveTo(), completed = assignment.getTrip().getCompletedAt();
        if (end == null || (completed != null && completed.isBefore(end))) end = completed;
        if (end == null) {
            if (assignment.getActualMiles()!=null && assignment.getActualMiles().signum()>0)
                throw new BadRequestException("ASSIGNMENT_WORK_DATE_VALIDATION_REQUIRED", "Actual assignment miles require a closing/completion event");
            return null;
        }
        if (assignment.getEffectiveFrom()==null || end.isBefore(assignment.getEffectiveFrom()))
            throw new BadRequestException("ASSIGNMENT_WORK_DATE_VALIDATION_REQUIRED", "Assignment closing event precedes its start");
        return utcDate(end);
    }

    private LocalDate utcDate(OffsetDateTime date) { return date.withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDate(); }

    private void preventSourceReuse(List<SettlementLine> lines, List<SettlementLine> previous) {
        for (var line : lines) for (var old : previous) {
            if ("DAILY".equals(line.getLineType()) && "DAILY".equals(old.getLineType()) && old.getBusinessDate()==null
                    && !line.getBusinessDate().isBefore(old.getSettlement().getPayPeriod().getStartDate())
                    && !line.getBusinessDate().isAfter(old.getSettlement().getPayPeriod().getEndDate()))
                throw new BadRequestException("SETTLEMENT_LEGACY_SOURCE_DATE_UNAVAILABLE", "Overlapping historical daily pay has no snapshotted business date");
            boolean same = "LOAD".equals(line.getLineType()) && "LOAD".equals(old.getLineType())
                    && line.getLoad()!=null && old.getLoad()!=null && line.getLoad().getId().equals(old.getLoad().getId());
            same |= "DAILY".equals(line.getLineType()) && "DAILY".equals(old.getLineType())
                    && line.getBusinessDate()!=null && line.getBusinessDate().equals(old.getBusinessDate());
            same |= Objects.equals(line.getSourceType(),old.getSourceType()) && line.getSourceId()!=null
                    && line.getSourceId().equals(old.getSourceId()) && line.getLineType().equals(old.getLineType());
            if (same) throw new BadRequestException("SETTLEMENT_SOURCE_ALREADY_USED", "Original driver pay source is already reserved by another settlement; use adjustment/reversal");
        }
    }

    private void projectAttributedLines(DriverSettlement settlement) {
        for (SettlementLine line : lineRepository.findBySettlementIdOrderById(settlement.getId())) {
            if (line.getLoad() == null && line.getTrip() == null) continue;
            BigDecimal projectedAmount = line.getAmount();
            String source;
            if ("ORIGINAL".equals(settlement.getSettlementType()) && "EARNING".equals(line.getLineClass())) source = "DRIVER_SETTLEMENT";
            else if ("REVERSAL".equals(settlement.getSettlementType()) && "DRIVER_SETTLEMENT_REVERSAL".equals(line.getSourceType())) {
                source = "DRIVER_SETTLEMENT_REVERSAL";
                if ("DEDUCTION".equals(line.getLineClass())) projectedAmount = projectedAmount.negate();
            } else if ("ADJUSTMENT".equals(settlement.getSettlementType()) && "DRIVER_SETTLEMENT_ADJUSTMENT".equals(line.getSourceType()) &&
                    Set.of("EARNING", "DEDUCTION").contains(line.getLineClass())) {
                source = "DRIVER_SETTLEMENT_ADJUSTMENT";
                if ("DEDUCTION".equals(line.getLineClass())) projectedAmount = projectedAmount.negate();
            } else continue;
            if (projectedAmount.signum() == 0) continue;
            var prior = shipmentCostRepository.findBySourceTypeAndSourceId(source,line.getId());
            if (prior.isPresent()) {
                var old = prior.get();
                CurrencyGuard.requireSameCurrency(line.getCurrency(),old.getCurrency());
                if (old.getAmount().compareTo(projectedAmount)!=0 || !Objects.equals(old.getLoad()==null?null:old.getLoad().getId(),line.getLoad()==null?null:line.getLoad().getId())
                        || !Objects.equals(old.getTrip()==null?null:old.getTrip().getId(),line.getTrip()==null?null:line.getTrip().getId())
                        || old.getDriver()==null || !old.getDriver().getId().equals(settlement.getDriver().getId()) || !"ACTUAL".equals(old.getCostBasis())
                        || !Set.of("APPROVED","POSTED").contains(old.getStatus()))
                    throw new BadRequestException("SETTLEMENT_COST_SOURCE_CONFLICT", "Existing projected cost differs from finalized settlement line");
                continue;
            }
            ShipmentCost cost = new ShipmentCost(); cost.setLoad(line.getLoad()); cost.setTrip(line.getTrip());
            cost.setDriver(settlement.getDriver()); cost.setCategory("DRIVER"); cost.setCostBasis("ACTUAL"); cost.setStatus("APPROVED");
            cost.setApprovedAt(settlement.getLockedAt()); cost.setApprovedBy(settlement.getLockedBy());
            cost.setSourceType(source); cost.setSourceId(line.getId()); cost.setAmount(projectedAmount); cost.setCurrency(line.getCurrency());
            cost.setCalculationSnapshot(line.getCalculationSnapshot()); cost.setNote("Locked settlement " + settlement.getSettlementNumber() + " / " + line.getDescription());
            cost.setIncurredAt(settlement.getLockedAt() == null ? now() : settlement.getLockedAt());
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
            if (!"DRIVER_PAY_POLICY_UNAVAILABLE".equals(noPolicyAtPeriodStart.getCode())) throw noPolicyAtPeriodStart;
            for (TripDriverAssignment assignment : assignments) {
                LocalDate date = tripWorkDate(assignment, from, through);
                if (date==null || date.isBefore(from) || date.isAfter(through)) continue;
                try { return resolvePolicy(driverId, date); } catch (BadRequestException missing) {
                    if (!"DRIVER_PAY_POLICY_UNAVAILABLE".equals(missing.getCode())) throw missing;
                }
            }
            for (TimeEntry entry : entries) {
                if (entry.getDate() == null) continue;
                try { return resolvePolicy(driverId, utcDate(entry.getDate())); } catch (BadRequestException missing) {
                    if (!"DRIVER_PAY_POLICY_UNAVAILABLE".equals(missing.getCode())) throw missing;
                }
            }
            throw noPolicyAtPeriodStart;
        }
    }

    private SettlementLine line(String type, String lineClass, Trip trip, Load load, String description,
            BigDecimal quantity, String unit, BigDecimal rate, BigDecimal amount, String currency, String sourceType, UUID sourceId) {
        SettlementLine l = new SettlementLine(); l.setLineType(type); l.setLineClass(lineClass); l.setTrip(trip); l.setLoad(load);
        l.setDescription(description); l.setQuantity(quantity); l.setUnit(unit); l.setRate(rate); l.setAmount(money(amount,currency));
        l.setCurrency(currency); l.setSourceType(sourceType); l.setSourceId(sourceId); return l;
    }
    private Load uniqueLoad(List<Load> loads) { return loads.size() == 1 ? loads.getFirst() : null; }
    private OffsetDateTime now() { return OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private BigDecimal money(BigDecimal amount, String currency) { return rounding.money(amount,currency,FinancialRoundingPolicy.Boundary.ALLOCATION); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (tools.jackson.core.JacksonException e) { throw new IllegalStateException("Cannot snapshot payroll calculation", e); }
    }
}
