package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RatingEngine {
    private final RatingV1Rounding rounding;
    private final FuelSurchargeCalculator fsc;
    public RatingEngine(RatingV1Rounding rounding, FuelSurchargeCalculator fsc) { this.rounding=rounding;this.fsc=fsc; }
    public RatingPreview calculate(RatingInputs input, List<FuelIndexObservation> observations, String correlationId) {
        var rule=input.rule(); String currency=rule.currency();
        if (!RatingV1Rounding.CODE.equals(rule.roundingPolicyCode()) || rule.roundingPolicyVersion()!=RatingV1Rounding.VERSION)
            throw new BadRequestException("INVALID_RATE_POLICY","Unsupported rounding policy version");
        BigDecimal raw;
        if (rule.method()==RatingMethod.FLAT) raw=rule.baseRate();
        else if (rule.method()==RatingMethod.PER_MILE && input.linehaulMileage()!=null)
            raw=rule.baseRate().multiply(input.linehaulMileage().eligibleMiles(),RatingV1Rounding.INTERMEDIATE);
        else throw new BadRequestException("RATING_VALIDATION_REQUIRED","Supported method and explicit mileage required");
        BigDecimal bounded=rule.minimumCharge()==null?raw:raw.max(rule.minimumCharge());
        if(rule.maximumCharge()!=null)bounded=bounded.min(rule.maximumCharge());
        List<RatingLine> lines=new ArrayList<>();
        lines.add(new RatingLine("LINEHAUL",rule.ruleId(),"Linehaul",raw,rounding.money(bounded,currency),currency));
        FuelSurchargeResult fuel=null;
        if(rule.fsc()!=null){
            fuel=fsc.calculate(rule.fsc(),input.pricingDate().pricingDate(),input.fscMileage(),currency,observations);
            lines.add(new RatingLine("FSC",rule.ruleId(),"Fuel surcharge",fuel.unroundedTotal(),fuel.total(),currency));
        }
        for(var a:input.accessorials()){
            CurrencyGuard.requireSameCurrency(currency,a.currency());
            lines.add(new RatingLine("ACCESSORIAL",a.chargeId(),a.type(),a.customerAmount(),rounding.money(a.customerAmount(),currency),currency));
        }
        // Exact sum of already-rounded money lines. Tax is a separate assessment, never a fake zero.
        BigDecimal subtotal=lines.stream().map(RatingLine::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new RatingPreview(input,raw,bounded,fuel,lines,subtotal,currency,rounding.money(BigDecimal.ZERO,currency).scale(),"NOT_INCLUDED_SEPARATE_POLICY",
                RatingV1Rounding.CODE,RatingV1Rounding.VERSION,Instant.now(),correlationId,null,null);
    }
}
