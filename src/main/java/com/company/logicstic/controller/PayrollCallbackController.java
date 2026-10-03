package com.company.logicstic.controller;
import com.company.logicstic.service.payroll.payment.PayrollCallbackService;
import com.company.logicstic.dto.payroll.PayrollPaymentEventView;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.Map;
@RestController @RequestMapping("/api/payroll/provider-callbacks") @RequiredArgsConstructor
public class PayrollCallbackController {
 private final PayrollCallbackService callbacks;
 @PostMapping("/{provider}") public PayrollPaymentEventView receive(@PathVariable String provider,@RequestBody String originalBody,
   @RequestHeader Map<String,String> headers) {return callbacks.receive(provider,originalBody,headers);}
}
