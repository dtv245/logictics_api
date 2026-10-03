package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.PayslipPdfRenderer;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PayslipPdfRendererTest {
 private tools.jackson.databind.JsonNode snapshot(int count) {
  var calculation=new LinkedHashMap<String,Object>();
  calculation.put("workerClassification","CONTRACTOR");calculation.put("jurisdiction",Map.of("countryCode","VN","subdivisionCode","HỒ CHÍ MINH"));
  calculation.put("policyId",UUID.randomUUID());calculation.put("policyVersion",1);
  calculation.put("grossAmount","100.00");calculation.put("otherDeductionAmount","2.00");
  calculation.put("reimbursementAmount","5.00");calculation.put("netAmount","93.00");
  calculation.put("settlementIds",java.util.stream.IntStream.range(0,count).mapToObj(i -> UUID.randomUUID()).toList());
  var data=new LinkedHashMap<String,Object>();data.put("employeeName","Nguyễn Văn An");data.put("driverId",UUID.randomUUID());
  data.put("runNumber","PR-FIXTURE");data.put("periodStart","2026-01-01");data.put("periodEnd","2026-01-31");
  data.put("issuedAt","2026-02-01T00:00:00Z");data.put("currency","USD");data.put("incomeTaxAmount","7.00");data.put("insuranceAmount","3.00");
  data.put("calculation",calculation);return JsonMapper.builder().build().valueToTree(data);
 }
 @Test void pdfRoundTripsUnicodeEmployeeAndReconciledAmounts() throws Exception {
  var renderer=new PayslipPdfRenderer(new ClassPathResource("fonts/DejaVuSans.ttf"));var pdf=renderer.render(snapshot(1));
  try(var document=Loader.loadPDF(pdf)) {
   var text=new PDFTextStripper().getText(document);assertTrue(text.contains("Nguyễn Văn An"));assertTrue(text.contains("93.00"));
   assertTrue(text.contains("Income tax: 7.00"));assertEquals(1,document.getNumberOfPages());
  }
 }
 @Test void multiplePagesPreserveEverySourceIdentifier() throws Exception {
  var source=snapshot(100);var pdf=new PayslipPdfRenderer(new ClassPathResource("fonts/DejaVuSans.ttf")).render(source);
  try(var document=Loader.loadPDF(pdf)) {
   assertTrue(document.getNumberOfPages()>1);var text=new PDFTextStripper().getText(document);
   for(var id:source.get("calculation").get("settlementIds")) assertTrue(text.contains(id.asText()));
  }
 }
}
