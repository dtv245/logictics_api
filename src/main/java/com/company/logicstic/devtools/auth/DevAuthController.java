package com.company.logicstic.devtools.auth;

import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import com.company.logicstic.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev-auth")
@RestController
@RequestMapping("/api/dev-auth")
public class DevAuthController {

  private final DevAuthProperties properties;

  public DevAuthController(DevAuthProperties properties) {
    this.properties = properties;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<DevAuthLoginResponse>> login(
      @Valid @RequestBody DevAuthLoginRequest body, HttpServletRequest request) {
    if (!properties.credentialsMatch(body.username(), body.password())) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid development credentials");
    }

    DevAuthLoginResponse response =
        new DevAuthLoginResponse(
            properties.accessToken(),
            "Bearer",
            properties.tokenTtl().toSeconds(),
            DevAuthConfiguration.SUBJECT,
            properties.username(),
            properties.tenantId(),
            List.of(DevAuthConfiguration.ROLE));
    return ResponseEntity.ok(
        ApiResponse.success(
            "DEV_AUTHENTICATED", "Development login successful", response, request));
  }
}
