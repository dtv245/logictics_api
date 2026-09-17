package com.company.logicstic.identity.currentuser;

import com.company.logicstic.shared.web.ApiResponse;
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
