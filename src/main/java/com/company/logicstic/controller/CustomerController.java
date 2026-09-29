package com.company.logicstic.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.customer.CreateCustomerRequest;
import com.company.logicstic.dto.customer.CustomerView;
import com.company.logicstic.service.CustomerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CustomerView>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "name") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<CustomerView> data = customerService.search(search, status, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        CustomerView data = customerService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerView>> create(
            @Valid @RequestBody CreateCustomerRequest body,
            HttpServletRequest request
    ) {
        CustomerView data = customerService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCustomerRequest body,
            HttpServletRequest request
    ) {
        CustomerView data = customerService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}