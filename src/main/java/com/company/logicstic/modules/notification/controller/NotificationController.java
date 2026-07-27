package com.company.logicstic.modules.notification.controller;

import com.company.logicstic.modules.notification.dto.response.NotificationResponse;
import com.company.logicstic.modules.notification.service.NotificationService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!nodb")
@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> list(
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
      @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(Constants.MAX_PAGE_SIZE)
          int pageSize,
      HttpServletRequest request) {
    PagedResponse<NotificationResponse> data = notificationService.list(page, pageSize);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<NotificationResponse>> getById(
      @PathVariable UUID id, HttpServletRequest request) {
    NotificationResponse data = notificationService.getById(id);
    return ResponseEntity.ok(ApiResponse.success(data, request));
  }

  @PostMapping("/mark-all-read")
  public ResponseEntity<ApiResponse<Integer>> markAllAsRead(HttpServletRequest request) {
    int updated = notificationService.markAllAsRead();
    return ResponseEntity.ok(
        ApiResponse.success("OK", "Notifications marked as read", updated, request));
  }
}
