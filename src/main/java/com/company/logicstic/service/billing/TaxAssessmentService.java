package com.company.logicstic.service.billing;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.billing.domain.TaxAssessment;
import com.company.logicstic.service.rating.CurrencyScaleProvider;
import com.company.logicstic.service.rating.RatingFingerprintService;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class TaxAssessmentService {
    private final TaxAssessmentRepository assessments;
    private final EmployeeRepository employees;
    private final LoadRepository loads;
    private final CurrencyScaleProvider scales;
    private final RatingFingerprintService fingerprints;

    public TaxAssessmentRequest validate(TaxAssessmentRequest a) {
        if(a==null || a.assessmentId()==null || a.loadId()==null || a.customerId()==null
                || a.sourceType()==null || !Set.of("ACCOUNTING","TRUSTED_EXTERNAL_TAX_PROVIDER").contains(a.sourceType())
                || !text(a.sourceReference()) || !text(a.jurisdiction()) || !text(a.assessedBy())
                || a.assessedAt()==null || a.taxableBasis()==null || a.taxableBasis().signum()<0
                || a.taxAmount()==null || a.taxAmount().signum()<0 || a.assessedAt().getNano()%1000!=0
                || (a.policyVersion()!=null && !text(a.policyVersion())))
            throw new BadRequestException("INVALID_TAX_ASSESSMENT","Explicit nonnegative assessment with source, date and assessor required");
        String currency=CurrencyGuard.canonical(a.currency());
        try { a.taxAmount().setScale(scales.scale(currency),RoundingMode.UNNECESSARY); }
        catch(ArithmeticException ex) { throw new BadRequestException("INVALID_TAX_ASSESSMENT","Assessed tax must have exact currency precision; no silent rounding"); }
        return new TaxAssessmentRequest(a.assessmentId(),a.loadId(),a.customerId(),a.sourceType(),a.sourceReference(),a.jurisdiction(),
                a.taxableBasis(),a.taxAmount(),currency,a.policyVersion(),a.assessedAt().withOffsetSameInstant(ZoneOffset.UTC),a.assessedBy());
    }
    private boolean text(String value) { return value!=null && !value.isBlank() && value.length()<=1000; }

    @Transactional
    public TaxAssessment capture(TaxAssessmentRequest request,UUID actor) {
        var a=validate(request);
        if(actor==null || !employees.existsById(actor)) throw new ForbiddenException("Tax capture requires a persisted tenant accounting actor");
        String hash=fingerprints.hash(a); assessments.lock(a.assessmentId());
        var previous=assessments.find(a.assessmentId());
        if(previous.isPresent()) {
            if(!previous.get().inputHash().equals(hash))
                throw new ApiException(HttpStatus.CONFLICT,"TAX_ASSESSMENT_CONFLICT","Assessment identity already has different immutable inputs");
            return previous.get();
        }
        var load=loads.findById(a.loadId()).orElseThrow(()->new ResourceNotFoundException("Assessment Load not found"));
        if(load.getCustomer()==null || !a.customerId().equals(load.getCustomer().getId()))
            throw new BadRequestException("INVALID_TAX_ASSESSMENT","Assessment must match Load customer");
        var value=new TaxAssessment(a,actor,OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS),hash);
        assessments.insert(value); return assessments.find(a.assessmentId()).orElseThrow();
    }

    @Transactional(readOnly=true)
    public TaxAssessment get(UUID id) {
        return assessments.find(id).orElseThrow(()->new ResourceNotFoundException("Tax assessment not found"));
    }
}
