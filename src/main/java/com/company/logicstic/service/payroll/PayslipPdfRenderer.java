package com.company.logicstic.service.payroll;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import tools.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
@Component
public class PayslipPdfRenderer {
 private final Resource fontResource;
 public PayslipPdfRenderer(@Value("${app.payroll.payslip.font-resource:classpath:fonts/DejaVuSans.ttf}") Resource fontResource) {this.fontResource=fontResource;}
 public String version(){return "LOGISTICSX_PAYSLIP_PDF_1";}
 public byte[] render(JsonNode snapshot) {
  var payroll=snapshot.get("calculation");
  var currency=snapshot.get("currency").asText();
  var rows=new ArrayList<String>();rows.add("Payroll payslip");rows.add("Employee: "+snapshot.get("employeeName").asText());
  rows.add("Employee ID: "+snapshot.get("driverId").asText());rows.add("Payroll run: "+snapshot.get("runNumber").asText());
  rows.add("Period: "+snapshot.get("periodStart").asText()+" / "+snapshot.get("periodEnd").asText());
  rows.add("Issued: "+snapshot.get("issuedAt").asText());rows.add("Currency: "+snapshot.get("currency").asText());
  rows.add("Worker classification: "+payroll.get("workerClassification").asText());
  rows.add("Jurisdiction: "+payroll.get("jurisdiction").toString());
  rows.add("Policy: "+payroll.get("policyId").asText()+" / "+payroll.get("policyVersion").asText());
  for(var field:List.of("grossAmount","otherDeductionAmount","reimbursementAmount","netAmount")) rows.add(field+": "+amount(payroll.get(field),currency));
  rows.add("Income tax: "+amount(snapshot.get("incomeTaxAmount"),currency));
  rows.add("Insurance: "+amount(snapshot.get("insuranceAmount"),currency));
  rows.add("Settlement inputs:");
  for(var id:payroll.get("settlementIds")) rows.add("  "+id.asText());
  try(var document=new PDDocument();var input=fontResource.getInputStream();var output=new ByteArrayOutputStream()) {
   var font=PDType0Font.load(document,input,true);
   var wrapped=new ArrayList<String>();
   for(var text:rows) {
    // Wrap by rendered width, never truncate monetary values or identifiers.
    var current=new StringBuilder();
    for(int cp:text.codePoints().toArray()) {
     String character=new String(Character.toChars(cp));
     try {font.encode(character);}
     catch(IllegalArgumentException e) {throw new BadRequestException("PAYSLIP_FONT_UNSUPPORTED","Configure a payslip font covering employee/jurisdiction characters");}
     if(current.length()>0 && font.getStringWidth(current.toString()+character)/1000*10>510) {wrapped.add(current.toString());current.setLength(0);}
     current.append(character);
    }
    wrapped.add(current.toString());
   }
   for(int start=0;start<wrapped.size();start+=45) {
    var page=new PDPage();document.addPage(page);
    try(var stream=new PDPageContentStream(document,page)) {
     stream.beginText();stream.setFont(font,10);stream.setLeading(15);stream.newLineAtOffset(45,740);
     for(var text:wrapped.subList(start,Math.min(start+45,wrapped.size()))) {stream.showText(text);stream.newLine();}
     stream.endText();
    }
   }
   document.getDocumentInformation().setTitle("Payroll payslip");document.save(output);return output.toByteArray();
  } catch(IOException e) {throw new BadRequestException("PAYSLIP_PDF_UNAVAILABLE","Payslip PDF/font could not be generated");}
 }
 private String amount(JsonNode value,String currency) {
  return new java.math.BigDecimal(value.asText()).setScale(com.company.logicstic.common.MoneyRoundingPolicy.getScaleForCurrency(currency),
          java.math.RoundingMode.UNNECESSARY).toPlainString();
 }
}
