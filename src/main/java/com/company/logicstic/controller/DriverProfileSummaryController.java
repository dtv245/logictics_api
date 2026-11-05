package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.DriverExpiringItemView;
import com.company.logicstic.dto.driverprofile.DriverProfileSummaryView;
import com.company.logicstic.service.DriverExpiryService;
import com.company.logicstic.service.DriverProfileSummaryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers")
public class DriverProfileSummaryController {
    private final DriverExpiryService expiryService;
    private final DriverProfileSummaryService summaryService;

    public DriverProfileSummaryController(DriverExpiryService expiryService,
                                          DriverProfileSummaryService summaryService) {
        this.expiryService = expiryService;
        this.summaryService = summaryService;
    }

    @GetMapping("/expiring")
    public ResponseEntity<ApiResponse<PagedResponse<DriverExpiringItemView>>> expiring(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(expiryService.list(days, type, page, pageSize), request));
    }

    @GetMapping("/{driverId}/profile-summary")
    public ResponseEntity<ApiResponse<DriverProfileSummaryView>> summary(
            @PathVariable UUID driverId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(summaryService.get(driverId), request));
    }
}
