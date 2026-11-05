package com.company.logicstic.repository;
import com.company.logicstic.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PayslipRepository extends JpaRepository<Payslip,UUID> {
 Optional<Payslip> findByItemId(UUID id);
 List<Payslip> findByDriverIdOrderByIssuedAtDesc(UUID id);

 interface PayslipListRow {
  UUID getId();
  UUID getPayrollItemId();
  UUID getDriverId();
  java.time.OffsetDateTime getIssuedAt();
  UUID getIssuedBy();
  String getRendererVersion();
  String getPdfSha256();
 }

 @Query("""
   select p.id as id, p.item.id as payrollItemId, p.driver.id as driverId,
          p.issuedAt as issuedAt, p.issuedBy as issuedBy,
          p.rendererVersion as rendererVersion, p.pdfSha256 as pdfSha256
   from Payslip p where p.driver.id = :driverId
   """)
 Page<PayslipListRow> findPageByDriverId(@Param("driverId") UUID driverId, Pageable pageable);
}
