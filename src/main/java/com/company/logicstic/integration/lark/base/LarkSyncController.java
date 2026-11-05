package com.company.logicstic.integration.lark.base;

import com.company.logicstic.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lark/base")
public class LarkSyncController {
  private final LarkSyncService service;
  public LarkSyncController(LarkSyncService service) { this.service=service; }
  @GetMapping("/loads/{id}/sync")
  public ApiResponse<LarkSyncView> loadStatus(@PathVariable UUID id,HttpServletRequest request) {
    return ApiResponse.success(service.status("load",id),request);
  }
  @PostMapping("/loads/{id}/sync")
  public ApiResponse<LarkSyncView> syncLoad(@PathVariable UUID id,
      @RequestParam(defaultValue="false") boolean recreateMissing,HttpServletRequest request) {
    return ApiResponse.success(service.sync("load",id,recreateMissing),request);
  }
  @GetMapping("/drivers/{id}/sync")
  public ApiResponse<LarkSyncView> driverStatus(@PathVariable UUID id,HttpServletRequest request) {
    return ApiResponse.success(service.status("driver",id),request);
  }
  @PostMapping("/drivers/{id}/sync")
  public ApiResponse<LarkSyncView> syncDriver(@PathVariable UUID id,
      @RequestParam(defaultValue="false") boolean recreateMissing,HttpServletRequest request) {
    return ApiResponse.success(service.sync("driver",id,recreateMissing),request);
  }
  @GetMapping("/missing")
  public ApiResponse<List<LarkSyncView>> missing(@RequestParam(defaultValue="load") String domain,
      @RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset,HttpServletRequest request) {
    return ApiResponse.success(service.missing(domain,limit,offset),request);
  }
  public record BackfillRequest(@NotNull UUID entityId,@NotBlank String recordId,Boolean dryRun) {}
  @PostMapping("/{domain}/backfill")
  public ApiResponse<LarkSyncService.Backfill> backfill(@PathVariable String domain,
      @Valid @RequestBody BackfillRequest body,HttpServletRequest request) {
    return ApiResponse.success(service.backfill(domain,body.entityId(),body.recordId(),!Boolean.FALSE.equals(body.dryRun())),request);
  }
}

