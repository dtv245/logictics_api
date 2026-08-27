package com.company.logicstic.modules.identity.controller;

import com.company.logicstic.modules.identity.dto.response.CurrentUserResponse;
import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!nodb")
@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

  private final CurrentUserService currentUserService;

  public CurrentUserController(CurrentUserService currentUserService) {
    this.currentUserService = currentUserService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
      JwtAuthenticationToken authentication, HttpServletRequest request) {
    CurrentUserResponse data = currentUserService.getCurrentUser(authentication);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }
}
