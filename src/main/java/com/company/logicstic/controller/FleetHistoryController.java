package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.fleet.FleetHistory.*;
import com.company.logicstic.service.fleet.FleetHistoryService;
import com.company.logicstic.service.fleet.FleetHistoryService.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;
import java.util.List;

@RestController @RequestMapping("/api/fleet") @RequiredArgsConstructor
public class FleetHistoryController {
    private final FleetHistoryService fleet;private final EmployeeRepository employees;
    @PostMapping("/policies") public ResponseEntity<ApiResponse<Policy>> publish(@jakarta.validation.Valid @RequestBody Publish body,Authentication auth,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.publish(body,actor(auth)),request));}
    @GetMapping("/policies") public ResponseEntity<ApiResponse<List<Policy>>> policies(
            @RequestParam(defaultValue="1") Integer page,
            @RequestParam(defaultValue="20") Integer pageSize,
            HttpServletRequest request){
        var pageable=PaginationValidator.lookupPageRequest(page,pageSize,Sort.by("policyCode").ascending()
                .and(Sort.by("policyVersion").descending()));
        return ResponseEntity.ok(ApiResponse.successPage(fleet.policies(pageable),request));
    }
    @GetMapping("/policies/{id}") public ResponseEntity<ApiResponse<Policy>> policy(@PathVariable UUID id,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.policy(id),request));}
    @PostMapping("/status-events") public ResponseEntity<ApiResponse<Event>> capture(@jakarta.validation.Valid @RequestBody Capture body,Authentication auth,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.capture(body,actor(auth)),request));}
    @PostMapping("/mileage-attributions") public ResponseEntity<ApiResponse<Mileage>> attribute(@jakarta.validation.Valid @RequestBody Attribute body,Authentication auth,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.attribute(body,actor(auth)),request));}
    private UUID actor(Authentication auth){if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))throw new ForbiddenException("Authenticated fleet actor required");return employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Fleet actor must map to tenant employee")).getId();}
}
