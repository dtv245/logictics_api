package com.company.logicstic.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.dto.load.LoadEventView;
import com.company.logicstic.dto.load.LoadTimelineResponse;
import com.company.logicstic.dto.load.RecordLoadEventRequest;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.LoadEvent;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.TripStop;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.LoadEventRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadTimelineService {

    private final LoadRepository loadRepository;
    private final LoadEventRepository loadEventRepository;
    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;

    @Transactional
    public LoadEventView recordEvent(UUID loadId, RecordLoadEventRequest req) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        LoadEvent event = new LoadEvent();
        event.setLoad(load);
        event.setEventType(req.eventType());
        event.setPreviousStatus(req.previousStatus() != null ? req.previousStatus() : load.getStatus());
        event.setNewStatus(req.newStatus());
        event.setOccurredAt(req.occurredAt() != null ? req.occurredAt() : OffsetDateTime.now());
        event.setLatitude(req.latitude());
        event.setLongitude(req.longitude());
        event.setSource(req.source() != null && !req.source().isBlank() ? req.source() : "SYSTEM");
        event.setActorId(req.actorId());
        event.setDocumentId(req.documentId());
        event.setNote(req.note());

        if (req.tripId() != null) {
            Trip trip = tripRepository.findById(req.tripId()).orElse(null);
            event.setTrip(trip);
        }

        if (req.tripStopId() != null) {
            TripStop stop = tripStopRepository.findById(req.tripStopId()).orElse(null);
            event.setTripStop(stop);
        }

        // If event specifies newStatus, transition load status
        if (req.newStatus() != null && !req.newStatus().isBlank()) {
            load.setStatus(req.newStatus());
            loadRepository.save(load);
        }

        LoadEvent saved = loadEventRepository.save(event);
        return toEventView(saved);
    }

    @Transactional(readOnly = true)
    public LoadTimelineResponse getTimeline(UUID loadId) {
        Load load = loadRepository.findById(loadId)
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + loadId));

        List<LoadEventView> eventViews = loadEventRepository.findByLoadIdOrderByOccurredAtAsc(loadId).stream()
                .map(this::toEventView)
                .toList();

        return new LoadTimelineResponse(
                load.getId(),
                load.getNumber() != null ? String.valueOf(load.getNumber()) : null,
                load.getStatus(),
                eventViews
        );
    }

    private LoadEventView toEventView(LoadEvent e) {
        return new LoadEventView(
                e.getId(),
                e.getLoad().getId(),
                e.getTrip() != null ? e.getTrip().getId() : null,
                e.getTripStop() != null ? e.getTripStop().getId() : null,
                e.getEventType(),
                e.getPreviousStatus(),
                e.getNewStatus(),
                e.getOccurredAt(),
                e.getLatitude(),
                e.getLongitude(),
                e.getSource(),
                e.getActorId(),
                e.getDocumentId(),
                e.getNote()
        );
    }
}
