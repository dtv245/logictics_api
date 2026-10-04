package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.entity.PayPeriod;
import com.company.logicstic.service.payroll.PayPeriodService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pay-periods")
@RequiredArgsConstructor
public class PayPeriodController {
    private final PayPeriodService service;

    public record Request(@NotBlank @Size(max=50) String periodCode, @NotNull LocalDate startDate,
            @NotNull LocalDate endDate, LocalDate paymentDate) {}

    @GetMapping
    public ResponseEntity<ApiResponse<List<PayPeriod>>> list(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.list(), request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayPeriod>> create(@Valid @RequestBody Request body, HttpServletRequest request) {
        PayPeriod data = service.create(body.periodCode(), body.startDate(), body.endDate(), body.paymentDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }
}
