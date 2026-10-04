package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.RatingAcceptRequest;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.RatingSnapshotRepository;
import com.company.logicstic.service.rating.domain.AcceptedRatingSnapshot;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class RatingSnapshotService {
    private final RatingSnapshotRepository snapshots;
    private final RatingPreviewService previews;
    private final RatingFingerprintService fingerprints;
    private final RatingSnapshotWriter writer;
    public AcceptedRatingSnapshot accept(UUID load,RatingAcceptRequest request,UUID actor,String correlationId){
        validate(request);String hash=fingerprints.command(load,request);
        var existing=snapshots.request(request.idempotencyKey());if(existing.isPresent())return RatingSnapshotWriter.replay(existing.get(),hash);
        // Fresh calculation/HTTP happens before any financial write transaction.
        var preview=previews.preview(load,request.rating(),correlationId);
        if(!request.expectedInputHash().equals(preview.inputHash())||!request.expectedResultHash().equals(preview.resultHash()))throw RatingSnapshotWriter.stale();
        var outcome=writer.persist(load,request,hash,preview,actor);
        log.info("rating_accepted correlationId={} snapshotId={} ruleVersion={} policyVersion={}",correlationId,outcome.snapshotId(),preview.inputs().rule().version(),preview.roundingPolicyVersion());
        return outcome;
    }
    public AcceptedRatingSnapshot get(UUID id){return snapshots.find(id).orElseThrow(()->new ResourceNotFoundException("Accepted rating not found"));}
    private void validate(RatingAcceptRequest r){
        if(r==null||r.idempotencyKey()==null||r.idempotencyKey().isBlank()||r.idempotencyKey().length()>200||r.rating()==null
                ||r.expectedInputHash()==null||!r.expectedInputHash().matches("[0-9a-f]{64}")||r.expectedResultHash()==null||!r.expectedResultHash().matches("[0-9a-f]{64}"))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED","Explicit key, rating and preview fingerprints required");
        if(r.supersedesSnapshotId()==null?(r.reasonCode()!=null||r.reason()!=null):(r.reasonCode()==null||!r.reasonCode().matches("[A-Z][A-Z0-9_]{0,79}")||r.reason()==null||r.reason().isBlank()))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED","Superseding correction requires explicit reason code/context; original acceptance has no correction fields");
    }
}
