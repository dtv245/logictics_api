package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/lark")
public class LarkAuthController {

  private final LarkAuthService authService;

  public LarkAuthController(LarkAuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/authorize")
  public ResponseEntity<ApiResponse<Map<String, String>>> getAuthorizeUrl(
      @RequestParam(value = "returnTo", required = false) String returnTo,
      HttpServletRequest request) {
    String authorizationUrl = authService.getAuthorizeUrl(returnTo);
    Map<String, String> response = Map.of("authorizationUrl", authorizationUrl);
    return ResponseEntity.ok(
        ApiResponse.success("LARK_AUTHORIZE_URL", "Lark authorization URL generated", response, request));
  }

  @GetMapping("/login")
  public ResponseEntity<Void> redirectToLark(
      @RequestParam(value = "returnTo", required = false) String returnTo) {
    String authorizationUrl = authService.getAuthorizeUrl(returnTo);
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create(authorizationUrl));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }

  @PostMapping("/callback")
  public ResponseEntity<ApiResponse<LarkAuthService.LarkLoginResult>> handleCallback(
      @RequestBody LarkCallbackRequest body,
      HttpServletRequest request) {
    LarkAuthService.LarkLoginResult result =
        authService.handleCallback(
            body != null ? body.code() : null,
            body != null ? body.state() : null,
            body != null ? body.error() : null,
            body != null ? body.errorDescription() : null,
            body != null ? body.returnTo() : null);

    return ResponseEntity.ok(
        ApiResponse.success("LARK_AUTHENTICATED", "Đăng nhập Lark thành công", result, request));
  }
}
