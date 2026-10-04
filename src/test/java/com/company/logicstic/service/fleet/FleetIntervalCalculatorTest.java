package com.company.logicstic.service.fleet;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.fleet.FleetHistory.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetIntervalCalculatorTest {
    final FleetIntervalCalculator c=new FleetIntervalCalculator();
    BigDecimal n(String v){return new BigDecimal(v);}
    Durations d(String productive,String capacity,String gap,String conflict,long count){return new Durations(UUID.randomUUID(),n("3600"),n("3600"),n(capacity),n(productive),n(gap),n(conflict),count);}
    @Test void fullProductiveIsHundredPercent(){assertEquals(n("100.00"),c.utilization(List.of(d("3600","3600","0","0",3))).value());}
    @Test void partialProductiveIsAvailableNotPartialHistory(){var m=c.utilization(List.of(d("1800","3600","0","0",4)));assertEquals(n("50.00"),m.value());assertEquals(MetricAvailability.AVAILABLE,m.availability());}
    @Test void fleetAggregatesDurationsRatherThanMeanPercentages(){assertEquals(n("25.00"),c.utilization(List.of(d("100","100","0","0",3),d("0","300","0","0",3))).value());}
    @Test void missingOneTruckHistoryCannotHideBehindCompleteNeighbor(){var m=c.utilization(List.of(d("3600","3600","0","0",3),d("0","0","3600","0",0)));assertNull(m.value());assertEquals(MetricAvailability.UNAVAILABLE,m.availability());assertEquals(n("3600"),m.numerator());assertTrue(m.reason().contains("NO_AVAILABILITY_HISTORY"));}
    @Test void conflictIsUnavailableNotZero(){var m=c.utilization(List.of(d("0","0","0","300",4)));assertNull(m.value());assertTrue(m.reason().contains("SOURCE_CAPACITY_CONFLICT"));}
    @Test void initialOrExpiredGapIsUnavailableEvenWithKnownDurations(){assertNull(c.utilization(List.of(d("1200","1800","1800","0",3))).value());}
    @Test void zeroDenominatorIsUnavailable(){assertNull(c.utilization(List.of(d("0","0","0","0",3))).value());}
    @Test void loadedRatioUsesLoadedPlusEmptyNotLegacyActual(){assertEquals(n("80.00"),c.ratio("LOADED",n("80"),n("100"),"MILE").value());}
    @Test void excludedOperationalMilesDoNotMakeDeadheadComplement(){assertEquals(n("16.67"),c.ratio("EMPTY",n("20"),n("120"),"MILE").value());}
    @Test void fleetNumericPolicyUsesRatioEightThenPercentTwoHalfEven(){assertEquals(n("12.34"),c.ratio("R",n("0.12345"),n("1"),"MILE").value());assertEquals(n("12.36"),c.ratio("R",n("0.12355"),n("1"),"MILE").value());}
    @Test void explicitZoneResolvesLocalDateAcrossDstWithoutJvmDefault(){var p=c.period(null,null,LocalDate.parse("2026-03-08"),LocalDate.parse("2026-03-09"),"America/New_York");assertEquals(23*3600,Duration.between(p.from(),p.to()).toSeconds());assertEquals(Instant.parse("2026-03-08T05:00:00Z"),p.from());}
    @Test void missingZoneMixedOrInvalidPeriodNeverFallsBack(){assertThrows(ApiException.class,()->c.period(null,null,LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-02"),null));assertThrows(ApiException.class,()->c.period(Instant.EPOCH,Instant.EPOCH,null,null,null));assertThrows(ApiException.class,()->c.period(Instant.EPOCH,Instant.EPOCH.plusSeconds(1),LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-02"),"UTC"));}
}
