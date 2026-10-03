package com.company.logicstic.repository;
import com.company.logicstic.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PayslipRepository extends JpaRepository<Payslip,UUID> {
 Optional<Payslip> findByItemId(UUID id);
 List<Payslip> findByDriverIdOrderByIssuedAtDesc(UUID id);
}
