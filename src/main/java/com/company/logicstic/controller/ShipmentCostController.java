package com.company.logicstic.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.cost.CreateShipmentCostRequest;
import com.company.logicstic.dto.cost.ShipmentCostView;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.cost.CostAllocator;
import com.company.logicstic.service.cost.ShipmentCostEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loads/{loadId}/costs")
@RequiredArgsConstructor
public class ShipmentCostController {

    private final ShipmentCostEngine shipmentCostEngine;
    private final CostAllocator costAllocator;
    private final EmployeeRepository employeeRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentCostView>> createCost(
            @PathVariable UUID loadId,
            @Valid @RequestBody CreateShipmentCostRequest body,
            HttpServletRequest request
    ) {
        ShipmentCostView data = shipmentCostEngine.createCost(loadId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PostMapping("/sync-expenses")
    public ResponseEntity<ApiResponse<Integer>> syncExpenses(
            @PathVariable UUID loadId,
            HttpServletRequest request
    ) {
        int count = shipmentCostEngine.syncAllExpensesForLoad(loadId);
        return ResponseEntity.ok(ApiResponse.success(count, request));
    }

    @PostMapping("/allocate-maintenance")
    public ResponseEntity<ApiResponse<ShipmentCostView>> allocateMaintenance(
            @PathVariable UUID loadId,
            @RequestParam BigDecimal truckMaintenanceCpm,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        ShipmentCostView data = costAllocator.allocateMaintenanceCost(loadId, truckMaintenanceCpm, currency);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{costId}/verify")
    public ResponseEntity<ApiResponse<ShipmentCostView>> verifyCost(
            @PathVariable UUID loadId,
            @PathVariable UUID costId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        ShipmentCostView data = shipmentCostEngine.verifyCost(loadId, costId, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{costId}/approve")
    public ResponseEntity<ApiResponse<ShipmentCostView>> approveCost(
            @PathVariable UUID loadId,
            @PathVariable UUID costId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        ShipmentCostView data = shipmentCostEngine.approveCost(loadId, costId, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/{costId}/void")
    public ResponseEntity<ApiResponse<ShipmentCostView>> voidCost(
            @PathVariable UUID loadId,
            @PathVariable UUID costId,
            @RequestParam String reason,
            Authentication authentication,
            HttpServletRequest request
    ) {
        ShipmentCostView data = shipmentCostEngine.voidCost(loadId, costId, reason, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentCostView>>> getCosts(
            @PathVariable UUID loadId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request
    ) {
        var pageable = PaginationValidator.pageRequest(page, pageSize, Sort.by("incurredAt").descending());
        var data = shipmentCostEngine.getCostsForLoad(loadId, pageable);
        return ResponseEntity.ok(ApiResponse.successPage(data, request));
    }

    private UUID actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ForbiddenException("Authenticated cost actor required");
        }
        return employeeRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenException("Cost actor must map to an employee"))
                .getId();
    }
}
