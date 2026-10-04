package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.load.LoadView;
import com.company.logicstic.dto.load.SetPickupBusinessDateRequest;
import com.company.logicstic.service.rating.LoadPickupBusinessDateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class LoadPickupBusinessDateController {
    private final LoadPickupBusinessDateService dates;
    @PostMapping("/api/loads/{id}/requested-pickup-business-date")
    public ResponseEntity<ApiResponse<LoadView>> remediate(@PathVariable UUID id,
            @RequestBody SetPickupBusinessDateRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(dates.remediate(id, body), request));
    }
}
