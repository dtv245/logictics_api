package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.DriverBankAccountView;
import com.company.logicstic.dto.driverprofile.DriverDependentView;
import com.company.logicstic.service.DriverBankAccountService;
import com.company.logicstic.service.DriverDependentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver/me")
public class DriverSelfPersonalRecordsController {
    private final DriverDependentService dependents;
    private final DriverBankAccountService bankAccounts;

    public DriverSelfPersonalRecordsController(DriverDependentService dependents,
            DriverBankAccountService bankAccounts) {
        this.dependents = dependents;
        this.bankAccounts = bankAccounts;
    }

    @GetMapping("/dependents")
    public ResponseEntity<ApiResponse<PagedResponse<DriverDependentView>>> dependents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "fullName") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(dependents.listMine(page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<PagedResponse<DriverBankAccountView>>> bankAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bankAccounts.listMine(page, pageSize, orderBy, descending), request));
    }
}
