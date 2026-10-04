package com.company.logicstic.service.rating;

import com.company.logicstic.dto.rating.RatingPreviewRequest;
import com.company.logicstic.service.rating.domain.RatingPreview;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class RatingPreviewService {
    private final RatingInputLoader inputs;
    private final FuelIndexProvider indexes;
    private final RatingEngine engine;
    private final RatingFingerprintService fingerprints;
    public RatingPreview preview(UUID id, RatingPreviewRequest request, String correlationId) {
        long start=System.nanoTime();
        try {
            var prepared=inputs.load(id,request); // Short read transaction finishes before network I/O.
            var policy=prepared.rule().fsc();
            var result=engine.calculate(prepared,policy==null?List.of():indexes.observations(policy.indexRegion(),prepared.pricingDate().pricingDate()),correlationId);
            log.info("rating_calculation outcome=SUCCESS correlationId={} ruleId={} ruleVersion={} policyVersion={} durationMs={}",
                    correlationId,prepared.rule().ruleId(),prepared.rule().version(),result.roundingPolicyVersion(),(System.nanoTime()-start)/1_000_000);
            return fingerprints.stamp(result);
        } catch(com.company.logicstic.exception.ApiException ex){
            log.warn("rating_calculation outcome=FAILURE correlationId={} domainCode={} durationMs={}",correlationId,ex.getCode(),(System.nanoTime()-start)/1_000_000);
            throw ex;
        }
    }
}
