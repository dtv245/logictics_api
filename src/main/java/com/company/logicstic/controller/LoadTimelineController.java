package com.company.logicstic.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.load.LoadTimelineResponse;
import com.company.logicstic.service.LoadTimelineService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/loads/{loadId}")
@RequiredArgsConstructor
public class LoadTimelineController {

    private final LoadTimelineService loadTimelineService;

    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<LoadTimelineResponse>> getTimeline(
            @PathVariable UUID loadId,
            HttpServletRequest request
    ) {
        LoadTimelineResponse data = loadTimelineService.getTimeline(loadId);
        return ResponseEntity.ok(ApiResponse.success(data, request));
    }

}
