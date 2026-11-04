package com.company.logicstic.ai.dispatch;

import com.company.logicstic.ai.config.AiDispatchProperties;
import com.company.logicstic.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.company.logicstic.ai.dispatch.dto.AiDispatchDtos;
import com.company.logicstic.ai.dispatch.evidence.DispatchEvidenceService;
import com.company.logicstic.ai.gap.AiGapService;
import com.company.logicstic.ai.llm.LlmClient;
import com.company.logicstic.ai.llm.PromptBuilder;
import com.company.logicstic.entity.AiDispatchDecision;
import com.company.logicstic.entity.AiDispatchSession;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.AiDispatchDecisionRepository;
import com.company.logicstic.repository.AiDispatchSessionRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDispatchService {

    private final AiDispatchSessionRepository sessionRepository;
    private final AiDispatchDecisionRepository decisionRepository;
    private final LoadRepository loadRepository;
    private final TruckRepository truckRepository;
    private final EmployeeRepository employeeRepository;
    private final AiGapService gapService;
    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final AiProperties properties;
    private final AiDispatchProperties dispatchProperties;
    private final com.company.logicstic.service.LoadService loadService;
    private final com.company.logicstic.repository.AiLoadGapRepository gapRepository;
    private final com.company.logicstic.repository.AiRunRepository runRepository;
    private final DispatchEvidenceService dispatchEvidenceService;
    private static final com.fasterxml.jackson.databind.ObjectMapper DECISION_OUTPUT_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    @Transactional
    public AiDispatchDtos.RunResult runDispatch(AiDispatchDtos.RunRequest request, UUID actorId, String tenantId) {
        log.info("Running AI Dispatch for loadId={} mode={} tenantId={}", request.loadId(), request.mode(), tenantId);

        long startTime = System.currentTimeMillis();
        if (!properties.isDispatchEnabledFor(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AI_FEATURE_DISABLED",
                "Phân hệ AI Dispatch chưa được kích hoạt cho tenant này.");
        }

        // 1. Guard autonomous mode
        if ("Autonomous".equalsIgnoreCase(request.mode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTONOMOUS_MODE_LOCKED",
                "Chế độ Autonomous hiện đang bị khóa. Chỉ cho phép chế độ HumanInTheLoop.");
        }

        // 2. Load existence & gap precondition check
        Load load = loadRepository.findById(request.loadId())
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + request.loadId()));

        if (gapService.hasBlockingGaps(request.loadId(), tenantId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BLOCKED_BY_GAP",
                "Không thể điều phối vì load còn thông tin chặn (BLOCKING) chưa giải quyết. Vui lòng giải quyết gap trước.");
        }

        OffsetDateTime startedAt = OffsetDateTime.now();
        DispatchEvidenceService.Assessment assessment = dispatchEvidenceService.assess(load, request.maxSuggestions());
        List<DispatchEvidenceService.Candidate> evidenceCandidates = assessment.candidates();
        List<RankedCandidate> rankedCandidates = evidenceCandidates.stream()
            .map(candidate -> new RankedCandidate(candidate, 0, "DETERMINISTIC", candidate.reasoning()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        String rankingSource = evidenceCandidates.isEmpty() ? "evidence_unavailable" : "deterministic_evidence_v1";
        int inputTokens = 0;
        int outputTokens = 0;
        boolean tokenUsageMeasured = false;
        String modelUsed = null;
        String summary = evidenceCandidates.isEmpty()
            ? "Chưa thể tạo đề xuất điều phối. " + String.join(" ", assessment.missingEvidence())
            : "Đã tạo " + evidenceCandidates.size() + " đề xuất theo điểm xác định từ tuyến đường, ETA, HOS và đơn giá lương. "
                + "LLM chưa được cấu hình; thứ tự này do công thức xác định tạo ra. "
                + (assessment.missingEvidence().isEmpty() ? "" : "Thông tin còn thiếu: " + String.join(" ", assessment.missingEvidence()));

        if (!evidenceCandidates.isEmpty() && "Groq".equalsIgnoreCase(properties.defaultProvider())) {
            try {
                String candidateData = buildGroqCandidateData(evidenceCandidates, request.instructions());
                LlmClient.Response llmResponse = llmClient.chat(new LlmClient.Request(
                    promptBuilder.buildDispatchReasoningSystemPrompt(), candidateData, List.of(),
                    0.2, properties.maxTokens(), properties.activeModel()));
                rankedCandidates = parseGroqRankings(evidenceCandidates, llmResponse.text());
                rankingSource = "groq_llm_evidence_rerank_v1";
                modelUsed = llmResponse.model();
                inputTokens = llmResponse.inputTokens();
                outputTokens = llmResponse.outputTokens();
                tokenUsageMeasured = !llmClient.reportsSyntheticUsage();
                com.fasterxml.jackson.databind.JsonNode llmOutput = DECISION_OUTPUT_MAPPER.readTree(stripJsonFence(llmResponse.text()));
                String llmSummary = llmOutput.path("summary").asText("").trim();
                summary = llmSummary.isBlank()
                    ? "Groq đã xếp hạng lại các ứng viên vượt qua kiểm tra khả thi."
                    : llmSummary;
            } catch (Exception ex) {
                log.warn("Groq dispatch reranking failed; retaining deterministic evidence ranking for loadId={} (errorType={})",
                    request.loadId(), ex.getClass().getSimpleName());
                summary = "Groq chưa thể xếp hạng lại; đang giữ thứ tự xác định từ dữ liệu vận hành. "
                    + (assessment.missingEvidence().isEmpty() ? "" : "Thông tin còn thiếu: " + String.join(" ", assessment.missingEvidence()));
            }
        }

        // Groq reports real tokens but its pricing is not configured here; do not invent a USD cost.
        BigDecimal estimatedCostUsd = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        // 6. Persist AiDispatchSession
        AiDispatchSession session = new AiDispatchSession();
        session.setTenantId(tenantId);
        session.setMode("HumanInTheLoop");
        session.setStatus("COMPLETED");
        session.setTriggeredByUserId(actorId);
        session.setStartedAt(startedAt);
        session.setCompletedAt(OffsetDateTime.now());
        session.setInstructions(request.instructions());
        session.setInputTokensUsed(inputTokens);
        session.setOutputTokensUsed(outputTokens);
        session.setCacheReadTokens(0);
        session.setCacheCreationTokens(0);
        session.setEstimatedCostUsd(estimatedCostUsd);
        session.setModelUsed(modelUsed);
        session.setDecisionCount(rankedCandidates.size());
        session.setSummary(summary);
        session.setRequestCost(1);
        session.setIsOverage(false);

        AiDispatchSession savedSession = sessionRepository.save(session);

        List<AiDispatchDtos.Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < rankedCandidates.size(); i++) {
            RankedCandidate ranked = rankedCandidates.get(i);
            DispatchEvidenceService.Candidate candidate = ranked.candidate();
            int rank = i + 1;
            Truck truck = candidate.truck();
            AiDispatchDecision decision = new AiDispatchDecision();
            decision.setSession(savedSession);
            decision.setTenantId(tenantId);
            decision.setType("DISPATCH_RECOMMENDATION");
            decision.setStatus("Suggested");
            decision.setReasoning(ranked.reasoning());
            decision.setToolName(rankingSource.startsWith("groq_") ? "groq_dispatch_rerank_v1" : "deterministic_dispatch_v1");
            decision.setToolInput(candidate.evidenceJson());
            decision.setToolOutput("{\"rank\":" + rank + ",\"score\":" + candidate.score().toPlainString()
                + ",\"deadheadMiles\":" + candidate.deadheadMiles()
                + ",\"hosRemainingHours\":" + (candidate.drivingMinutesRemaining() / 60.0)
                + ",\"confidence\":\"" + ranked.confidence() + "\"}");
            decision.setLoadId(load.getId());
            decision.setTruckId(truck.getId());
            AiDispatchDecision savedDecision = decisionRepository.save(decision);
            suggestions.add(new AiDispatchDtos.Suggestion(
                savedDecision.getId(), rank, truck.getId(), truck.getNumber(),
                truck.getMainDriver().getId(), (truck.getMainDriver().getFirstName() + " " + truck.getMainDriver().getLastName()).trim(),
                candidate.score(), ranked.confidence(), ranked.reasoning(),
                new AiDispatchDtos.SuggestionMetric(candidate.deadheadMiles(), null,
                    candidate.drivingMinutesRemaining() / 60.0, null), "Suggested"));
        }
        persistEvidenceGap(load, tenantId, assessment.missingEvidence());

        long latencyMs = System.currentTimeMillis() - startTime;
        com.company.logicstic.entity.AiRun audit = new com.company.logicstic.entity.AiRun();
        audit.setTenantId(tenantId);
        audit.setRunType("DISPATCH_OPTIMIZATION");
        audit.setStatus("evidence_unavailable".equals(rankingSource) ? "FALLBACK" : "SUCCESS");
        audit.setLoadId(request.loadId());
        audit.setModelName(savedSession.getModelUsed());
        audit.setPromptVersion(rankingSource);
        audit.setInputTokens(inputTokens);
        audit.setOutputTokens(outputTokens);
        audit.setTotalTokens(inputTokens + outputTokens);
        audit.setEstimatedCostUsd(savedSession.getEstimatedCostUsd());
        audit.setLatencyMs(latencyMs);
        audit.setResultSummary(savedSession.getSummary());
        runRepository.save(audit);

        return new AiDispatchDtos.RunResult(
            savedSession.getId(),
            savedSession.getNumber(),
            savedSession.getStatus(),
            audit.getStatus(),
            "evidence_unavailable".equals(rankingSource) ? "EVIDENCE_UNAVAILABLE"
                : rankingSource.startsWith("groq_") ? "GROQ_RERANKED"
                : "deterministic_evidence_v1".equals(rankingSource) ? "LLM_UNAVAILABLE" : null,
            savedSession.getMode(),
            rankingSource,
            savedSession.getModelUsed(),
            new AiDispatchDtos.TokenUsage(
                inputTokens, outputTokens, inputTokens + outputTokens, tokenUsageMeasured),
            savedSession.getEstimatedCostUsd(),
            savedSession.getSummary(),
            List.copyOf(suggestions)
        );
    }

    private record RankedCandidate(DispatchEvidenceService.Candidate candidate, int requestedRank,
            String confidence, String reasoning) { }

    private String buildGroqCandidateData(List<DispatchEvidenceService.Candidate> candidates, String instructions) {
        List<Map<String, Object>> safeCandidates = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            safeCandidates.add(Map.of(
                "candidate", i + 1,
                "deadheadMiles", candidate.deadheadMiles(),
                "totalRouteMiles", candidate.totalRouteMiles(),
                "eta", candidate.eta().toString(),
                "drivingMinutesRemaining", candidate.drivingMinutesRemaining(),
                "estimatedDriverPay", candidate.driverPayEstimate(),
                "currency", candidate.payCurrency(),
                "evidenceSummary", candidate.reasoning()));
        }
        String wrappedInstructions = promptBuilder.sanitizeAndWrapUntrustedData(
            "dispatcher instructions", instructions == null ? "" : instructions.substring(0, Math.min(instructions.length(), 2000)));
        try {
            return "Return JSON only with summary and rankings[]. Each rank must reference a candidate number in the supplied list. "
                + "Do not create IDs or candidates.\n" + wrappedInstructions
                + "\nVERIFIED_CANDIDATES=" + DECISION_OUTPUT_MAPPER.writeValueAsString(safeCandidates);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize verified dispatch candidates", ex);
        }
    }

    private List<RankedCandidate> parseGroqRankings(List<DispatchEvidenceService.Candidate> candidates, String text) {
        try {
            JsonNode root = DECISION_OUTPUT_MAPPER.readTree(stripJsonFence(text));
            JsonNode rankings = root.path("rankings");
            if (!rankings.isArray() || rankings.isEmpty()) {
                throw new IllegalArgumentException("Groq response has no rankings");
            }
            List<RankedCandidate> result = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            for (JsonNode item : rankings) {
                int candidateNumber = item.path("rank").asInt(-1);
                if (candidateNumber < 1 || candidateNumber > candidates.size() || !used.add(candidateNumber)) continue;
                String confidence = item.path("confidence").asText("MEDIUM").toUpperCase(Locale.ROOT);
                if (!Set.of("HIGH", "MEDIUM", "LOW").contains(confidence)) confidence = "MEDIUM";
                String reasoning = item.path("reasoning").asText("").trim();
                if (reasoning.length() > 1000) reasoning = reasoning.substring(0, 1000);
                result.add(new RankedCandidate(candidates.get(candidateNumber - 1), candidateNumber,
                    confidence, reasoning.isBlank() ? candidates.get(candidateNumber - 1).reasoning() : reasoning));
            }
            if (result.isEmpty()) throw new IllegalArgumentException("Groq returned no valid candidate ranks");
            for (int i = 0; i < candidates.size(); i++) {
                if (used.add(i + 1)) result.add(new RankedCandidate(candidates.get(i), i + 1,
                    "DETERMINISTIC", candidates.get(i).reasoning()));
            }
            return List.copyOf(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Groq returned invalid JSON", ex);
        }
    }

    private static String stripJsonFence(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private void persistEvidenceGap(Load load, String tenantId, List<String> missing) {
        final String ruleCode = "DISPATCH_EVIDENCE_MISSING";
        var existing = gapRepository.findByTenantIdAndLoadIdAndRuleCode(tenantId, load.getId(), ruleCode).orElse(null);
        if (missing == null || missing.isEmpty()) {
            if (existing != null && "OPEN".equals(existing.getStatus())) {
                existing.setStatus("RESOLVED");
                existing.setResolvedAt(OffsetDateTime.now());
                gapRepository.save(existing);
            }
            return;
        }
        String details = String.join(" ", missing);
        if (existing == null) {
            existing = new com.company.logicstic.entity.AiLoadGap();
            existing.setTenantId(tenantId);
            existing.setLoad(load);
            existing.setRuleCode(ruleCode);
            existing.setFieldName("dispatch_evidence");
            existing.setSeverity("WARNING");
            existing.setStatus("OPEN");
            existing.setAskCount(0);
            existing.setMaxAsks(properties.maxAsks());
            existing.setQuestionNatural(details);
            existing.setQuestionTemplate(details);
        } else {
            existing.setStatus("OPEN");
            existing.setResolvedAt(null);
            existing.setQuestionNatural(details);
            existing.setQuestionTemplate(details);
        }
        gapRepository.save(existing);
    }

    @Transactional
    public AiDispatchDtos.Suggestion approveDecision(UUID decisionId, String notes, UUID actorId, String tenantId) {
        if (!dispatchProperties.approvalEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_APPROVAL_DISABLED",
                "Chức năng phê duyệt đề xuất AI đang tắt. Liên hệ quản trị viên để bật trước khi gán xe.");
        }

        AiDispatchDecision decision = decisionRepository.findByIdAndTenantId(decisionId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Decision not found: " + decisionId));

        if (!"Suggested".equalsIgnoreCase(decision.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DECISION_NOT_PENDING",
                "Quyết định không ở trạng thái Suggested để phê duyệt.");
        }

        // Revalidation: Load and Truck state
        Load load = loadRepository.findById(decision.getLoadId())
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + decision.getLoadId()));

        Truck truck = truckRepository.findById(decision.getTruckId())
            .orElseThrow(() -> new ResourceNotFoundException("Truck not found: " + decision.getTruckId()));

        if (load.getAssignedTruck() != null) {
            decision.setStatus("Failed");
            decision.setRejectionReason("FEASIBILITY_FAILED: Load đã được gán cho xe khác");
            decisionRepository.save(decision);
            throw new ApiException(HttpStatus.CONFLICT, "FEASIBILITY_FAILED",
                "Revalidation thất bại: Chuyến hàng đã được gán xe trước đó.");
        }

        if ("MAINTENANCE".equalsIgnoreCase(truck.getStatus())) {
            decision.setStatus("Failed");
            decision.setRejectionReason("FEASIBILITY_FAILED: Xe đang trong lịch bảo trì");
            decisionRepository.save(decision);
            throw new ApiException(HttpStatus.CONFLICT, "FEASIBILITY_FAILED",
                "Revalidation thất bại: Xe đang trong lịch bảo trì.");
        }

        // Execute Assignment via official domain LoadService (no shortcuts)
        loadService.assignTruck(load.getId(), truck.getId());

        decision.setStatus("Executed");
        decision.setApprovedByUserId(actorId);
        decision.setExecutedAt(OffsetDateTime.now());
        AiDispatchDecision saved = decisionRepository.save(decision);

        return toSuggestionView(saved, truck);
    }

    @Transactional
    public AiDispatchDtos.Suggestion rejectDecision(UUID decisionId, String reason, UUID actorId, String tenantId) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REJECTION_REASON_REQUIRED",
                "Bắt buộc phải cung cấp lý do từ chối gợi ý điều phối.");
        }

        AiDispatchDecision decision = decisionRepository.findByIdAndTenantId(decisionId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Decision not found: " + decisionId));

        if (!"Suggested".equalsIgnoreCase(decision.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DECISION_NOT_PENDING",
                "Quyết định không ở trạng thái Suggested để từ chối.");
        }

        decision.setStatus("Rejected");
        decision.setRejectionReason(reason);
        decision.setApprovedByUserId(actorId);
        AiDispatchDecision saved = decisionRepository.save(decision);

        Truck truck = decision.getTruckId() != null ? truckRepository.findById(decision.getTruckId()).orElse(null) : null;
        return toSuggestionView(saved, truck);
    }

    @Transactional
    public void cancelSession(UUID sessionId, UUID actorId, String tenantId) {
        AiDispatchSession session = sessionRepository.findByIdAndTenantId(sessionId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        session.setStatus("CANCELLED");
        session.setCompletedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        List<AiDispatchDecision> pending = decisionRepository.findBySessionIdAndStatus(sessionId, "Suggested");
        for (AiDispatchDecision d : pending) {
            d.setStatus("Rejected");
            d.setRejectionReason("Session cancelled by user");
            decisionRepository.save(d);
        }
    }

    @Transactional(readOnly = true)
    public Page<AiDispatchSession> getSessions(Pageable pageable, String status, String tenantId) {
        if (status != null && !status.isBlank()) {
            return sessionRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return sessionRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public AiDispatchDtos.SessionDetail getSessionDetail(UUID sessionId, String tenantId, Pageable pageable) {
        AiDispatchSession session = sessionRepository.findByIdAndTenantId(sessionId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        Page<AiDispatchDecision> decisions = decisionRepository.findPageBySessionId(sessionId, pageable);
        List<UUID> truckIds = decisions.getContent().stream().map(AiDispatchDecision::getTruckId)
            .filter(Objects::nonNull).distinct().toList();
        Map<UUID, Truck> trucks = truckIds.isEmpty() ? Map.of() : truckRepository
            .findAllWithMainDriverByIdIn(truckIds).stream().collect(java.util.stream.Collectors.toMap(Truck::getId, t -> t));
        List<AiDispatchDtos.Suggestion> suggestions = decisions.map(d -> toSuggestionView(d, trucks.get(d.getTruckId()))).getContent();

        return new AiDispatchDtos.SessionDetail(
            session.getId(),
            session.getNumber(),
            session.getMode(),
            session.getStatus(),
            session.getTriggeredByUserId(),
            session.getStartedAt(),
            session.getCompletedAt(),
            session.getModelUsed(),
            session.getDecisionCount(),
            session.getEstimatedCostUsd(),
            session.getSummary(),
            session.getErrorMessage(),
            suggestions,
            com.company.logicstic.dto.PageInfo.of(decisions)
        );
    }

    @Transactional(readOnly = true)
    public AiDispatchDtos.SessionDetail getSessionDetail(UUID sessionId, String tenantId) {
        Pageable firstPage = org.springframework.data.domain.PageRequest.of(0, 100,
            org.springframework.data.domain.Sort.by("createdAt").descending().and(org.springframework.data.domain.Sort.by("id").descending()));
        return getSessionDetail(sessionId, tenantId, firstPage);
    }

    @Transactional(readOnly = true)
    public Page<AiDispatchDtos.Suggestion> getPendingDecisions(String tenantId, Pageable pageable) {
        Page<AiDispatchDecision> pending = decisionRepository.findPageByTenantIdAndStatus(tenantId, "Suggested", pageable);
        List<UUID> truckIds = pending.getContent().stream().map(AiDispatchDecision::getTruckId)
            .filter(Objects::nonNull).distinct().toList();
        Map<UUID, Truck> trucks = truckIds.isEmpty() ? Map.of() : truckRepository
            .findAllWithMainDriverByIdIn(truckIds).stream().collect(java.util.stream.Collectors.toMap(Truck::getId, t -> t));
        return pending.map(d -> toSuggestionView(d, trucks.get(d.getTruckId())));
    }

    @Transactional(readOnly = true)
    public AiDispatchDtos.LoadStatusView getLoadStatus(UUID loadId, String tenantId) {
        Load load = loadRepository.findById(loadId)
            .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        var gaps = gapRepository.findByTenantIdAndLoadId(tenantId, loadId);
        long blockingCount = gaps.stream().filter(g -> "OPEN".equals(g.getStatus()) && "BLOCKING".equals(g.getSeverity())).count();
        long warningCount = gaps.stream().filter(g -> "OPEN".equals(g.getStatus()) && "WARNING".equals(g.getSeverity())).count();

        String gapStatus = blockingCount > 0 ? "HAS_BLOCKING" : (warningCount > 0 ? "HAS_WARNING" : "CLEAN");

        var pendingDecisions = decisionRepository.findByLoadIdAndStatusAndTenantId(loadId, "Suggested", tenantId);
        AiDispatchDecision pendingDecision = pendingDecisions.isEmpty() ? null : pendingDecisions.getFirst();
        Page<com.company.logicstic.entity.AiRun> recentRuns = runRepository.searchRuns(
            tenantId, loadId, "DISPATCH_OPTIMIZATION", null,
            org.springframework.data.domain.PageRequest.of(0, 1)
        );
        com.company.logicstic.entity.AiRun latestRun = recentRuns == null || recentRuns.isEmpty()
            ? null
            : recentRuns.getContent().getFirst();

        String dispatchStatus = "UNASSIGNED";
        if (load.getAssignedTruck() != null || "ASSIGNED".equalsIgnoreCase(load.getStatus())) {
            dispatchStatus = "EXECUTED";
        } else if (pendingDecision != null) {
            dispatchStatus = "SUGGESTED";
        }

        boolean canApprove = pendingDecision != null && !"EXECUTED".equals(dispatchStatus);
        boolean canReject = pendingDecision != null;
        boolean canRerun = blockingCount == 0;
        boolean canViewRuns = true;

        UUID truckId = null;
        String truckNumber = null;
        if (pendingDecision != null) {
            truckId = pendingDecision.getTruckId();
            truckNumber = truckRepository.findById(truckId).map(Truck::getNumber).orElse(null);
        } else if (load.getAssignedTruck() != null) {
            truckId = load.getAssignedTruck().getId();
            truckNumber = load.getAssignedTruck().getNumber();
        }

        return new AiDispatchDtos.LoadStatusView(
            loadId,
            gapStatus,
            (int) blockingCount,
            (int) warningCount,
            dispatchStatus,
            pendingDecision != null ? pendingDecision.getSession().getId() : null,
            pendingDecision != null ? pendingDecision.getId() : null,
            truckId,
            truckNumber,
            new AiDispatchDtos.LoadCapabilities(canApprove, canReject, canRerun, canViewRuns),
            latestRun != null ? latestRun.getId() : null,
            latestRun != null ? latestRun.getStatus() : null,
            latestRun != null && "evidence_unavailable".equalsIgnoreCase(latestRun.getPromptVersion())
                ? "EVIDENCE_UNAVAILABLE"
                : latestRun != null && "deterministic_evidence_v1".equalsIgnoreCase(latestRun.getPromptVersion())
                    ? "LLM_UNAVAILABLE"
                : null,
            latestRun != null ? latestRun.getResultSummary() : null,
            latestRun != null ? latestRun.getCreatedAt() : null
        );
    }

    @Transactional(readOnly = true)
    public List<AiDispatchDtos.LoadStatusView> getLoadsOverview(List<UUID> loadIds, String tenantId) {
        if (loadIds == null || loadIds.isEmpty()) return List.of();
        if (loadIds.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OVERVIEW_LIMIT_EXCEEDED", "Tối đa 100 loadIds cho mỗi lần truy vấn overview.");
        }
        return loadIds.stream().map(id -> {
            try {
                return getLoadStatus(id, tenantId);
            } catch (ResourceNotFoundException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    @Transactional
    public AiDispatchDtos.RerunResult rerun(UUID loadId, String target, UUID actorId, String tenantId) {
        String mode = (target == null || target.isBlank()) ? "both" : target.toLowerCase();
        Object gapResult = null;
        AiDispatchDtos.RunResult dispatchResult = null;

        if ("gap".equals(mode) || "both".equals(mode)) {
            gapResult = gapService.evaluateLoad(loadId, tenantId);
        }
        if ("dispatch".equals(mode) || "both".equals(mode)) {
            dispatchResult = runDispatch(new AiDispatchDtos.RunRequest(loadId, "HumanInTheLoop", 5, null), actorId, tenantId);
        }
        return new AiDispatchDtos.RerunResult(loadId, mode, gapResult, dispatchResult);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AiDispatchDtos.RunAuditView> getRuns(
        UUID loadId, String type, String status, org.springframework.data.domain.Pageable pageable, String tenantId
    ) {
        return runRepository.searchRuns(tenantId, loadId, type, status, pageable)
            .map(r -> new AiDispatchDtos.RunAuditView(
                r.getId(),
                r.getRunType(),
                r.getStatus(),
                r.getLoadId(),
                r.getModelName(),
                r.getPromptVersion(),
                r.getInputTokens(),
                r.getOutputTokens(),
                r.getTotalTokens(),
                r.getEstimatedCostUsd(),
                r.getLatencyMs(),
                r.getErrorMessage(),
                r.getResultSummary(),
                r.getCreatedAt()
            ));
    }

    private AiDispatchDtos.Suggestion toSuggestionView(AiDispatchDecision d, Truck truck) {
        String truckNum = truck != null ? truck.getNumber() : "";
        UUID driverId = (truck != null && truck.getMainDriver() != null) ? truck.getMainDriver().getId() : null;
        String driverName = (truck != null && truck.getMainDriver() != null) ?
            (truck.getMainDriver().getFirstName() + " " + truck.getMainDriver().getLastName()) : "";

        if ("deterministic_dispatch_v1".equals(d.getToolName()) && d.getToolOutput() != null) {
            try {
                var output = DECISION_OUTPUT_MAPPER.readTree(d.getToolOutput());
                return new AiDispatchDtos.Suggestion(
                    d.getId(), output.path("rank").isNumber() ? output.path("rank").asInt() : null,
                    d.getTruckId(), truckNum, driverId, driverName,
                    output.path("score").isNumber() ? output.path("score").decimalValue() : null,
                    output.path("confidence").asText("DETERMINISTIC"), d.getReasoning(),
                    new AiDispatchDtos.SuggestionMetric(
                        output.path("deadheadMiles").isNumber() ? output.path("deadheadMiles").asDouble() : null,
                        null,
                        output.path("hosRemainingHours").isNumber() ? output.path("hosRemainingHours").asDouble() : null,
                        null), d.getStatus());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Unable to read stored deterministic dispatch evidence for decisionId={}", d.getId());
            }
        }

        return new AiDispatchDtos.Suggestion(
            d.getId(),
            null,
            d.getTruckId(),
            truckNum,
            driverId,
            driverName,
            null,
            null,
            d.getReasoning() != null ? d.getReasoning()
                : "Đề xuất phiên bản cũ chưa lưu snapshot bằng chứng tuyến đường, HOS, ETA hoặc chi phí.",
            new AiDispatchDtos.SuggestionMetric(null, null, null, null),
            d.getStatus()
        );
    }


}
