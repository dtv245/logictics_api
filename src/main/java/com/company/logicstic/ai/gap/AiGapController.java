package com.company.logicstic.ai.gap;

import com.company.logicstic.ai.gap.dto.AiGapRequests;
import com.company.logicstic.ai.gap.dto.AiGapView;
import com.company.logicstic.config.TenantContext;
import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/gaps")
@RequiredArgsConstructor
public class AiGapController {

    private final AiGapService gapService;
    private final EmployeeRepository employeeRepository;
    private final LarkProperties larkProperties;

    @GetMapping
    public ResponseEntity<ApiResponse<AiGapView.Summary>> getGaps(
        @RequestParam UUID loadId,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize,
        HttpServletRequest request
    ) {
        String tenantId = resolveTenantId();
        var pageable = PaginationValidator.pageRequest(page, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(
            gapService.getGapsSummary(loadId, severity, status, tenantId, pageable),
            request
        ));
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AiGapRequests.CheckResult>> checkGaps(
        @Valid @RequestBody AiGapRequests.CheckRequest body,
        HttpServletRequest request
    ) {
        String tenantId = resolveTenantId();
        int analyzed = 0;
        int total = 0;
        int newGaps = 0;
        int resolved = 0;

        if (body.loadIds() != null) {
            for (UUID loadId : body.loadIds()) {
                var res = gapService.evaluateLoad(loadId, tenantId);
                analyzed += res.analyzedLoadCount();
                total += res.totalGapsFound();
                newGaps += res.newGapsCount();
                resolved += res.autoResolvedCount();
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
            new AiGapRequests.CheckResult(analyzed, total, newGaps, resolved),
            request
        ));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ApiResponse<AiGapView>> dismissGap(
        @PathVariable UUID id,
        @Valid @RequestBody AiGapRequests.DismissRequest body,
        Authentication auth,
        HttpServletRequest request
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        return ResponseEntity.ok(ApiResponse.success(
            gapService.dismissGap(id, body.reason(), actorId, tenantId),
            request
        ));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<ApiResponse<AiGapView>> answerGap(
        @PathVariable UUID id,
        @Valid @RequestBody AiGapRequests.AnswerRequest body,
        Authentication auth,
        HttpServletRequest request
    ) {
        String tenantId = resolveTenantId();
        UUID actorId = resolveActorId(auth);
        return ResponseEntity.ok(ApiResponse.success(
            gapService.answerGap(id, body, actorId, tenantId),
            request
        ));
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
