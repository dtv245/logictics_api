package com.company.logicstic.service.calculation;

import java.util.List;
import java.util.UUID;

import com.company.logicstic.entity.CalculationSnapshot;
import com.company.logicstic.repository.CalculationSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculationSnapshotServiceTest {

    @Mock
    private CalculationSnapshotRepository snapshotRepository;

    @InjectMocks
    private CalculationSnapshotService snapshotService;

    private UUID entityId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
    }

    @Test
    @DisplayName("recordSnapshot saves immutable snapshot with SHA-256 checksum")
    void testRecordSnapshot() {
        when(snapshotRepository.save(any(CalculationSnapshot.class))).thenAnswer(invocation -> {
            CalculationSnapshot s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        String inputJson = "{\"miles\":500.0,\"rate\":2.50}";
        String resultJson = "{\"total\":1250.00}";

        CalculationSnapshot snapshot = snapshotService.recordSnapshot(
                "LOAD",
                entityId,
                "RATING",
                "RateEngine",
                "1.0.0",
                "STANDARD_CONTRACT",
                UUID.randomUUID(),
                "2026.1",
                inputJson,
                resultJson,
                "USD",
                UUID.randomUUID(),
                "corr-12345"
        );

        assertNotNull(snapshot);
        assertEquals("LOAD", snapshot.getEntityType());
        assertEquals(entityId, snapshot.getEntityId());
        assertEquals("RATING", snapshot.getCalculationType());
        assertEquals("USD", snapshot.getCurrency());
        assertNotNull(snapshot.getChecksum());
        // SHA-256 hex is 64 characters
        assertEquals(64, snapshot.getChecksum().length());

        ArgumentCaptor<CalculationSnapshot> captor = ArgumentCaptor.forClass(CalculationSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        CalculationSnapshot captured = captor.getValue();
        assertEquals(inputJson, captured.getInputJson());
        assertEquals(resultJson, captured.getResultJson());
    }

    @Test
    @DisplayName("getSnapshotsForEntity retrieves history of snapshots for an entity")
    void testGetSnapshotsForEntity() {
        CalculationSnapshot s1 = new CalculationSnapshot();
        s1.setId(UUID.randomUUID());
        s1.setEntityType("LOAD");
        s1.setEntityId(entityId);
        s1.setCalculationType("ESTIMATE");

        when(snapshotRepository.findByEntityTypeAndEntityIdOrderByCalculatedAtDesc("LOAD", entityId))
                .thenReturn(List.of(s1));

        List<CalculationSnapshot> results = snapshotService.getSnapshotsForEntity("LOAD", entityId);

        assertEquals(1, results.size());
        assertEquals("ESTIMATE", results.getFirst().getCalculationType());
    }
}
