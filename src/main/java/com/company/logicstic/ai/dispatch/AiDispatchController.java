package com.company.logicstic.ai.dispatch;

import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.ai.dispatch.dto.AiDispatchDtos;
import com.company.logicstic.config.TenantContext;
import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.entity.AiDispatchSession;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiDispatchController {

    private final AiDispatchService dispatchService;
    private final EmployeeRepository employeeRepository;
    private final LarkProperties larkProperties;
    private final AiProperties aiProperties;

    @PostMapping("/dispatch/runs")
    public ResponseEntity<ApiResponse<AiDispatchDtos.RunResult>> runDispatch(
        @Valid @RequestBody AiDispatchDtos.RunRequest request,
        Authentication auth,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
            dispatchService.runDispatch(request, actorId, tenantId),
            httpRequest
        ));
    }

    @GetMapping("/dispatch/sessions")
    public ResponseEntity<ApiResponse<PagedResponse<AiDispatchSession>>> listSessions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        var pageable = PaginationValidator.zeroBasedPageRequest(page, size, Sort.by("startedAt").descending());
        Page<AiDispatchSession> paged = dispatchService.getSessions(pageable, status, tenantId);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(paged), httpRequest));
    }

    @GetMapping("/dispatch/sessions/{id}")
    public ResponseEntity<ApiResponse<AiDispatchDtos.SessionDetail>> getSession(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "20") Integer size,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        var pageable = PaginationValidator.zeroBasedPageRequest(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(dispatchService.getSessionDetail(id, tenantId, pageable), httpRequest));
    }

    @GetMapping("/dispatch/pending")
    public ResponseEntity<ApiResponse<List<AiDispatchDtos.Suggestion>>> getPending(
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "20") Integer size,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        var pageable = PaginationValidator.zeroBasedPageRequest(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.successPageZeroBased(dispatchService.getPendingDecisions(tenantId, pageable), httpRequest));
    }

    @PostMapping("/dispatch/decisions/{id}/approve")
    public ResponseEntity<ApiResponse<AiDispatchDtos.Suggestion>> approveDecision(
        @PathVariable UUID id,
        @RequestBody(required = false) AiDispatchDtos.ApproveRequest request,
        Authentication auth,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        String notes = request != null ? request.notes() : null;
        return ResponseEntity.ok(ApiResponse.success(
            dispatchService.approveDecision(id, notes, actorId, tenantId),
            httpRequest
        ));
    }

    @PostMapping("/dispatch/decisions/{id}/reject")
    public ResponseEntity<ApiResponse<AiDispatchDtos.Suggestion>> rejectDecision(
        @PathVariable UUID id,
        @Valid @RequestBody AiDispatchDtos.RejectRequest request,
        Authentication auth,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        return ResponseEntity.ok(ApiResponse.success(
            dispatchService.rejectDecision(id, request.reason(), actorId, tenantId),
            httpRequest
        ));
    }

    @PostMapping("/dispatch/sessions/{id}/cancel")
    public ResponseEntity<ApiResponse<Map<String, String>>> cancelSession(
        @PathVariable UUID id,
        Authentication auth,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        dispatchService.cancelSession(id, actorId, tenantId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "CANCELLED"), httpRequest));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AiDispatchDtos.ConfigView>> getConfig(
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            new AiDispatchDtos.ConfigView(
                aiProperties.gapEnabled(),
                aiProperties.dispatchEnabled(),
                aiProperties.dryRun(),
                aiProperties.defaultProvider(),
                aiProperties.activeModel(),
                aiProperties.maxTokens(),
                aiProperties.cooldownMinutes(),
                aiProperties.maxAsks()
            ),
            httpRequest
        ));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<AiDispatchDtos.FeaturesView>> getFeatures(
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        return ResponseEntity.ok(ApiResponse.success(
            new AiDispatchDtos.FeaturesView(
                aiProperties.isGapEnabledFor(tenantId),
                aiProperties.isDispatchEnabledFor(tenantId),
                aiProperties.isDryRunFor(tenantId),
                true, // Autonomous mode is locked
                aiProperties.defaultProvider(),
                aiProperties.activeModel(),
                aiProperties.maxTokens(),
                aiProperties.cooldownMinutes(),
                aiProperties.maxAsks()
            ),
            httpRequest
        ));
    }

    @GetMapping("/loads/{loadId}/status")
    public ResponseEntity<ApiResponse<AiDispatchDtos.LoadStatusView>> getLoadStatus(
        @PathVariable UUID loadId,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        return ResponseEntity.ok(ApiResponse.success(dispatchService.getLoadStatus(loadId, tenantId), httpRequest));
    }

    @PostMapping("/loads/overview")
    public ResponseEntity<ApiResponse<List<AiDispatchDtos.LoadStatusView>>> getOverview(
        @Valid @RequestBody AiDispatchDtos.OverviewRequest request,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        return ResponseEntity.ok(ApiResponse.success(dispatchService.getLoadsOverview(request.loadIds(), tenantId), httpRequest));
    }

    @PostMapping("/loads/{loadId}/rerun")
    public ResponseEntity<ApiResponse<AiDispatchDtos.RerunResult>> rerun(
        @PathVariable UUID loadId,
        @Valid @RequestBody AiDispatchDtos.RerunRequest request,
        Authentication auth,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        return ResponseEntity.ok(ApiResponse.success(
            dispatchService.rerun(loadId, request.target(), actorId, tenantId),
            httpRequest
        ));
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<PagedResponse<AiDispatchDtos.RunAuditView>>> getRuns(
        @RequestParam(required = false) UUID loadId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest httpRequest
    ) {
        String tenantId = resolveTenantId();
        var pageable = PaginationValidator.zeroBasedPageRequest(page, size, Sort.by("id").descending());
        Page<AiDispatchDtos.RunAuditView> paged = dispatchService.getRuns(loadId, type, status, pageable, tenantId);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(paged), httpRequest));
    }

    private String resolveTenantId() {
        return TenantContext.getTenantId().orElse(larkProperties.defaultTenantId());
    }

    private UUID resolveActorId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return employeeRepository.findByEmail(auth.getName()).map(Employee::getId).orElse(null);
    }
}
