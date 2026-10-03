package com.company.logicstic.controller;

import com.company.logicstic.entity.PayPeriod;
import com.company.logicstic.service.payroll.PayPeriodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/pay-periods") @RequiredArgsConstructor
public class PayPeriodController {
    private final PayPeriodService service;
    public record Request(@NotBlank @Size(max=50) String periodCode, @NotNull LocalDate startDate,
            @NotNull LocalDate endDate, LocalDate paymentDate) {}
    @GetMapping public List<PayPeriod> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PayPeriod create(@Valid @RequestBody Request request) {
        return service.create(request.periodCode(),request.startDate(),request.endDate(),request.paymentDate());
    }
}
