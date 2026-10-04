package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.RatingAcceptRequest;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.rating.domain.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RatingSnapshotWriter {
    private final RatingSnapshotRepository snapshots;
    private final RatingInputLoader inputs;
    private final RatingFingerprintService fingerprints;
    private final EmployeeRepository employees;
    @Transactional
    public AcceptedRatingSnapshot persist(UUID load,RatingAcceptRequest request,String commandHash,RatingPreview preview,UUID actor){
        snapshots.lockRequest(request.idempotencyKey());
        var existing=snapshots.request(request.idempotencyKey());
        if(existing.isPresent())return replay(existing.get(),commandHash);
        if(actor==null||!employees.existsById(actor))throw new BadRequestException("RATING_ACTOR_REQUIRED","Authenticated persisted actor required");
        snapshots.freezeInputs(load,request.rating().accessorialIds());
        var current=inputs.load(load,request.rating());
        if(!fingerprints.inputs(current).equals(preview.inputHash()))throw stale();
        if(request.supersedesSnapshotId()!=null){
            var prior=snapshots.find(request.supersedesSnapshotId()).orElseThrow(()->new ResourceNotFoundException("Superseded rating not found"));
            if(!prior.calculation().inputs().loadId().equals(load)||!prior.calculation().inputs().matchContext().customerId().equals(current.matchContext().customerId())
                    ||!prior.calculation().currency().equals(preview.currency()))throw new BadRequestException("RATING_VALIDATION_REQUIRED","Correction must retain Load/customer/currency identity");
        }
        var s=new AcceptedRatingSnapshot(UUID.randomUUID(),preview,actor,OffsetDateTime.now(ZoneOffset.UTC),request.supersedesSnapshotId(),request.reasonCode(),request.reason(),request.idempotencyKey(),commandHash);
        snapshots.insert(s);return snapshots.find(s.snapshotId()).orElseThrow(()->new ResourceNotFoundException("Accepted snapshot not found"));
    }
    public static AcceptedRatingSnapshot replay(AcceptedRatingSnapshot existing,String hash){
        if(!existing.commandHash().equals(hash))throw new ApiException(HttpStatus.CONFLICT,"RATING_IDEMPOTENCY_CONFLICT","Key was used with a different normalized rating command");
        return existing;
    }
    public static ApiException stale(){return new ApiException(HttpStatus.CONFLICT,"RATING_PREVIEW_STALE","Rating inputs/result changed; preview again and explicitly accept");}
}
