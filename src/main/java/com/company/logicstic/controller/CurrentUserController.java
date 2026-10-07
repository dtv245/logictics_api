package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "bearerAuth",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class CurrentUserController {
    private final CurrentUserService currentUsers;

    public CurrentUserController(CurrentUserService currentUsers) {
        this.currentUsers = currentUsers;
    }

    @Operation(summary = "Read the current authenticated identity",
            description = "No caller identity or tenant selectors. Subject and roles come from authenticated security; employee mapping is tenant-local.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authenticated identity"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unresolved or mismatched authenticated tenant"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Ambiguous employee mapping")
    })
    @GetMapping("/api/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> current(Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .body(ApiResponse.success(currentUsers.current(authentication), request));
    }
}
