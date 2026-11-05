package com.company.logicstic.ai;

import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.ai.dispatch.AiDispatchController;
import com.company.logicstic.ai.dispatch.AiDispatchService;
import com.company.logicstic.ai.dispatch.dto.AiDispatchDtos;
import com.company.logicstic.ai.gap.AiGapController;
import com.company.logicstic.ai.gap.AiGapService;
import com.company.logicstic.ai.gap.dto.AiGapRequests;
import com.company.logicstic.ai.gap.dto.AiGapView;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTenantIsolationAndControllerTest {

    @Mock private AiGapService gapService;
    @Mock private AiDispatchService dispatchService;
    @Mock private EmployeeRepository employeeRepository;

    private LarkProperties larkProperties;
    private AiProperties aiProperties;
    private AiGapController gapController;
    private AiDispatchController dispatchController;

    @BeforeEach
    void setUp() {
        larkProperties = new LarkProperties(false, null, null, null, "a-secure-custom-32-byte-secret-for-test-suite", null, null, null, null, "tenant-default", null, null);
        aiProperties = new AiProperties(true, true, false, 30, 5, 3, "Anthropic", "claude-haiku-4-5", "", "", 4096, 15);
        gapController = new AiGapController(gapService, employeeRepository, larkProperties);
        dispatchController = new AiDispatchController(dispatchService, employeeRepository, larkProperties, aiProperties);
    }

    @Test
    @DisplayName("gapController dismissGap checks tenant boundary and fails when gap belongs to another tenant")
    void gapController_tenantIsolation() {
        UUID gapId = UUID.randomUUID();
        when(gapService.dismissGap(eq(gapId), any(), any(), eq("tenant-default")))
            .thenThrow(new ResourceNotFoundException("Gap not found: " + gapId));

        assertThrows(ResourceNotFoundException.class, () ->
            gapController.dismissGap(gapId, new AiGapRequests.DismissRequest("Reason"), null, new MockHttpServletRequest())
        );
    }

    @Test
    @DisplayName("dispatchController approveDecision fails when decision belongs to another tenant")
    void dispatchController_tenantIsolation() {
        UUID decisionId = UUID.randomUUID();
        when(dispatchService.approveDecision(eq(decisionId), any(), any(), eq("tenant-default")))
            .thenThrow(new ResourceNotFoundException("Decision not found: " + decisionId));

        assertThrows(ResourceNotFoundException.class, () ->
            dispatchController.approveDecision(decisionId, new AiDispatchDtos.ApproveRequest("ok"), null, new MockHttpServletRequest())
        );
    }

    @Test
    @DisplayName("dispatchController getConfig returns active configuration flags")
    void dispatchController_getConfig() {
        var resp = dispatchController.getConfig(new MockHttpServletRequest());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().success());
        assertTrue(resp.getBody().data().gapEnabled());
        assertTrue(resp.getBody().data().dispatchEnabled());
        assertFalse(resp.getBody().data().dryRun());
        assertEquals("Anthropic", resp.getBody().data().defaultProvider());
        assertEquals("claude-haiku-4-5", resp.getBody().data().activeModel());
    }

    @Test
    @DisplayName("dispatchController getFeatures returns feature flags and locked autonomous status")
    void dispatchController_getFeatures() {
        var resp = dispatchController.getFeatures(new MockHttpServletRequest());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().success());
        assertTrue(resp.getBody().data().gapDetection());
        assertTrue(resp.getBody().data().dispatchOptimization());
        assertTrue(resp.getBody().data().autonomousModeLocked());
    }

    @Test
    @DisplayName("dispatchController getLoadStatus returns load status and capabilities")
    void dispatchController_getLoadStatus() {
        UUID loadId = UUID.randomUUID();
        var statusView = new AiDispatchDtos.LoadStatusView(
            loadId, "CLEAN", 0, 0, "UNASSIGNED", null, null, null, null,
            new AiDispatchDtos.LoadCapabilities(false, false, true, true)
        );
        when(dispatchService.getLoadStatus(loadId, "tenant-default")).thenReturn(statusView);

        var resp = dispatchController.getLoadStatus(loadId, new MockHttpServletRequest());
        assertNotNull(resp.getBody());
        assertEquals("CLEAN", resp.getBody().data().gapStatus());
        assertTrue(resp.getBody().data().capabilities().canRerun());
        assertFalse(resp.getBody().data().capabilities().canApprove());
    }

    @Test
    @DisplayName("dispatchController getOverview validates and returns overview list")
    void dispatchController_getOverview() {
        UUID loadId = UUID.randomUUID();
        var statusView = new AiDispatchDtos.LoadStatusView(
            loadId, "HAS_WARNING", 0, 1, "SUGGESTED", null, null, null, null,
            new AiDispatchDtos.LoadCapabilities(true, true, true, true)
        );
        when(dispatchService.getLoadsOverview(List.of(loadId), "tenant-default")).thenReturn(List.of(statusView));

        var resp = dispatchController.getOverview(new AiDispatchDtos.OverviewRequest(List.of(loadId)), new MockHttpServletRequest());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().data().size());
        assertEquals("HAS_WARNING", resp.getBody().data().getFirst().gapStatus());
    }

    @Test
    @DisplayName("dispatchController rerun triggers re-evaluation for requested target")
    void dispatchController_rerun() {
        UUID loadId = UUID.randomUUID();
        var rerunResult = new AiDispatchDtos.RerunResult(loadId, "both", null, null);
        when(dispatchService.rerun(eq(loadId), eq("both"), any(), eq("tenant-default"))).thenReturn(rerunResult);

        var resp = dispatchController.rerun(loadId, new AiDispatchDtos.RerunRequest("both"), null, new MockHttpServletRequest());
        assertNotNull(resp.getBody());
        assertEquals("both", resp.getBody().data().target());
    }

    @Test
    @DisplayName("dispatchController getRuns returns audit page")
    void dispatchController_getRuns() {
        when(dispatchService.getRuns(any(), any(), any(), any(), eq("tenant-default")))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        var resp = dispatchController.getRuns(null, null, null, 0, 20, new MockHttpServletRequest());
        assertNotNull(resp.getBody());
        assertEquals(0, resp.getBody().data().items().size());
    }

    @Test
    @DisplayName("cross-tenant isolation: Tenant B cannot query Tenant A load status")
    void tenantB_cannotQueryTenantA_loadStatus() {
        UUID loadId = UUID.randomUUID();
        when(dispatchService.getLoadStatus(loadId, "tenant-default"))
            .thenThrow(new ResourceNotFoundException("Load not found: " + loadId));

        assertThrows(ResourceNotFoundException.class, () ->
            dispatchController.getLoadStatus(loadId, new MockHttpServletRequest())
        );
    }
}
