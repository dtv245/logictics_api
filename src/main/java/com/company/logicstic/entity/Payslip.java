package com.company.logicstic.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="payslips") @Getter @Setter @NoArgsConstructor
public class Payslip {
 @Id @GeneratedValue @UuidGenerator @Column(nullable=false,updatable=false) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payroll_run_item_id",nullable=false,unique=true) private PayrollRunItem item;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="driver_id",nullable=false) private Employee driver;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="snapshot_json",nullable=false,columnDefinition="jsonb") private String snapshotJson;
 @Column(name="pdf_document_id") private UUID pdfDocumentId;
 @Column(name="pdf_uri",length=1000) private String pdfUri;
 @Column(name="issued_at",nullable=false) private OffsetDateTime issuedAt;
 @Column(name="issued_by") private UUID issuedBy;
 @Column(name="renderer_version",length=60) private String rendererVersion;
 @Column(name="pdf_sha256",length=64) private String pdfSha256;
 @Column(name="pdf_content",columnDefinition="bytea") private byte[] pdfContent;
}
