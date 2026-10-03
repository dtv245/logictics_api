package com.company.logicstic.service.payroll;
import com.company.logicstic.exception.BadRequestException;
import org.springframework.stereotype.Component;
@Component
public class PayrollWorkflow {
 public void requireTransition(String from,String to) {
  boolean allowed=switch(to) {
   case "IN_REVIEW" -> "CALCULATED".equals(from);
   case "APPROVED" -> "IN_REVIEW".equals(from);
   case "LOCKED" -> "APPROVED".equals(from);
   default -> false;
  };
  if(!allowed) throw new BadRequestException("PAYROLL_TRANSITION_INVALID","Payroll review/approval/lock must follow validated workflow");
 }
}
