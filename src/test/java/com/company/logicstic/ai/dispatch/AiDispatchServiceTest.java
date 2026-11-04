package com.company.logicstic.ai.dispatch;

import com.company.logicstic.ai.config.AiDispatchProperties;
import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.ai.dispatch.dto.AiDispatchDtos;
import com.company.logicstic.ai.dispatch.evidence.DispatchEvidenceService;
import com.company.logicstic.ai.gap.AiGapService;
import com.company.logicstic.ai.llm.DeterministicFakeLlmProvider;
import com.company.logicstic.ai.llm.LlmClient;
import com.company.logicstic.ai.llm.PromptBuilder;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiDispatchServiceTest {

    @Mock private AiDispatchSessionRepository sessionRepository;
    @Mock private AiDispatchDecisionRepository decisionRepository;
    @Mock private LoadRepository loadRepository;
    @Mock private TruckRepository truckRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AiGapService gapService;
    @Mock private com.company.logicstic.service.LoadService loadService;
    @Mock private com.company.logicstic.repository.AiLoadGapRepository gapRepository;
    @Mock private com.company.logicstic.repository.AiRunRepository runRepository;
    @Mock private DispatchEvidenceService dispatchEvidenceService;

    private DeterministicFakeLlmProvider fakeLlm;
    private PromptBuilder promptBuilder;
    private AiProperties properties;
    private AiDispatchProperties dispatchProperties;
    private AiDispatchService service;

    @BeforeEach
    void setUp() {
        fakeLlm = new DeterministicFakeLlmProvider();
        promptBuilder = new PromptBuilder();
        properties = new AiProperties(true, true, false, 30, 5, 3, "Anthropic", "claude-haiku-4-5", "", "", 4096, 15);
        // Most tests exercise the approval path itself, so unlock the gate here.
        // The disabled-gate case is covered explicitly below.
        dispatchProperties = dispatchProperties(true);
        service = new AiDispatchService(
            sessionRepository, decisionRepository, loadRepository, truckRepository,
            employeeRepository, gapService, fakeLlm, promptBuilder, properties,
            dispatchProperties, loadService, gapRepository, runRepository, dispatchEvidenceService
        );
        lenient().when(dispatchEvidenceService.assess(any(), any())).thenReturn(
            new DispatchEvidenceService.Assessment(List.of(), List.of("Thiếu dữ liệu định tuyến đã xác thực.")));
    }

    private static AiDispatchProperties dispatchProperties(boolean approvalEnabled) {
        return new AiDispatchProperties(
            true, approvalEnabled, 10, 20, 4000,
            java.time.Duration.ofMinutes(30), java.time.Duration.ofHours(24),
            java.time.Duration.ofSeconds(45), 2048, BigDecimal.valueOf(0.2),
            "Anthropic", List.of("Anthropic"), java.util.Map.of(), java.util.Map.of()
        );
    }

    private void rebindService(AiDispatchProperties props) {
        service = new AiDispatchService(
            sessionRepository, decisionRepository, loadRepository, truckRepository,
            employeeRepository, gapService, fakeLlm, promptBuilder, properties,
            props, loadService, gapRepository, runRepository, dispatchEvidenceService
        );
    }

    @Test
    @DisplayName("runDispatch locks Autonomous mode and throws AUTONOMOUS_MODE_LOCKED")
    void runDispatch_autonomousLocked() {
        var req = new AiDispatchDtos.RunRequest(UUID.randomUUID(), "Autonomous", 5, "instruction");
        ApiException ex = assertThrows(ApiException.class, () ->
            service.runDispatch(req, UUID.randomUUID(), "tenant-1")
        );
        assertEquals("AUTONOMOUS_MODE_LOCKED", ex.getCode());
    }

    @Test
    @DisplayName("runDispatch blocks when load has open BLOCKING gaps")
    void runDispatch_blockedByGap() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "tenant-1";

        Load load = new Load();
        load.setId(loadId);

        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapService.hasBlockingGaps(loadId, tenantId)).thenReturn(true);

        var req = new AiDispatchDtos.RunRequest(loadId, "HumanInTheLoop", 5, null);
        ApiException ex = assertThrows(ApiException.class, () ->
            service.runDispatch(req, UUID.randomUUID(), tenantId)
        );
        assertEquals("BLOCKED_BY_GAP", ex.getCode());
    }

    @Test
    @DisplayName("runDispatch does not create recommendations without qualified evidence")
    void runDispatch_success() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "tenant-1";

        Load load = new Load();
        load.setId(loadId);
        load.setNumber(1001L);
        load.setDistance(400.0);
        load.setDeliveryCostAmount(BigDecimal.valueOf(1800.00));

        Employee driver = new Employee();
        driver.setId(UUID.randomUUID());
        driver.setFirstName("John");
        driver.setLastName("Doe");

        Truck truck = new Truck();
        truck.setId(UUID.randomUUID());
        truck.setNumber("TRK-101");
        truck.setStatus("AVAILABLE");
        truck.setMainDriver(driver);

        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapService.hasBlockingGaps(loadId, tenantId)).thenReturn(false);
        when(sessionRepository.save(any(AiDispatchSession.class))).thenAnswer(i -> {
            AiDispatchSession s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        var req = new AiDispatchDtos.RunRequest(loadId, "HumanInTheLoop", 5, "Optimize deadhead");
        var result = service.runDispatch(req, UUID.randomUUID(), tenantId);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertTrue(result.suggestions().isEmpty());
        assertEquals("evidence_unavailable", result.rankingSource());
        assertNull(result.modelUsed());
        assertTrue(result.summary().contains("Thiếu dữ liệu định tuyến"));
        // Fake provider token counts are fixtures and must never be persisted.
        assertEquals(0, result.tokensUsed().input());
        assertEquals(0, result.tokensUsed().output());
        assertEquals(0, result.tokensUsed().total());
        assertFalse(result.tokensUsed().measured());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.estimatedCostUsd()));
        verify(sessionRepository).save(any(AiDispatchSession.class));
        verify(decisionRepository, never()).save(any(AiDispatchDecision.class));
        org.mockito.ArgumentCaptor<AiRun> auditCaptor = org.mockito.ArgumentCaptor.forClass(AiRun.class);
        verify(runRepository).save(auditCaptor.capture());
        assertEquals("FALLBACK", auditCaptor.getValue().getStatus());
        assertEquals("evidence_unavailable", auditCaptor.getValue().getPromptVersion());
        assertEquals(loadId, auditCaptor.getValue().getLoadId());
        assertEquals(result.summary(), auditCaptor.getValue().getResultSummary());
    }

    @Test
    @DisplayName("runDispatch does not call LLM without qualified evidence")
    void runDispatch_doesNotCallLlmWithoutQualifiedEvidence() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "tenant-1";

        Load load = new Load();
        load.setId(loadId);
        load.setNumber(1002L);

        Employee driver = new Employee();
        driver.setId(UUID.randomUUID());
        driver.setFirstName("Jane");
        driver.setLastName("Smith");

        Truck truck = new Truck();
        truck.setId(UUID.randomUUID());
        truck.setNumber("TRK-202");
        truck.setStatus("AVAILABLE");
        truck.setMainDriver(driver);

        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapService.hasBlockingGaps(loadId, tenantId)).thenReturn(false);
        when(sessionRepository.save(any(AiDispatchSession.class))).thenAnswer(i -> {
            AiDispatchSession s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        LlmClient llm = mock(LlmClient.class);
        service = new AiDispatchService(
            sessionRepository, decisionRepository, loadRepository, truckRepository,
            employeeRepository, gapService, llm, promptBuilder, properties,
            dispatchProperties, loadService, gapRepository, runRepository, dispatchEvidenceService
        );

        var req = new AiDispatchDtos.RunRequest(loadId, "HumanInTheLoop", 5, null);
        var result = service.runDispatch(req, UUID.randomUUID(), tenantId);

        assertNotNull(result);
        assertEquals("evidence_unavailable", result.rankingSource());
        assertEquals("COMPLETED", result.status());
        assertEquals("FALLBACK", result.runStatus());
        assertEquals("EVIDENCE_UNAVAILABLE", result.resultCode());
        assertTrue(result.suggestions().isEmpty());
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("runDispatch persists deterministic evidence-backed suggestions when candidate evidence is ready")
    void runDispatch_persistsDeterministicCandidate() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "tenant-1";
        Employee driver = new Employee(); driver.setId(UUID.randomUUID()); driver.setFirstName("Lan"); driver.setLastName("Nguyen");
        Truck truck = new Truck(); truck.setId(UUID.randomUUID()); truck.setNumber("TRK-REAL-1"); truck.setMainDriver(driver);
        Load load = new Load(); load.setId(loadId); load.setNumber(1003L);
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapService.hasBlockingGaps(loadId, tenantId)).thenReturn(false);
        when(sessionRepository.save(any(AiDispatchSession.class))).thenAnswer(i -> {
            AiDispatchSession session = i.getArgument(0); session.setId(UUID.randomUUID()); return session;
        });
        when(decisionRepository.save(any(AiDispatchDecision.class))).thenAnswer(i -> {
            AiDispatchDecision decision = i.getArgument(0); decision.setId(UUID.randomUUID()); return decision;
        });
        var candidate = new DispatchEvidenceService.Candidate(truck, 4.0, 84.0,
                java.time.Duration.ofHours(2), java.time.OffsetDateTime.now().plusHours(4),
                480, new BigDecimal("42.00"), "USD", new BigDecimal("100.00"),
                "{\"source\":\"configured routing and DB\"}", "ETA lấy từ tuyến; đơn giá theo policy đã lưu.");
        when(dispatchEvidenceService.assess(load, 5)).thenReturn(new DispatchEvidenceService.Assessment(
                List.of(candidate), List.of("Chưa có cấu hình nhiên liệu/tiêu hao xe.")));
        LlmClient llm = mock(LlmClient.class);
        service = new AiDispatchService(sessionRepository, decisionRepository, loadRepository, truckRepository,
                employeeRepository, gapService, llm, promptBuilder, properties, dispatchProperties,
                loadService, gapRepository, runRepository, dispatchEvidenceService);

        var result = service.runDispatch(new AiDispatchDtos.RunRequest(loadId, "HumanInTheLoop", 5, null),
                UUID.randomUUID(), tenantId);

        assertEquals("deterministic_evidence_v1", result.rankingSource());
        assertEquals("LLM_UNAVAILABLE", result.resultCode());
        assertEquals("SUCCESS", result.runStatus());
        assertEquals(1, result.suggestions().size());
        assertEquals(truck.getId(), result.suggestions().getFirst().truckId());
        verify(decisionRepository).save(argThat(d -> "Suggested".equals(d.getStatus())
                && "deterministic_dispatch_v1".equals(d.getToolName())
                && d.getToolInput().contains("configured routing")));
        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("runDispatch uses Groq to rerank only evidence-qualified candidates")
    void runDispatch_reranksQualifiedCandidatesWithGroq() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "tenant-1";
        Employee driver = new Employee();
        driver.setId(UUID.randomUUID());
        driver.setFirstName("Lan");
        driver.setLastName("Nguyen");
        Truck truck = new Truck();
        truck.setId(UUID.randomUUID());
        truck.setNumber("TRK-GROQ-1");
        truck.setMainDriver(driver);
        Load load = new Load();
        load.setId(loadId);
        load.setNumber(1004L);
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapService.hasBlockingGaps(loadId, tenantId)).thenReturn(false);
        when(sessionRepository.save(any(AiDispatchSession.class))).thenAnswer(i -> {
            AiDispatchSession session = i.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });
        when(decisionRepository.save(any(AiDispatchDecision.class))).thenAnswer(i -> {
            AiDispatchDecision decision = i.getArgument(0);
            decision.setId(UUID.randomUUID());
            return decision;
        });
        var candidate = new DispatchEvidenceService.Candidate(truck, 4.0, 84.0,
                java.time.Duration.ofHours(2), java.time.OffsetDateTime.now().plusHours(4),
                480, new BigDecimal("42.00"), "USD", new BigDecimal("100.00"),
                "{\"source\":\"verified routing and DB\"}", "ETA theo tuyến đã xác minh.");
        when(dispatchEvidenceService.assess(load, 5)).thenReturn(new DispatchEvidenceService.Assessment(
                List.of(candidate), List.of()));
        properties = new AiProperties(true, true, false, 30, 5, 3, "Groq", "openai/gpt-oss-20b",
                "test-key", "https://api.groq.com/openai/v1", 512, 15);
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(LlmClient.Request.class))).thenReturn(new LlmClient.Response(
                "{\"summary\":\"Groq đã xếp hạng ứng viên.\",\"rankings\":[{\"rank\":1,\"confidence\":\"HIGH\",\"reasoning\":\"Phù hợp nhất theo ETA và chi phí.\"}]}",
                List.of(), 90, 40, "openai/gpt-oss-20b"));
        service = new AiDispatchService(sessionRepository, decisionRepository, loadRepository, truckRepository,
                employeeRepository, gapService, llm, promptBuilder, properties, dispatchProperties,
                loadService, gapRepository, runRepository, dispatchEvidenceService);

        var result = service.runDispatch(new AiDispatchDtos.RunRequest(loadId, "HumanInTheLoop", 5, null),
                UUID.randomUUID(), tenantId);

        assertEquals("groq_llm_evidence_rerank_v1", result.rankingSource());
        assertEquals("GROQ_RERANKED", result.resultCode());
        assertEquals("openai/gpt-oss-20b", result.modelUsed());
        assertTrue(result.tokensUsed().measured());
        assertEquals("HIGH", result.suggestions().getFirst().confidence());
        assertEquals("Phù hợp nhất theo ETA và chi phí.", result.suggestions().getFirst().reasoning());
        verify(llm).chat(any(LlmClient.Request.class));
        verify(decisionRepository).save(argThat(d -> "groq_dispatch_rerank_v1".equals(d.getToolName())
                && d.getToolOutput().contains("HIGH")));
    }

    @Test
    @DisplayName("stored deterministic decision keeps its evidence metrics when read again")
    void decisionRead_restoresStoredDeterministicEvidence() {
        UUID decisionId = UUID.randomUUID();
        UUID truckId = UUID.randomUUID();
        String tenantId = "tenant-1";
        AiDispatchDecision decision = new AiDispatchDecision();
        decision.setId(decisionId); decision.setTenantId(tenantId); decision.setTruckId(truckId);
        decision.setStatus("Suggested"); decision.setToolName("deterministic_dispatch_v1");
        decision.setToolOutput("{\"rank\":2,\"score\":87.25,\"deadheadMiles\":4.5,\"hosRemainingHours\":7.0,\"confidence\":\"DETERMINISTIC\"}");
        decision.setReasoning("ETA theo dịch vụ định tuyến; HOS theo ELD.");
        Truck truck = new Truck(); truck.setId(truckId); truck.setNumber("TRK-1");
        Employee driver = new Employee(); driver.setId(UUID.randomUUID()); driver.setFirstName("Lan"); driver.setLastName("Nguyen");
        truck.setMainDriver(driver);
        when(decisionRepository.findByIdAndTenantId(decisionId, tenantId)).thenReturn(Optional.of(decision));
        when(decisionRepository.save(decision)).thenReturn(decision);
        when(truckRepository.findById(truckId)).thenReturn(Optional.of(truck));

        AiDispatchDtos.Suggestion suggestion = service.rejectDecision(decisionId, "Chọn chuyến khác", UUID.randomUUID(), tenantId);

        assertEquals(2, suggestion.rank());
        assertEquals(new BigDecimal("87.25"), suggestion.score());
        assertEquals(4.5, suggestion.metrics().deadheadMiles());
        assertEquals(7.0, suggestion.metrics().hosRemainingHours());
        assertEquals("ETA theo dịch vụ định tuyến; HOS theo ELD.", suggestion.reasoning());
    }

    @Test
    @DisplayName("approveDecision revalidates truck and fails with FEASIBILITY_FAILED if truck is in maintenance")
    void approveDecision_revalidationFailsMaintenance() {
        UUID decisionId = UUID.randomUUID();
        UUID loadId = UUID.randomUUID();
        UUID truckId = UUID.randomUUID();
        String tenantId = "tenant-1";

        AiDispatchDecision decision = new AiDispatchDecision();
        decision.setId(decisionId);
        decision.setTenantId(tenantId);
        decision.setStatus("Suggested");
        decision.setLoadId(loadId);
        decision.setTruckId(truckId);

        Load load = new Load();
        load.setId(loadId);

        Truck truck = new Truck();
        truck.setId(truckId);
        truck.setStatus("MAINTENANCE"); // Revalidation failure

        when(decisionRepository.findByIdAndTenantId(decisionId, tenantId)).thenReturn(Optional.of(decision));
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(truckRepository.findById(truckId)).thenReturn(Optional.of(truck));

        ApiException ex = assertThrows(ApiException.class, () ->
            service.approveDecision(decisionId, "Approve note", UUID.randomUUID(), tenantId)
        );
        assertEquals("FEASIBILITY_FAILED", ex.getCode());
        assertEquals("Failed", decision.getStatus());
        verify(decisionRepository).save(decision);
    }

    @Test
    @DisplayName("approveDecision executes assignment on clean revalidation")
    void approveDecision_success() {
        UUID decisionId = UUID.randomUUID();
        UUID loadId = UUID.randomUUID();
        UUID truckId = UUID.randomUUID();
        String tenantId = "tenant-1";

        AiDispatchDecision decision = new AiDispatchDecision();
        decision.setId(decisionId);
        decision.setTenantId(tenantId);
        decision.setStatus("Suggested");
        decision.setLoadId(loadId);
        decision.setTruckId(truckId);

        Load load = new Load();
        load.setId(loadId);

        Truck truck = new Truck();
        truck.setId(truckId);
        truck.setNumber("TRK-555");
        truck.setStatus("AVAILABLE");

        when(decisionRepository.findByIdAndTenantId(decisionId, tenantId)).thenReturn(Optional.of(decision));
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(truckRepository.findById(truckId)).thenReturn(Optional.of(truck));
        when(decisionRepository.save(any(AiDispatchDecision.class))).thenAnswer(i -> i.getArgument(0));

        var suggestion = service.approveDecision(decisionId, "All good", UUID.randomUUID(), tenantId);

        assertEquals("Executed", decision.getStatus());
        verify(loadService).assignTruck(loadId, truckId);
    }

    @Test
    @DisplayName("approveDecision called on already processed decision throws DECISION_NOT_PENDING")
    void approveDecision_secondTime_throwsDecisionNotPending() {
        UUID decisionId = UUID.randomUUID();
        String tenantId = "tenant-1";

        AiDispatchDecision decision = new AiDispatchDecision();
        decision.setId(decisionId);
        decision.setTenantId(tenantId);
        decision.setStatus("Executed"); // already executed

        when(decisionRepository.findByIdAndTenantId(decisionId, tenantId)).thenReturn(Optional.of(decision));

        ApiException ex = assertThrows(ApiException.class, () ->
            service.approveDecision(decisionId, "Note", UUID.randomUUID(), tenantId)
        );
        assertEquals("DECISION_NOT_PENDING", ex.getCode());
        verify(loadService, never()).assignTruck(any(), any());
    }

    @Test
    @DisplayName("rejectDecision requires non-blank reason")
    void rejectDecision_requiresReason() {
        UUID decisionId = UUID.randomUUID();
        ApiException ex = assertThrows(ApiException.class, () ->
            service.rejectDecision(decisionId, "  ", UUID.randomUUID(), "tenant-1")
        );
        assertEquals("REJECTION_REASON_REQUIRED", ex.getCode());
    }

    @Test
    @DisplayName("approveDecision is blocked while the approval flag is off and never assigns a truck")
    void approveDecision_approvalDisabled_doesNotAssign() {
        rebindService(dispatchProperties(false));

        UUID decisionId = UUID.randomUUID();

        ApiException ex = assertThrows(ApiException.class, () ->
            service.approveDecision(decisionId, "Approve note", UUID.randomUUID(), "tenant-1")
        );
        assertEquals("AI_APPROVAL_DISABLED", ex.getCode());
        verify(decisionRepository, never()).findByIdAndTenantId(any(), any());
        verify(loadService, never()).assignTruck(any(), any());
    }

    @Test
    @DisplayName("load status restores the latest evidence-unavailable run from the audit repository")
    void getLoadStatus_restoresEvidenceUnavailableRun() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "tenant-1";

        Load load = new Load();
        load.setId(loadId);
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapRepository.findByTenantIdAndLoadId(tenantId, loadId)).thenReturn(List.of());
        when(decisionRepository.findByLoadIdAndStatusAndTenantId(loadId, "Suggested", tenantId))
            .thenReturn(List.of());
        var latestRun = new AiRun();
        latestRun.setId(UUID.randomUUID());
        latestRun.setStatus("FALLBACK");
        latestRun.setPromptVersion("evidence_unavailable");
        latestRun.setResultSummary("Chưa thể tạo đề xuất điều phối vì thiếu dữ liệu đã xác thực.");
        latestRun.setCreatedAt(java.time.OffsetDateTime.parse("2026-11-04T10:00:00Z"));
        when(runRepository.searchRuns(eq(tenantId), eq(loadId), eq("DISPATCH_OPTIMIZATION"), isNull(), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(latestRun)));

        var status = service.getLoadStatus(loadId, tenantId);

        assertEquals("UNASSIGNED", status.dispatchStatus());
        assertNull(status.latestSessionId());
        assertEquals(latestRun.getId(), status.latestRunId());
        assertEquals("FALLBACK", status.latestRunStatus());
        assertEquals("EVIDENCE_UNAVAILABLE", status.latestRunResultCode());
        assertEquals(latestRun.getResultSummary(), status.latestRunSummary());
        assertEquals(latestRun.getCreatedAt(), status.latestRunAt());
        verify(decisionRepository, never()).save(any(AiDispatchDecision.class));
    }

    @Test
    @DisplayName("rejectDecision marks status as Rejected with reason")
    void rejectDecision_success() {
        UUID decisionId = UUID.randomUUID();
        String tenantId = "tenant-1";

        AiDispatchDecision decision = new AiDispatchDecision();
        decision.setId(decisionId);
        decision.setTenantId(tenantId);
        decision.setStatus("Suggested");

        when(decisionRepository.findByIdAndTenantId(decisionId, tenantId)).thenReturn(Optional.of(decision));
        when(decisionRepository.save(any(AiDispatchDecision.class))).thenAnswer(i -> i.getArgument(0));

        var suggestion = service.rejectDecision(decisionId, "Tài xế bận", UUID.randomUUID(), tenantId);

        assertEquals("Rejected", decision.getStatus());
        assertEquals("Tài xế bận", decision.getRejectionReason());
    }
}
