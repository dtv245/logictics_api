package com.company.logicstic.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.logicstic.dto.load.LoadEventView;
import com.company.logicstic.dto.load.LoadTimelineResponse;
import com.company.logicstic.dto.load.RecordLoadEventRequest;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.LoadEvent;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.LoadEventRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadTimelineServiceTest {

    @Mock
    private LoadRepository loadRepository;
    @Mock
    private LoadEventRepository loadEventRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripStopRepository tripStopRepository;

    @InjectMocks
    private LoadTimelineService loadTimelineService;

    private UUID loadId;
    private Load load;

    @BeforeEach
    void setUp() {
        loadId = UUID.randomUUID();
        load = new Load();
        load.setId(loadId);
        load.setNumber(1001L);
        load.setStatus("ASSIGNED");
    }

    @Test
    @DisplayName("recordEvent captures audit event and updates load status when new status provided")
    void testRecordEvent() {
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(loadEventRepository.save(any(LoadEvent.class))).thenAnswer(invocation -> {
            LoadEvent ev = invocation.getArgument(0);
            ev.setId(UUID.randomUUID());
            return ev;
        });

        RecordLoadEventRequest req = new RecordLoadEventRequest(
                "STATUS_CHANGE",
                "ASSIGNED",
                "IN_TRANSIT",
                OffsetDateTime.now(),
                37.7749,
                -122.4194,
                "DISPATCHER",
                UUID.randomUUID(),
                null,
                null,
                null,
                "Driver departed shipper"
        );

        LoadEventView view = loadTimelineService.recordEvent(loadId, req);

        assertNotNull(view);
        assertEquals(loadId, view.loadId());
        assertEquals("STATUS_CHANGE", view.eventType());
        assertEquals("ASSIGNED", view.previousStatus());
        assertEquals("IN_TRANSIT", view.newStatus());
        assertEquals("IN_TRANSIT", load.getStatus(), "Load status must transition to newStatus");
        verify(loadRepository).save(load);
    }

    @Test
    @DisplayName("getTimeline returns chronological event history for a load")
    void testGetTimeline() {
        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));

        LoadEvent e1 = new LoadEvent();
        e1.setId(UUID.randomUUID());
        e1.setLoad(load);
        e1.setEventType("CREATED");
        e1.setOccurredAt(OffsetDateTime.now().minusHours(2));

        LoadEvent e2 = new LoadEvent();
        e2.setId(UUID.randomUUID());
        e2.setLoad(load);
        e2.setEventType("DISPATCHED");
        e2.setOccurredAt(OffsetDateTime.now().minusHours(1));

        when(loadEventRepository.findByLoadIdOrderByOccurredAtAsc(loadId)).thenReturn(List.of(e1, e2));

        LoadTimelineResponse response = loadTimelineService.getTimeline(loadId);

        assertNotNull(response);
        assertEquals(loadId, response.loadId());
        assertEquals("1001", response.loadNumber());
        assertEquals(2, response.events().size());
        assertEquals("CREATED", response.events().get(0).eventType());
        assertEquals("DISPATCHED", response.events().get(1).eventType());
    }

    @Test
    @DisplayName("recordEvent throws ResourceNotFoundException for unknown load")
    void testRecordEventUnknownLoad() {
        when(loadRepository.findById(loadId)).thenReturn(Optional.empty());

        RecordLoadEventRequest req = new RecordLoadEventRequest(
                "TEST", null, null, null, null, null, null, null, null, null, null, null
        );

        assertThrows(ResourceNotFoundException.class, () -> loadTimelineService.recordEvent(loadId, req));
    }
}
