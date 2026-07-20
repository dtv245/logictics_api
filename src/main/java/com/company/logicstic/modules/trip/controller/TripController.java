package com.company.logicstic.modules.trip.controller;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.modules.trip.dto.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.TripView;
import com.company.logicstic.modules.trip.service.TripService;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.ApiResponse;
import com.company.logicstic.shared.dto.PagedResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Profile("!nodb")
@RestController
@RequestMapping("/api/trips")
@Validated
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TripView>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID truckId,
            @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE) @Min(1) int page,
            @RequestParam(defaultValue = "" + Constants.DEFAULT_PAGE_SIZE) @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize,
            @RequestParam(defaultValue = Constants.DEFAULT_SORT_FIELD_NAME) String orderBy,
            @RequestParam(defaultValue = "false") boolean descending,
            HttpServletRequest request
    ) {
        PagedResponse<TripView> data = tripService.search(search, status, truckId, page, pageSize, orderBy, descending);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripView>> getById(@PathVariable UUID id, HttpServletRequest request) {
        TripView data = tripService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TripView>> create(
            @Valid @RequestBody CreateTripRequest body,
            HttpServletRequest request
    ) {
        TripView data = tripService.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TripView>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTripRequest body,
            HttpServletRequest request
    ) {
        TripView data = tripService.update(id, body);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, HttpServletRequest request) {
        tripService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, request));
    }
}