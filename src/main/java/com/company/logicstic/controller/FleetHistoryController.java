package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
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

@RestController @RequestMapping("/api/fleet") @RequiredArgsConstructor
public class FleetHistoryController {
    private final FleetHistoryService fleet;private final EmployeeRepository employees;
    @PostMapping("/policies") public ResponseEntity<ApiResponse<Policy>> publish(@RequestBody Publish body,Authentication auth,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.publish(body,actor(auth)),request));}
    @GetMapping("/policies/{id}") public ResponseEntity<ApiResponse<Policy>> policy(@PathVariable UUID id,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.policy(id),request));}
    @PostMapping("/status-events") public ResponseEntity<ApiResponse<Event>> capture(@RequestBody Capture body,Authentication auth,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.capture(body,actor(auth)),request));}
    @PostMapping("/mileage-attributions") public ResponseEntity<ApiResponse<Mileage>> attribute(@RequestBody Attribute body,Authentication auth,HttpServletRequest request){return ResponseEntity.ok(ApiResponse.success(fleet.attribute(body,actor(auth)),request));}
    private UUID actor(Authentication auth){if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))throw new ForbiddenException("Authenticated fleet actor required");return employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Fleet actor must map to tenant employee")).getId();}
}
