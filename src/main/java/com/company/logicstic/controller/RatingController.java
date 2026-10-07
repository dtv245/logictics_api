package com.company.logicstic.controller;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.rating.RatingPreviewRequest;
import com.company.logicstic.dto.rating.RatingAcceptRequest;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.service.rating.RatingSnapshotService;
import com.company.logicstic.service.rating.domain.AcceptedRatingSnapshot;
import com.company.logicstic.service.rating.RatingPreviewService;
import com.company.logicstic.service.rating.domain.RatingPreview;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
public class RatingController {
    private final RatingPreviewService ratings;
    private final RatingSnapshotService snapshots;
    private final EmployeeRepository employees;
    @PostMapping("/api/loads/{id}/rating/preview")
    public ResponseEntity<ApiResponse<RatingPreview>> preview(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody RatingPreviewRequest body,HttpServletRequest request){
        String correlation=(String)request.getAttribute("correlationId");
        if(correlation==null)correlation=UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.success(ratings.preview(id,body,correlation),request));
    }
    @PostMapping("/api/loads/{id}/rating/accept")
    public ResponseEntity<ApiResponse<AcceptedRatingSnapshot>> accept(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody RatingAcceptRequest body,
            org.springframework.security.core.Authentication auth,HttpServletRequest request){
        if(auth==null||!auth.isAuthenticated())throw new ForbiddenException("Authenticated rating actor required");
        var actor=employees.findByEmail(auth.getName()).orElseThrow(()->new ForbiddenException("Rating actor must map to tenant employee"));
        String correlation=(String)request.getAttribute("correlationId");if(correlation==null)correlation=UUID.randomUUID().toString();
        return ResponseEntity.ok(ApiResponse.success(snapshots.accept(id,body,actor.getId(),correlation),request));
    }
    @GetMapping("/api/rating/snapshots/{id}")
    public ResponseEntity<ApiResponse<AcceptedRatingSnapshot>> get(@PathVariable UUID id,HttpServletRequest request){
        return ResponseEntity.ok(ApiResponse.success(snapshots.get(id),request));
    }
}
