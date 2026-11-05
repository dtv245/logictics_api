package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.DriverEmergencyContactView;
import com.company.logicstic.dto.driverprofile.DriverProfileView;
import com.company.logicstic.service.DriverEmergencyContactService;
import com.company.logicstic.service.DriverProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver/me")
public class DriverSelfProfileController {
    private final DriverProfileService profiles;
    private final DriverEmergencyContactService contacts;

    public DriverSelfProfileController(DriverProfileService profiles, DriverEmergencyContactService contacts) {
        this.profiles = profiles;
        this.contacts = contacts;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<DriverProfileView>> profile(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(profiles.getMine(), request));
    }

    @GetMapping("/emergency-contacts")
    public ResponseEntity<ApiResponse<PagedResponse<DriverEmergencyContactView>>> emergencyContacts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contacts.listMine(page, pageSize, orderBy, descending), request));
    }
}
