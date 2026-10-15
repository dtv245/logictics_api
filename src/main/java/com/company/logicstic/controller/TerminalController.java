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
import com.company.logicstic.dto.terminal.CreateTerminalRequest;
import com.company.logicstic.dto.terminal.TerminalView;
import com.company.logicstic.service.TerminalService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/terminals")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TerminalView>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "name") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<TerminalView> data = terminalService.search(search, type, countryCode, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TerminalView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        TerminalView data = terminalService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TerminalView>> create(
            @Valid @RequestBody CreateTerminalRequest body,
            HttpServletRequest request
    ) {
        TerminalView data = terminalService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TerminalView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTerminalRequest body,
            HttpServletRequest request
    ) {
        TerminalView data = terminalService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        terminalService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}
