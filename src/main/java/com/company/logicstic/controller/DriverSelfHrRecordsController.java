package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.DriverInsuranceView;
import com.company.logicstic.dto.driverprofile.EmploymentContractView;
import com.company.logicstic.service.DriverInsuranceService;
import com.company.logicstic.service.EmploymentContractService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver/me")
public class DriverSelfHrRecordsController {
    private final EmploymentContractService contracts;
    private final DriverInsuranceService insurances;

    public DriverSelfHrRecordsController(EmploymentContractService contracts, DriverInsuranceService insurances) {
        this.contracts = contracts;
        this.insurances = insurances;
    }

    @GetMapping("/contracts")
    public ResponseEntity<ApiResponse<PagedResponse<EmploymentContractView>>> contracts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "effectiveDate") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contracts.listMine(page, pageSize, orderBy, descending), request));
    }

    @GetMapping("/insurances")
    public ResponseEntity<ApiResponse<PagedResponse<DriverInsuranceView>>> insurances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "participationStartDate") String orderBy,
            @RequestParam(defaultValue = "true") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(insurances.listMine(page, pageSize, orderBy, descending), request));
    }
}
