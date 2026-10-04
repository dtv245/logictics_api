package com.company.logicstic.service.fleet;

import com.company.logicstic.common.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.fleet.FleetHistory.*;
import com.company.logicstic.service.fleet.FleetHistory.Period;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** FLEET_REPORTING_V1 v1: approved independently of Rating and Optimization. */
@Component
public class FleetIntervalCalculator {
    public static final String CODE="FLEET_REPORTING_V1";
    public Period period(Instant from,Instant to,LocalDate first,LocalDate exclusiveLast,String zone) {
        if(first!=null || exclusiveLast!=null) {
            if(from!=null || to!=null || first==null || exclusiveLast==null || zone==null || zone.isBlank())throw invalid();
            try {ZoneId id=ZoneId.of(zone);from=first.atStartOfDay(id).toInstant();to=exclusiveLast.atStartOfDay(id).toInstant();}
            catch(DateTimeException e){throw invalid();}
        } else if(zone!=null && !zone.isBlank()) {try{ZoneId.of(zone);}catch(DateTimeException e){throw invalid();}}
        if(from==null || to==null || !from.isBefore(to) || from.getNano()%1000!=0 || to.getNano()%1000!=0)throw invalid();
        return new Period(from,to,zone);
    }
    public MetricDto utilization(List<Durations> scope) {
        BigDecimal productive=BigDecimal.ZERO,capacity=BigDecimal.ZERO;List<String> reasons=new ArrayList<>();
        if(scope.isEmpty())reasons.add("NO_AVAILABILITY_HISTORY");
        for(var d:scope) {
            productive=productive.add(d.productiveSeconds());capacity=capacity.add(d.capacitySeconds());
            if(d.eventCount()==0)reasons.add("NO_AVAILABILITY_HISTORY");
            if(d.conflictSeconds().signum()>0)reasons.add("SOURCE_CAPACITY_CONFLICT");
            if(d.gapSeconds().signum()>0)reasons.add("FLEET_HISTORY_UNAVAILABLE");
        }
        if(!reasons.isEmpty())return new MetricDto("FLEET_UTILIZATION_PERCENT",null,"PERCENT",productive,capacity,MetricAvailability.UNAVAILABLE,CODE,String.join(";",new TreeSet<>(reasons)));
        return ratio("FLEET_UTILIZATION_PERCENT",productive,capacity,"SECOND");
    }
    public MetricDto ratio(String code,BigDecimal numerator,BigDecimal denominator,String basis) {
        if(numerator==null || denominator==null || numerator.signum()<0 || denominator.signum()<=0 || numerator.compareTo(denominator)>0)
            return MetricDto.unavailable(code,"PERCENT","VALID_POSITIVE_DENOMINATOR_REQUIRED");
        BigDecimal authoritative=numerator.divide(denominator,MathContext.DECIMAL128).setScale(8,RoundingMode.HALF_EVEN);
        BigDecimal percent=authoritative.multiply(BigDecimal.valueOf(100),MathContext.DECIMAL128).setScale(2,RoundingMode.HALF_EVEN);
        return MetricDto.available(code,percent,"PERCENT",numerator,denominator,CODE+":"+basis);
    }
    private static BadRequestException invalid(){return new BadRequestException("INVALID_FLEET_REPORT_PERIOD","Explicit half-open Instant period or LocalDate period with business ZoneId required; microsecond precision");}
}
