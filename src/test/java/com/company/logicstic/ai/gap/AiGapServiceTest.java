package com.company.logicstic.ai.gap;

import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.ai.gap.dto.AiGapRequests;
import com.company.logicstic.entity.AiLoadGap;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.AiLoadGapHistoryRepository;
import com.company.logicstic.repository.AiLoadGapRepository;
import com.company.logicstic.repository.LoadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGapServiceTest {

    @Mock private AiLoadGapRepository gapRepository;
    @Mock private AiLoadGapHistoryRepository historyRepository;
    @Mock private LoadRepository loadRepository;
    @Mock private com.company.logicstic.repository.AiRunRepository runRepository;

    private GapRuleEngine ruleEngine;
    private GapQuestionTemplateService templateService;
    private AiProperties properties;
    private AiGapService service;

    @BeforeEach
    void setUp() {
        ruleEngine = new GapRuleEngine();
        templateService = new GapQuestionTemplateService();
        properties = new AiProperties(true, true, false, 30, 5, 3, "Anthropic", "claude-haiku-4-5", "", "", 4096, 15);
        service = new AiGapService(gapRepository, historyRepository, loadRepository, ruleEngine, templateService, properties, runRepository);
    }

    @Test
    @DisplayName("getGapsSummary accepts omitted optional filters")
    void getGapsSummary_nullFilters() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "test-tenant";
        Load load = new Load(); load.setId(loadId);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapRepository.findGapPage(tenantId, loadId, null, null, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        service.getGapsSummary(loadId, null, null, tenantId, pageable);

        verify(gapRepository).findGapPage(tenantId, loadId, null, null, pageable);
    }

    @Test
    @DisplayName("getGapsSummary normalizes optional filters before repository lookup")
    void getGapsSummary_normalizesFilters() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "test-tenant";
        Load load = new Load(); load.setId(loadId);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(gapRepository.findGapPage(tenantId, loadId, "BLOCKING", "OPEN", pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        service.getGapsSummary(loadId, " blocking ", " open ", tenantId, pageable);

        verify(gapRepository).findGapPage(tenantId, loadId, "BLOCKING", "OPEN", pageable);
    }

    @Test
    @DisplayName("evaluateLoad creates gap when violations exist")
    void evaluateLoad_createsGaps() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "test-tenant";

        Load load = new Load();
        load.setId(loadId);
        load.setOriginLocationLatitude(null); // violation
        load.setOriginLocationLongitude(null);

        when(loadRepository.findByIdForUpdate(loadId)).thenReturn(Optional.of(load));
        when(gapRepository.findByTenantIdAndLoadId(tenantId, loadId)).thenReturn(List.of());
        when(gapRepository.save(any(AiLoadGap.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.evaluateLoad(loadId, tenantId);

        assertTrue(result.totalGapsFound() > 0);
        assertTrue(result.newGapsCount() > 0);
        verify(gapRepository, atLeastOnce()).save(any(AiLoadGap.class));
        verify(historyRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("evaluateLoad auto-resolves previously detected gap when fixed")
    void evaluateLoad_autoResolves() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "test-tenant";

        Load load = new Load();
        load.setId(loadId);
        load.setOriginLocationLatitude(41.8781);
        load.setOriginLocationLongitude(-87.6298);
        load.setDestinationLocationLatitude(32.7767);
        load.setDestinationLocationLongitude(-96.7970);
        load.setOriginAddressCity("Chicago");
        load.setOriginAddressState("IL");
        load.setDestinationAddressCity("Dallas");
        load.setDestinationAddressState("TX");
        load.setRequestedPickupDate(OffsetDateTime.now().plusDays(1));
        load.setRequestedDeliveryDate(OffsetDateTime.now().plusDays(3));
        load.setDeliveryCostAmount(BigDecimal.valueOf(1500.00));
        load.setIsHazmat(false);
        load.setType("Dry Van");

        AiLoadGap oldGap = new AiLoadGap();
        oldGap.setId(UUID.randomUUID());
        oldGap.setTenantId(tenantId);
        oldGap.setRuleCode("MISSING_ORIGIN_COORDINATES");
        oldGap.setStatus("OPEN");
        oldGap.setSeverity("BLOCKING");

        when(loadRepository.findByIdForUpdate(loadId)).thenReturn(Optional.of(load));
        when(gapRepository.findByTenantIdAndLoadId(tenantId, loadId)).thenReturn(List.of(oldGap));
        when(gapRepository.save(any(AiLoadGap.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.evaluateLoad(loadId, tenantId);

        assertEquals("RESOLVED", oldGap.getStatus());
        assertNotNull(oldGap.getResolvedAt());
        assertTrue(result.autoResolvedCount() > 0);
    }

    @Test
    @DisplayName("dismissGap allows dismissing WARNING gap")
    void dismissGap_warningAllowed() {
        UUID gapId = UUID.randomUUID();
        String tenantId = "test-tenant";

        Load load = new Load();
        load.setId(UUID.randomUUID());

        AiLoadGap gap = new AiLoadGap();
        gap.setId(gapId);
        gap.setTenantId(tenantId);
        gap.setLoad(load);
        gap.setSeverity("WARNING");
        gap.setStatus("OPEN");
        gap.setQuestionTemplate("Sample");

        when(gapRepository.findById(gapId)).thenReturn(Optional.of(gap));
        when(gapRepository.save(any(AiLoadGap.class))).thenAnswer(i -> i.getArgument(0));

        var view = service.dismissGap(gapId, "Khách hàng xác nhận không cần", UUID.randomUUID(), tenantId);

        assertEquals("DISMISSED", view.status());
        assertEquals("Khách hàng xác nhận không cần", view.dismissReason());
    }

    @Test
    @DisplayName("dismissGap throws GAP_CANNOT_DISMISS_BLOCKING when attempting to dismiss BLOCKING gap")
    void dismissGap_blockingNotAllowed() {
        UUID gapId = UUID.randomUUID();
        String tenantId = "test-tenant";

        AiLoadGap gap = new AiLoadGap();
        gap.setId(gapId);
        gap.setTenantId(tenantId);
        gap.setSeverity("BLOCKING");
        gap.setStatus("OPEN");

        when(gapRepository.findById(gapId)).thenReturn(Optional.of(gap));

        ApiException ex = assertThrows(ApiException.class, () ->
            service.dismissGap(gapId, "Try dismiss", UUID.randomUUID(), tenantId)
        );
        assertEquals("GAP_CANNOT_DISMISS_BLOCKING", ex.getCode());
    }

    @Test
    @DisplayName("answerGap resolves gap and applies proposed value if confirmed")
    void answerGap_appliesProposedValue() {
        UUID gapId = UUID.randomUUID();
        String tenantId = "test-tenant";

        Load load = new Load();
        load.setId(UUID.randomUUID());
        load.setType("Flatbed");

        AiLoadGap gap = new AiLoadGap();
        gap.setId(gapId);
        gap.setTenantId(tenantId);
        gap.setLoad(load);
        gap.setSeverity("WARNING");
        gap.setStatus("OPEN");
        gap.setRuleCode("NOTE_CONFLICT");
        gap.setProposedValue("REEFER");
        gap.setQuestionTemplate("Sample");

        when(gapRepository.findById(gapId)).thenReturn(Optional.of(gap));
        when(gapRepository.save(any(AiLoadGap.class))).thenAnswer(i -> i.getArgument(0));

        var request = new AiGapRequests.AnswerRequest("Reefer", true, "Confirmed");
        var view = service.answerGap(gapId, request, UUID.randomUUID(), tenantId);

        assertEquals("RESOLVED", view.status());
        assertEquals("Reefer", load.getType());
        verify(loadRepository).save(load);
    }

    @Test
    @DisplayName("evaluateLoad is idempotent: duplicate evaluations do not create duplicate gaps")
    void evaluateLoad_isIdempotent() {
        UUID loadId = UUID.randomUUID();
        String tenantId = "test-tenant";

        Load load = new Load();
        load.setId(loadId);
        load.setOriginLocationLatitude(null); // only missing origin
        load.setOriginLocationLongitude(-87.6298);
        load.setDestinationLocationLatitude(32.7767);
        load.setDestinationLocationLongitude(-96.7970);
        load.setOriginAddressCity("Chicago");
        load.setOriginAddressState("IL");
        load.setDestinationAddressCity("Dallas");
        load.setDestinationAddressState("TX");
        load.setRequestedPickupDate(OffsetDateTime.now().plusDays(1));
        load.setRequestedDeliveryDate(OffsetDateTime.now().plusDays(3));
        load.setDeliveryCostAmount(BigDecimal.valueOf(1500.00));
        load.setIsHazmat(false);
        load.setType("Dry Van");
        load.setAssignedDispatcher(new com.company.logicstic.entity.Employee());
        load.setDistance(500.0);

        AiLoadGap existing = new AiLoadGap();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setRuleCode("MISSING_ORIGIN_COORDINATES");
        existing.setStatus("OPEN");
        existing.setSeverity("BLOCKING");
        existing.setAskCount(1);
        existing.setLastAskedAt(OffsetDateTime.now()); // within cooldown

        when(loadRepository.findByIdForUpdate(loadId)).thenReturn(Optional.of(load));
        when(gapRepository.findByTenantIdAndLoadId(tenantId, loadId)).thenReturn(List.of(existing));

        var result = service.evaluateLoad(loadId, tenantId);

        assertEquals(0, result.newGapsCount(), "Should not create any new gaps for already open rule");
        assertEquals(0, result.autoResolvedCount());
        verify(gapRepository, never()).save(argThat(g -> g.getId() == null)); // no new insert
    }
}
