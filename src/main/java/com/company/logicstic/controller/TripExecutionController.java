package com.company.logicstic.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.trip.AssignDriverRequest;
import com.company.logicstic.dto.trip.TripDriverAssignmentView;
import com.company.logicstic.dto.trip.TripStopView;
import com.company.logicstic.service.TripExecutionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TripExecutionController {

    private final TripExecutionService tripExecutionService;

    @PostMapping("/trips/{tripId}/drivers")
    public ResponseEntity<ApiResponse<TripDriverAssignmentView>> assignDriver(
            @PathVariable UUID tripId,
            @Valid @RequestBody AssignDriverRequest requestBody,
            HttpServletRequest request
    ) {
        TripDriverAssignmentView data = tripExecutionService.assignDriver(tripId, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PostMapping("/trips/{tripId}/drivers/{assignmentId}/unassign")
    public ResponseEntity<ApiResponse<TripDriverAssignmentView>> unassignDriver(
            @PathVariable UUID tripId,
            @PathVariable UUID assignmentId,
            HttpServletRequest request
    ) {
        TripDriverAssignmentView data = tripExecutionService.unassignDriver(tripId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/trips/{tripId}/drivers")
    public ResponseEntity<ApiResponse<List<TripDriverAssignmentView>>> getTripDrivers(
            @PathVariable UUID tripId,
            HttpServletRequest request
    ) {
        List<TripDriverAssignmentView> data = tripExecutionService.getTripDrivers(tripId);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/trips/{tripId}/stops")
    public ResponseEntity<ApiResponse<List<TripStopView>>> getTripStops(
            @PathVariable UUID tripId,
            HttpServletRequest request
    ) {
        List<TripStopView> data = tripExecutionService.getTripStops(tripId);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/trip-stops/{id}/arrive")
    public ResponseEntity<ApiResponse<TripStopView>> arriveStop(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        TripStopView data = tripExecutionService.arriveStop(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/trip-stops/{id}/start-service")
    public ResponseEntity<ApiResponse<TripStopView>> startStopService(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        TripStopView data = tripExecutionService.startStopService(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/trip-stops/{id}/complete-service")
    public ResponseEntity<ApiResponse<TripStopView>> completeStopService(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        TripStopView data = tripExecutionService.completeStopService(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping("/trip-stops/{id}/depart")
    public ResponseEntity<ApiResponse<TripStopView>> departStop(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        TripStopView data = tripExecutionService.departStop(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }
}
