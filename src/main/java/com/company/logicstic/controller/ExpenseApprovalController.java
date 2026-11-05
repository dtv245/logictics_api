package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.cost.RejectExpenseRequest;
import com.company.logicstic.service.cost.ExpenseApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class ExpenseApprovalController {
    private final ExpenseApprovalService approvals;

    @PostMapping("/api/expenses/{id}/approve")
    public ResponseEntity<ApiResponse<ExpenseApprovalService.ApprovalResult>> approve(
            @PathVariable UUID id, Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(approvals.approve(id, authentication.getName()), request));
    }

    @PostMapping("/api/expenses/{id}/reject")
    public ResponseEntity<ApiResponse<ExpenseApprovalService.ApprovalResult>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectExpenseRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                approvals.reject(id, body.reason(), authentication.getName()), request));
    }
}
