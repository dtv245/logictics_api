package com.company.logicstic.integration.lark.base;

import com.company.logicstic.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lark/base")
public class LarkBaseController {

  private final LarkBaseService baseService;

  public LarkBaseController(LarkBaseService baseService) {
    this.baseService = baseService;
  }

  @PostMapping("/sync/load/{loadId}")
  public ResponseEntity<ApiResponse<LarkBaseRecord>> syncLoad(
      @PathVariable UUID loadId,
      HttpServletRequest request) {
    LarkBaseRecord record = baseService.syncLoad(loadId);
    return ResponseEntity.ok(
        ApiResponse.success("LARK_BASE_SYNCED", "Đồng bộ Load sang Lark Base thành công", record, request));
  }

  @PostMapping("/sync/driver/{employeeId}")
  public ResponseEntity<ApiResponse<LarkBaseRecord>> syncDriver(
      @PathVariable UUID employeeId,
      HttpServletRequest request) {
    LarkBaseRecord record = baseService.syncDriver(employeeId);
    return ResponseEntity.ok(
        ApiResponse.success("LARK_BASE_SYNCED", "Đồng bộ Tài xế sang Lark Base thành công", record, request));
  }

  @GetMapping("/loads")
  public ResponseEntity<ApiResponse<List<LarkBaseRecord>>> listSyncedLoads(
      @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize,
      @RequestParam(value = "pageToken", required = false) String pageToken,
      HttpServletRequest request) {
    List<LarkBaseRecord> records = baseService.listSyncedLoads(pageSize, pageToken);
    return ResponseEntity.ok(
        ApiResponse.success("LARK_BASE_LOADS", "Danh sách Load từ Lark Base", records, request));
  }
}
