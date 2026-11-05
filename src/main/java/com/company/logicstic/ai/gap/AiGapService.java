package com.company.logicstic.ai.gap;

import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.ai.event.LoadChangedInternalEvent;
import com.company.logicstic.ai.gap.dto.AiGapRequests;
import com.company.logicstic.ai.gap.dto.AiGapView;
import com.company.logicstic.entity.AiLoadGap;
import com.company.logicstic.entity.AiLoadGapHistory;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.AiLoadGapHistoryRepository;
import com.company.logicstic.repository.AiLoadGapRepository;
import com.company.logicstic.repository.LoadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.company.logicstic.dto.PageInfo;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGapService {

    private final AiLoadGapRepository gapRepository;
    private final AiLoadGapHistoryRepository historyRepository;
    private final LoadRepository loadRepository;
    private final GapRuleEngine ruleEngine;
    private final GapQuestionTemplateService templateService;
    private final AiProperties properties;
    private final com.company.logicstic.repository.AiRunRepository runRepository;

    @EventListener
    @Transactional
    public void onInternalLoadChanged(LoadChangedInternalEvent event) {
        if (!properties.isGapEnabledFor(event.tenantId()) && !properties.isDispatchEnabledFor(event.tenantId())) {
            log.debug("AI Gap processing skipped because features are disabled for tenant={}", event.tenantId());
            return;
        }
        log.info("Processing LoadChangedInternalEvent in AiGapService for loadId={} tenantId={}",
            event.loadId(), event.tenantId());
        try {
            evaluateLoad(event.loadId(), event.tenantId());
        } catch (Exception e) {
            log.error("Failed to evaluate gaps for loadId={}", event.loadId(), e);
        }
    }

    @Transactional
    public AiGapRequests.CheckResult evaluateLoad(UUID loadId, String tenantId) {
        long startTime = System.currentTimeMillis();
        // Row lock per load to serialize concurrent evaluations across requests
        Load load = loadRepository.findByIdForUpdate(loadId)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        List<GapRuleEngine.GapViolation> violations = ruleEngine.evaluate(load);
        Set<String> activeViolationCodes = violations.stream()
            .map(GapRuleEngine.GapViolation::ruleCode)
            .collect(Collectors.toSet());

        List<AiLoadGap> existingGaps = gapRepository.findByTenantIdAndLoadId(tenantId, loadId);
        Map<String, AiLoadGap> existingByCode = existingGaps.stream()
            .collect(Collectors.toMap(AiLoadGap::getRuleCode, g -> g, (a, b) -> a));

        int newGapsCount = 0;
        int resolvedCount = 0;
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Resolve gaps that are no longer violated
        for (AiLoadGap existing : existingGaps) {
            if ("OPEN".equals(existing.getStatus()) && !activeViolationCodes.contains(existing.getRuleCode())) {
                existing.setStatus("RESOLVED");
                existing.setResolvedAt(now);
                gapRepository.save(existing);
                historyRepository.save(new AiLoadGapHistory(
                    existing, "AUTO_RESOLVED", null, "SYSTEM", "Thông tin đã được bổ sung/sửa trên hệ thống"
                ));
                resolvedCount++;
            }
        }

        // 2. Add or update current violations
        for (GapRuleEngine.GapViolation violation : violations) {
            AiLoadGap existing = existingByCode.get(violation.ruleCode());
            if (existing == null) {
                AiLoadGap newGap = new AiLoadGap();
                newGap.setTenantId(tenantId);
                newGap.setLoad(load);
                newGap.setRuleCode(violation.ruleCode());
                newGap.setFieldName(violation.fieldName());
                newGap.setSeverity(violation.severity());
                newGap.setStatus("OPEN");
                newGap.setQuestionTemplate(templateService.formatQuestion(violation, load));
                newGap.setQuestionNatural(newGap.getQuestionTemplate());
                newGap.setProposedValue(violation.proposedValue());
                newGap.setAskCount(1);
                newGap.setMaxAsks(properties.maxAsks());
                newGap.setLastAskedAt(now);
                AiLoadGap saved = gapRepository.save(newGap);
                historyRepository.save(new AiLoadGapHistory(
                    saved, "CREATED", null, "SYSTEM", "Phát hiện gap theo luật: " + violation.ruleCode()
                ));
                newGapsCount++;
            } else if ("OPEN".equals(existing.getStatus())) {
                // Check cooldown before incrementing ask count
                long cooldownMins = properties.cooldownMinutes();
                if (existing.getLastAskedAt() == null || existing.getLastAskedAt().plusMinutes(cooldownMins).isBefore(now)) {
                    int nextAsks = existing.getAskCount() + 1;
                    existing.setAskCount(nextAsks);
                    existing.setLastAskedAt(now);
                    if (nextAsks > existing.getMaxAsks()) {
                        existing.setStatus("NEEDS_HUMAN");
                        historyRepository.save(new AiLoadGapHistory(
                            existing, "ESCALATED_NEEDS_HUMAN", null, "SYSTEM", "Vượt quá số lần hỏi tối đa (" + existing.getMaxAsks() + ")"
                        ));
                    }
                    gapRepository.save(existing);
                }
            }
        }

        long latencyMs = System.currentTimeMillis() - startTime;
        com.company.logicstic.entity.AiRun audit = new com.company.logicstic.entity.AiRun();
        audit.setTenantId(tenantId);
        audit.setRunType("GAP_EVALUATION");
        audit.setStatus("SUCCESS");
        audit.setLoadId(loadId);
        audit.setModelName("rules-engine-v1");
        audit.setPromptVersion("DETERMINISTIC_RULES_V1");
        audit.setLatencyMs(latencyMs);
        audit.setResultSummary("Violations: " + violations.size() + ", New: " + newGapsCount + ", Resolved: " + resolvedCount);
        runRepository.save(audit);

        return new AiGapRequests.CheckResult(1, violations.size(), newGapsCount, resolvedCount);
    }

    @Transactional(readOnly = true)
    public AiGapView.Summary getGapsSummary(UUID loadId, String severity, String status, String tenantId, Pageable pageable) {
        Load load = loadRepository.findById(loadId)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        long blockingCount = gapRepository.countByTenantIdAndLoadIdAndSeverityAndStatus(tenantId, loadId, "BLOCKING", "OPEN");
        long warningCount = gapRepository.countByTenantIdAndLoadIdAndSeverityAndStatus(tenantId, loadId, "WARNING", "OPEN");
        Page<AiLoadGap> gaps = gapRepository.findGapPage(tenantId, loadId,
            normalizeFilter(severity), normalizeFilter(status), pageable);
        List<AiGapView> views = gaps.map(this::toView).getContent();

        return new AiGapView.Summary(
            loadId,
            load.getNumber(),
            blockingCount > 0,
            blockingCount,
            warningCount,
            views,
            PageInfo.ofOneBased(gaps)
        );
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public boolean hasBlockingGaps(UUID loadId, String tenantId) {
        return gapRepository.existsByTenantIdAndLoadIdAndSeverityAndStatus(tenantId, loadId, "BLOCKING", "OPEN");
    }

    @Transactional
    public AiGapView dismissGap(UUID gapId, String reason, UUID actorId, String tenantId) {
        AiLoadGap gap = gapRepository.findById(gapId)
            .filter(g -> g.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Gap not found: " + gapId));

        if ("BLOCKING".equals(gap.getSeverity())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GAP_CANNOT_DISMISS_BLOCKING",
                "Không thể bỏ qua (dismiss) gap có mức độ nghiêm trọng BLOCKING. Phải điền thông tin để giải quyết.");
        }

        if (!"OPEN".equals(gap.getStatus()) && !"NEEDS_HUMAN".equals(gap.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GAP_ALREADY_RESOLVED", "Gap đã ở trạng thái: " + gap.getStatus());
        }

        gap.setStatus("DISMISSED");
        gap.setDismissedAt(OffsetDateTime.now());
        gap.setDismissedBy(actorId);
        gap.setDismissReason(reason);
        AiLoadGap saved = gapRepository.save(gap);

        historyRepository.save(new AiLoadGapHistory(
            saved, "DISMISSED", actorId, "USER", "Lý do: " + reason
        ));

        return toView(saved);
    }

    @Transactional
    public AiGapView answerGap(UUID gapId, AiGapRequests.AnswerRequest request, UUID actorId, String tenantId) {
        AiLoadGap gap = gapRepository.findById(gapId)
            .filter(g -> g.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Gap not found: " + gapId));

        if ("RESOLVED".equals(gap.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GAP_ALREADY_RESOLVED", "Gap đã được giải quyết.");
        }

        // Apply answer if confirmed proposed
        if (request.confirmProposed() && gap.getProposedValue() != null) {
            Load load = gap.getLoad();
            if ("NOTE_CONFLICT".equals(gap.getRuleCode()) && "REEFER".equalsIgnoreCase(gap.getProposedValue())) {
                load.setType("Reefer");
                loadRepository.save(load);
            }
        }

        gap.setStatus("RESOLVED");
        gap.setResolvedAt(OffsetDateTime.now());
        AiLoadGap saved = gapRepository.save(gap);

        historyRepository.save(new AiLoadGapHistory(
            saved, "ANSWERED", actorId, "USER", "Giá trị: " + request.value() + " | Ghi chú: " + request.notes()
        ));

        return toView(saved);
    }

    private AiGapView toView(AiLoadGap gap) {
        return new AiGapView(
            gap.getId(),
            gap.getLoad().getId(),
            gap.getRuleCode(),
            gap.getFieldName(),
            gap.getSeverity(),
            gap.getStatus(),
            gap.getQuestionTemplate(),
            gap.getQuestionNatural() != null ? gap.getQuestionNatural() : gap.getQuestionTemplate(),
            gap.getProposedValue(),
            gap.getAskCount(),
            gap.getMaxAsks(),
            gap.getLastAskedAt(),
            gap.getResolvedAt(),
            gap.getDismissedAt(),
            gap.getDismissReason(),
            gap.getCreatedAt()
        );
    }
}
