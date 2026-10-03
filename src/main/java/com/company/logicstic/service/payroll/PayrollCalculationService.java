package com.company.logicstic.service.payroll;

import com.company.logicstic.common.*;
import com.company.logicstic.dto.payroll.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.payroll.policy.*;
import com.company.logicstic.service.payroll.domain.*;
import com.company.logicstic.service.payroll.tax.*;
import com.company.logicstic.service.calculation.CalculationSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class PayrollCalculationService {
 private final PayrollRunRepository runs;
 private final PayrollRunItemRepository items;
 private final PayrollSupplementRepository supplements;
 private final PayPeriodRepository periods;
 private final DriverSettlementRepository settlements;
 private final SettlementLineRepository lines;
 private final PayrollPolicyVersionRepository policies;
 private final PayrollConfigurationService configuration;
 private final PayrollJurisdictionResolver jurisdictionResolver;
 private final PayrollPolicyResolver policyResolver;
 private final PayrollTaxCalculator taxes;
 private final PayrollGrossCalculator grossCalculator;
 private final SettlementReconciliationService settlementReconciliation;
 private final PayrollReconciliationService reconciliation;
 private final FinancialRoundingPolicy rounding;
 private final CalculationSnapshotService snapshots;
 private final ObjectMapper json;

 @Transactional
 public PayrollRunView calculate(CalculatePayrollRequest request) {
  validate(request);
  var period=periods.findByIdForUpdate(request.payPeriodId()).orElseThrow(() -> new BadRequestException("Payroll period not found"));
  var prior=runs.findByRequestKey(request.idempotencyKey());
  if(prior.isPresent()) {
   if(!json.readTree(prior.get().getCalculationInputJson()).equals(json.readTree(serialize(request))))
    throw new BadRequestException("PAYROLL_IDEMPOTENCY_CONFLICT","Request key already has different calculation inputs");
   return view(prior.get());
  }
  if(!"OPEN".equals(period.getStatus())) throw new BadRequestException("PAYROLL_PERIOD_NOT_OPEN","Payroll calculation requires an open period");
  var run=new PayrollRun(); run.setId(UUID.randomUUID()); run.setRunNumber("PR-"+run.getId());
  run.setPayPeriod(period); run.setCurrency(CurrencyGuard.canonical(request.currency())); run.setRequestKey(request.idempotencyKey());
  run.setEffectiveDate(request.effectiveDate()); run.setCalculationInputJson(serialize(request)); run.setStatus("DRAFT");
  runs.saveAndFlush(run); populate(run,request); return view(run);
 }

 @Transactional
 public PayrollRunView recalculate(UUID id) {
  var run=runs.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Payroll run not found"));
  if(!Set.of("VALIDATION_REQUIRED","CALCULATED").contains(run.getStatus()))
   throw new BadRequestException("PAYROLL_RECALCULATION_NOT_ALLOWED","Recalculation requires an unreviewed/unlocked run");
  if(run.getCalculationInputJson()==null)
   throw new BadRequestException("PAYROLL_LEGACY_SNAPSHOT_UNAVAILABLE","Historical payroll has no explicit calculation inputs; do not infer or backfill");
  var request=json.readValue(run.getCalculationInputJson(),CalculatePayrollRequest.class);
  populate(run,request); return view(run);
 }

 @Transactional(readOnly=true)
 public PayrollRunView get(UUID id) {
  return view(runs.findById(id).orElseThrow(() -> new BadRequestException("Payroll run not found")));
 }

 private void populate(PayrollRun run,CalculatePayrollRequest request) {
  var selected=new ArrayList<DriverSettlement>();
  for(var id:request.settlementIds().stream().sorted().toList()) {
   var source=settlements.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Payroll settlement not found"));
   if(!"LOCKED".equals(source.getStatus()) || !source.getPayPeriod().getId().equals(run.getPayPeriod().getId()))
    throw new BadRequestException("PAYROLL_SETTLEMENT_NOT_ELIGIBLE","Only LOCKED settlements in this payroll period are eligible");
   CurrencyGuard.requireSameCurrency(run.getCurrency(),source.getCurrency());
   settlementReconciliation.reconcile(source,lines.findBySettlementIdOrderById(source.getId())); selected.add(source);
  }
  var driverIds=selected.stream().map(s -> s.getDriver().getId()).collect(java.util.stream.Collectors.toSet());
  if(request.jurisdictionOverrides()!=null && !driverIds.containsAll(request.jurisdictionOverrides().keySet())
   || request.taxInputs()!=null && !driverIds.containsAll(request.taxInputs().keySet())
   || request.supplements()!=null && request.supplements().stream().anyMatch(s -> !driverIds.contains(s.driverId())))
   throw new BadRequestException("PAYROLL_INPUT_DRIVER_NOT_SELECTED","Overrides, tax inputs and supplements must belong to selected drivers");
  var grouped=new TreeMap<UUID,List<DriverSettlement>>();
  for(var source:selected) grouped.computeIfAbsent(source.getDriver().getId(),id -> new ArrayList<>()).add(source);
  var existing=items.findByPayrollRunIdOrderById(run.getId());
  var completed=new ArrayList<PayrollRunItem>();
  for(var entry:grouped.entrySet()) {
   var item=existing.stream().filter(i -> i.getDriver().getId().equals(entry.getKey())).findFirst().orElse(null);
   if(item==null) {
    for(var source:entry.getValue()) if(items.existsBySettlementsId(source.getId()))
     throw new BadRequestException("PAYROLL_SETTLEMENT_ALREADY_RESERVED","Settlement is already mapped to another payroll item");
    item=new PayrollRunItem(); item.setId(UUID.randomUUID()); item.setPayrollRun(run);
    item.setDriver(entry.getValue().getFirst().getDriver()); item.setSettlements(new ArrayList<>(entry.getValue()));
   }
   calculateItem(item,entry.getValue(),request);
   items.saveAndFlush(item);
   persistSupplements(item,request);
   completed.add(item);
  }
  run.setCalculatedAt(now()); run.setApprovedAt(null); run.setApprovedBy(null);
  boolean available=completed.stream().allMatch(i -> "AVAILABLE".equals(i.getTaxAvailability()));
  run.setStatus(available?"CALCULATED":"VALIDATION_REQUIRED");
  run.setValidationReason(available?null:completed.stream().filter(i -> i.getValidationReason()!=null).map(PayrollRunItem::getValidationReason).distinct().sorted().reduce((a,b) -> a+","+b).orElse("PAYROLL_VALIDATION_REQUIRED"));
  if(available) reconciliation.requireFinalizable(run,completed);
  var result=new LinkedHashMap<String,Object>(); result.put("engine","PayrollCalculationService");result.put("engineVersion","1");
  result.put("architecture","MULTI_JURISDICTION"); result.put("status",run.getStatus()); result.put("effectiveDate",run.getEffectiveDate());
  result.put("items",completed.stream().map(i -> json.readTree(i.getCalculationSnapshotJson())).toList());
  run.setCalculationSnapshotJson(serialize(result));
  snapshots.recordSnapshot("PAYROLL_RUN",run.getId(),"PAYROLL","PayrollCalculationService","1","MULTI_JURISDICTION",null,null,
   run.getCalculationInputJson(),run.getCalculationSnapshotJson(),run.getCurrency(),null,null);
  runs.saveAndFlush(run);
 }

 private void calculateItem(PayrollRunItem item,List<DriverSettlement> sources,CalculatePayrollRequest request) {
  var currency=item.getPayrollRun().getCurrency();
  var aggregate=grossCalculator.calculate(sources,lines::findBySettlementIdOrderById,id -> lines.findById(id).orElse(null),currency);
  var extra=request.supplements()==null?List.<CalculatePayrollRequest.Supplement>of():request.supplements().stream().filter(s -> s.driverId().equals(item.getDriver().getId())).toList();
  var deductions=aggregate.deductions().add(extra.stream().filter(s -> "DEDUCTION".equals(s.lineClass())).map(CalculatePayrollRequest.Supplement::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
  var reimbursement=aggregate.reimbursements().add(extra.stream().filter(s -> "REIMBURSEMENT".equals(s.lineClass())).map(CalculatePayrollRequest.Supplement::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
  var override=request.jurisdictionOverrides()==null?null:request.jurisdictionOverrides().get(item.getDriver().getId());
  var resolution=jurisdictionResolver.resolve(item.getDriver().getId(),override,request.effectiveDate());
  var context=new PayrollTaxContext(item.getDriver().getId(),resolution.jurisdiction(),resolution.workerClassification(),
   request.effectiveDate(),currency,money(aggregate.gross(),currency),money(deductions,currency),money(reimbursement,currency),aggregate.lines(),
   request.taxInputs()==null?Map.of():request.taxInputs().getOrDefault(item.getDriver().getId(),Map.of()));
  PayrollPolicy policy=null; String reason=resolution.reason();
  if(reason==null) {
   try { policy=policyResolver.resolve(resolution.jurisdiction(),resolution.workerClassification(),request.effectiveDate()); }
   catch(BadRequestException e) {
    if(!Set.of("PAYROLL_POLICY_NOT_CONFIGURED","PAYROLL_POLICY_AMBIGUOUS").contains(e.getCode())) throw e;
    reason=e.getCode();
   }
  }
  if(reason==null && (context.grossAmount().signum()<0 || context.otherDeductionAmount().signum()<0 || context.reimbursementAmount().signum()<0))
   reason="PAYROLL_RECOVERY_POLICY_REQUIRED";
  if(policy!=null) CurrencyGuard.requireSameCurrency(currency,policy.currency());
  var tax=reason==null?taxes.calculate(context,policy):PayrollTaxResult.unavailable(reason,currency,policy==null?null:policy.calculatorKey());
  if(tax==null || !("AVAILABLE".equals(tax.availability()) || "UNAVAILABLE".equals(tax.availability()))) tax=PayrollTaxResult.unavailable("PAYROLL_TAX_RESULT_INVALID",currency,policy==null?null:policy.calculatorKey());
  item.setCurrency(currency); item.setGrossAmount(context.grossAmount()); item.setOtherDeductionAmount(context.otherDeductionAmount());
  item.setReimbursementAmount(context.reimbursementAmount()); item.setEffectiveDate(request.effectiveDate());
  item.setJurisdiction(resolution.jurisdiction()==null?null:configuration.jurisdiction(resolution.jurisdiction()));
  item.setWorkerClassification(resolution.workerClassification()); item.setPayrollPolicy(policy==null?null:policies.findById(policy.id()).orElseThrow());
  item.setPayrollPolicyVersion(policy==null?null:policy.version()); item.setIncomeTaxAmount(null);item.setInsuranceAmount(null);item.setNetAmount(null);
  if("AVAILABLE".equals(tax.availability())) {
   item.setIncomeTaxAmount(money(tax.incomeTaxAmount(),currency)); item.setInsuranceAmount(money(tax.insuranceAmount(),currency));
   var net=money(item.getGrossAmount().subtract(item.getOtherDeductionAmount()).add(item.getReimbursementAmount())
    .subtract(item.getIncomeTaxAmount()).subtract(item.getInsuranceAmount()),currency);
   if(net.signum()<0) {
    tax=new PayrollTaxResult("UNAVAILABLE","PAYROLL_NET_RECOVERY_POLICY_REQUIRED",currency,tax.incomeTaxAmount(),tax.insuranceAmount(),
         tax.calculatorKey(),tax.calculatorVersion(),tax.inputs(),tax.outputs());
    item.setIncomeTaxAmount(null);item.setInsuranceAmount(null);
   }
   else item.setNetAmount(net);
  }
  item.setTaxAvailability(tax.availability());item.setValidationReason("AVAILABLE".equals(tax.availability())?null:Objects.requireNonNullElse(tax.reason(),"PAYROLL_TAX_RESULT_UNAVAILABLE"));
  item.setStatus("AVAILABLE".equals(tax.availability())?"CALCULATED":"VALIDATION_REQUIRED");
  var snapshot=new LinkedHashMap<String,Object>(); snapshot.put("driverId",item.getDriver().getId());snapshot.put("itemId",item.getId());
  snapshot.put("jurisdictionResolution",resolution);snapshot.put("jurisdiction",resolution.jurisdiction());
  snapshot.put("workerClassification",resolution.workerClassification());snapshot.put("policy",policy);
  snapshot.put("policyId",policy==null?null:policy.id());snapshot.put("policyVersion",policy==null?null:policy.version());
  snapshot.put("effectiveDate",request.effectiveDate());snapshot.put("taxContext",context);snapshot.put("taxResult",tax);
  snapshot.put("settlementIds",sources.stream().map(DriverSettlement::getId).sorted().toList());
  snapshot.put("supplements",extra);snapshot.put("grossAmount",item.getGrossAmount());snapshot.put("otherDeductionAmount",item.getOtherDeductionAmount());
  snapshot.put("reimbursementAmount",item.getReimbursementAmount());snapshot.put("netAmount",item.getNetAmount());
  snapshot.put("availability",item.getTaxAvailability());snapshot.put("reason",item.getValidationReason());snapshot.put("roundingPolicyVersion",rounding.version());
  item.setCalculationSnapshotJson(serialize(snapshot));
  snapshots.recordSnapshot("PAYROLL_ITEM",item.getId(),"PAYROLL","PayrollCalculationService","1","PAYROLL_POLICY",
   policy==null?null:policy.id(),policy==null?null:String.valueOf(policy.version()),serialize(context),item.getCalculationSnapshotJson(),currency,null,null);
 }

 private void persistSupplements(PayrollRunItem item,CalculatePayrollRequest request) {
  if(request.supplements()==null) return;
  for(var input:request.supplements()) if(input.driverId().equals(item.getDriver().getId())) {
   var existing=supplements.findById(input.sourceId());
   if(existing.isPresent()) {
    if(!existing.get().getItem().getId().equals(item.getId())) throw new BadRequestException("PAYROLL_SUPPLEMENT_ALREADY_RESERVED","Supplement already belongs to another payroll item");
    continue;
   }
   var row=new PayrollSupplement();row.setId(input.sourceId());row.setItem(item);row.setLineClass(input.lineClass());
   row.setDescription(input.description());row.setAmount(money(input.amount(),item.getCurrency()));supplements.saveAndFlush(row);
  }
 }

 private void validate(CalculatePayrollRequest request) {
  if(request==null || request.idempotencyKey()==null || request.idempotencyKey().isBlank() || request.idempotencyKey().length()>120
   || request.payPeriodId()==null || request.effectiveDate()==null || request.settlementIds()==null || request.settlementIds().isEmpty()
   || request.settlementIds().stream().anyMatch(Objects::isNull) || new HashSet<>(request.settlementIds()).size()!=request.settlementIds().size())
   throw new BadRequestException("PAYROLL_INPUT_INVALID","Explicit request key, period, effective date and unique settlements required");
  var currency=CurrencyGuard.canonical(request.currency());var supplementIds=new HashSet<UUID>();
  if(request.supplements()!=null) for(var s:request.supplements()) {
   if(s==null || s.sourceId()==null || !supplementIds.add(s.sourceId()) || s.driverId()==null || s.amount()==null || s.amount().signum()<=0
      || s.description()==null || s.description().isBlank() || s.description().length()>300 || s.lineClass()==null || !Set.of("DEDUCTION","REIMBURSEMENT").contains(s.lineClass())
      || money(s.amount(),currency).compareTo(s.amount())!=0)
    throw new BadRequestException("PAYROLL_SUPPLEMENT_INVALID","Unique explicit nonnegative monetary deduction/reimbursement inputs required");
  }
  if(request.taxInputs()!=null && request.taxInputs().entrySet().stream().anyMatch(e -> e.getKey()==null || e.getValue()==null
    || e.getValue().entrySet().stream().anyMatch(v -> v.getKey()==null || v.getValue()==null)))
   throw new BadRequestException("PAYROLL_TAX_INPUT_INVALID","Explicit tax input keys/values must be present");
 }
 public PayrollRunView view(PayrollRun run) {
  var result=items.findByPayrollRunIdOrderById(run.getId()).stream().map(i -> new PayrollRunView.Item(i.getId(),i.getDriver().getId(),
   i.getStatus(),i.getCurrency(),i.getGrossAmount(),i.getIncomeTaxAmount(),i.getInsuranceAmount(),i.getOtherDeductionAmount(),
   i.getReimbursementAmount(),i.getNetAmount(),i.getTaxAvailability(),i.getValidationReason(),i.getJurisdiction()==null?null:i.getJurisdiction().toDomain(),
   i.getWorkerClassification(),i.getPayrollPolicy()==null?null:i.getPayrollPolicy().getId(),i.getPayrollPolicyVersion(),i.getEffectiveDate(),
   i.getSettlements().stream().map(DriverSettlement::getId).sorted().toList(),i.getCalculationSnapshotJson())).toList();
  return new PayrollRunView(run.getId(),run.getRunNumber(),run.getPayPeriod().getId(),run.getCurrency(),run.getStatus(),
    run.getValidationReason(),run.getEffectiveDate(),run.getCalculatedAt(),run.getApprovedAt(),run.getLockedAt(),run.getPaidAt(),result);
 }
 private BigDecimal money(BigDecimal value,String currency) {return rounding.money(value,currency,FinancialRoundingPolicy.Boundary.ALLOCATION);}
 private String serialize(Object value) {return json.writeValueAsString(value);}
 private OffsetDateTime now() {return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);}
}
