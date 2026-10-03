package com.company.logicstic.dto.payroll;
import java.time.OffsetDateTime;
import java.util.UUID;
public record PayslipView(UUID id,UUID payrollItemId,UUID driverId,OffsetDateTime issuedAt,UUID issuedBy,
 String snapshotJson,String pdfUri,String rendererVersion,String pdfSha256) {}
