package com.company.logicstic.service.payroll;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.dto.payroll.PayslipView;
import com.company.logicstic.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.security.MessageDigest;
import java.util.*;
@Service @RequiredArgsConstructor
public class PayslipService {
 private final PayrollRunRepository runs;
 private final PayrollRunItemRepository items;
 private final PayslipRepository payslips;
 private final EmployeeRepository employees;
 private final PayrollReconciliationService reconciliation;
 private final PayslipPdfRenderer renderer;
 private final ObjectMapper json;
 @Transactional
 public List<PayslipView> issue(UUID runId,UUID actor) {
  if(actor==null || !employees.existsById(actor)) throw new BadRequestException("PAYSLIP_ACTOR_REQUIRED","Persisted payroll actor required");
  var run=runs.findByIdForUpdate(runId).orElseThrow(() -> new BadRequestException("Payroll run not found"));
  if(!Set.of("LOCKED","PAYMENT_SCHEDULED","COMPLETED").contains(run.getStatus())) throw new BadRequestException("PAYSLIP_PAYROLL_NOT_LOCKED","Payslips require locked payroll");
  var sourceItems=items.findByPayrollRunIdOrderById(runId);reconciliation.requireFinalizable(run,sourceItems);
  var result=new ArrayList<PayslipView>();
  for(var item:sourceItems) {
   var existing=payslips.findByItemId(item.getId());
   if(existing.isPresent()) {result.add(view(existing.get()));continue;}
   var issued=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
   var snapshot=new LinkedHashMap<String,Object>();snapshot.put("driverId",item.getDriver().getId());
   snapshot.put("employeeName",item.getDriver().getFirstName()+" "+item.getDriver().getLastName());
   snapshot.put("payrollRunId",runId);snapshot.put("runNumber",run.getRunNumber());snapshot.put("payrollItemId",item.getId());
   snapshot.put("periodStart",run.getPayPeriod().getStartDate());snapshot.put("periodEnd",run.getPayPeriod().getEndDate());
   snapshot.put("currency",item.getCurrency());snapshot.put("issuedAt",issued);snapshot.put("issuedBy",actor);
   snapshot.put("incomeTaxAmount",item.getIncomeTaxAmount());snapshot.put("insuranceAmount",item.getInsuranceAmount());
   snapshot.put("approvedAt",run.getApprovedAt());snapshot.put("approvedBy",run.getApprovedBy());
   snapshot.put("lockedAt",run.getLockedAt());snapshot.put("lockedBy",run.getLockedBy());
   snapshot.put("calculation",json.readTree(item.getCalculationSnapshotJson()));
   String data=json.writeValueAsString(snapshot);byte[] pdf=renderer.render(json.readTree(data));
   var slip=new Payslip();slip.setItem(item);slip.setDriver(item.getDriver());slip.setSnapshotJson(data);
   slip.setIssuedAt(issued);slip.setIssuedBy(actor);slip.setPdfContent(pdf);slip.setRendererVersion(renderer.version());
   try {slip.setPdfSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));}
   catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
   payslips.saveAndFlush(slip);result.add(view(slip));
  }
  return List.copyOf(result);
 }
 @Transactional(readOnly=true)
 public Page<PayslipView> mine(UUID employee, Pageable pageable) {
  return payslips.findPageByDriverId(employee, pageable).map(p -> new PayslipView(
   p.getId(), p.getPayrollItemId(), p.getDriverId(), p.getIssuedAt(), p.getIssuedBy(), null,
   "/api/payslips/" + p.getId() + "/pdf", p.getRendererVersion(), p.getPdfSha256()));
 }
 @Transactional(readOnly=true)
 public List<PayslipView> mine(UUID employee) {
  return mine(employee, org.springframework.data.domain.PageRequest.of(0, 100,
   org.springframework.data.domain.Sort.by("issuedAt").descending().and(org.springframework.data.domain.Sort.by("id")))).getContent();
 }
 @Transactional(readOnly=true)
 public PayslipView get(UUID id,UUID employee,boolean payrollRole) {return view(authorized(id,employee,payrollRole));}
 @Transactional(readOnly=true)
 public byte[] pdf(UUID id,UUID employee,boolean payrollRole) {
  var slip=authorized(id,employee,payrollRole);
  if(slip.getPdfContent()==null) throw new BadRequestException("PAYSLIP_PDF_UNAVAILABLE","Historical payslip has no persisted PDF artifact");
  return slip.getPdfContent().clone();
 }
 private Payslip authorized(UUID id,UUID employee,boolean payrollRole) {
  var slip=payslips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
  if(!payrollRole && !slip.getDriver().getId().equals(employee)) throw new ForbiddenException("Payslip belongs to another employee");
  return slip;
 }
 private PayslipView view(Payslip slip) {
  return new PayslipView(slip.getId(),slip.getItem().getId(),slip.getDriver().getId(),slip.getIssuedAt(),slip.getIssuedBy(),
   slip.getSnapshotJson(),"/api/payslips/"+slip.getId()+"/pdf",slip.getRendererVersion(),slip.getPdfSha256());
 }
}
