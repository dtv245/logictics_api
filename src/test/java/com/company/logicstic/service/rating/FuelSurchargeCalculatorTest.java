package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuelSurchargeCalculatorTest {
    private final LocalDate date = LocalDate.of(2026,1,12);
    private final RatingV1Rounding rounding = new RatingV1Rounding(new CurrencyScaleProvider());
    private final FuelIndexSelector selector = new FuelIndexSelector();
    private final FuelSurchargeCalculator calculator = new FuelSurchargeCalculator(rounding, selector);
    private IndexBasedFscPolicy policy(Integer age, BigDecimal mpg, BigDecimal base) {
        return new IndexBasedFscPolicy(RatingMileageBasis.CONTRACT_MILES,"EIA","US",age,mpg,base,"USD","GALLON");
    }
    private IndexBasedFscPolicy policy() { return policy(7,new BigDecimal("3"),new BigDecimal("2")); }
    private FuelIndexObservation observation(LocalDate at, String value, String region) {
        return new FuelIndexObservation("EIA",region,EiaFuelIndexProvider.seriesFor(region),at,new BigDecimal(value),
                "USD","GALLON","WEEKLY","ULSD",true,Instant.parse("2026-01-13T00:00:00Z"),"2.1.test","a".repeat(64));
    }
    private ResolvedRatingMileage miles(String value) {
        return new ResolvedRatingMileage(RatingMileageComponent.FSC,"CONTRACT_MILES","CONTRACT","agreement",1,
                new BigDecimal(value),"MILE",new BigDecimal(value),null,"test",null,null);
    }
    private String error(Runnable call) { return assertThrows(BadRequestException.class,call::run).getCode(); }
    @Test void selectsLatestNonFutureObservationWithoutRegionFallback() {
        var eligible=observation(date.minusDays(1),"3","US");
        assertSame(eligible,selector.select(policy(),date,List.of(observation(date.plusDays(1),"9","US"),eligible,observation(date.minusDays(5),"2","US"))));
        assertEquals("FUEL_INDEX_UNAVAILABLE",error(()->selector.select(policy(),date,List.of(observation(date,"3","PADD5")))));
    }
    @Test void explicitAgeBoundaryStaleAndMissingAreDifferent() {
        assertNotNull(selector.select(policy(),date,List.of(observation(date.minusDays(7),"3","US"))));
        assertEquals("FUEL_INDEX_STALE",error(()->selector.select(policy(),date,List.of(observation(date.minusDays(8),"3","US")))));
        assertEquals("FUEL_INDEX_UNAVAILABLE",error(()->selector.select(policy(),date,List.of())));
        assertEquals("INVALID_RATE_POLICY",error(()->selector.select(policy(null,BigDecimal.ONE,BigDecimal.ZERO),date,List.of())));
    }
    @Test void duplicateLatestOrUnqualifiedPriceCannotBePickedArbitrarily() {
        assertEquals("FUEL_INDEX_UNAVAILABLE",error(()->selector.select(policy(),date,List.of(observation(date,"3","US"),observation(date,"4","US")))));
        assertEquals("FUEL_INDEX_UNAVAILABLE",error(()->selector.select(policy(),date,List.of(observation(date,"-1","US")))));
    }
    @Test void unitRoundingIsAuthoritativeBeforeTotalAndRetainsInputs() {
        var r=calculator.calculate(policy(),date,miles("10000"),"USD",List.of(observation(date,"3","US")));
        assertEquals(new BigDecimal("0.333333"),r.perMile());
        assertEquals(new BigDecimal("3333.33"),r.total());
        assertEquals("RatingPolicyV1",r.roundingPolicyCode()); assertEquals(1,r.roundingPolicyVersion());
        assertEquals(new BigDecimal("3"),r.policy().contractMpg()); assertEquals(date,r.index().observationDate());
        assertEquals("a".repeat(64),r.index().contentHash()); assertEquals(new BigDecimal("10000"),r.mileage().eligibleMiles());
    }
    @Test void indexBelowBaseAndExplicitZeroMilesAreValidNotMissingInputs() {
        var r=calculator.calculate(policy(),date,miles("20"),"USD",List.of(observation(date,"1","US")));
        assertEquals(new BigDecimal("0.000000"),r.perMile()); assertEquals(new BigDecimal("0.00"),r.total());
        assertEquals(new BigDecimal("0.00"),calculator.calculate(policy(),date,miles("0"),"USD",List.of(observation(date,"3","US"))).total());
    }
    @Test void missingAndInvalidMpgBaseAndMilesNeverFallback() {
        assertEquals("RATE_MPG_REQUIRED",error(()->calculator.calculate(policy(7,null,BigDecimal.ZERO),date,miles("1"),"USD",List.of())));
        assertEquals("RATE_BASE_FUEL_PRICE_REQUIRED",error(()->calculator.calculate(policy(7,BigDecimal.ONE,null),date,miles("1"),"USD",List.of())));
        assertEquals("INVALID_RATE_POLICY",error(()->calculator.calculate(policy(7,BigDecimal.ZERO,BigDecimal.ZERO),date,miles("1"),"USD",List.of())));
        assertEquals("RATING_VALIDATION_REQUIRED",error(()->calculator.calculate(policy(),date,miles("-1"),"USD",List.of())));
    }
    @Test void currencyMinorUnitsAndMinMaxUseUnroundedComponent() {
        assertEquals(new BigDecimal("11"),rounding.money(new BigDecimal("10.5"),"JPY"));
        assertEquals(new BigDecimal("1.235"),rounding.money(new BigDecimal("1.2345"),"KWD"));
        assertEquals(new BigDecimal("1.24"),rounding.boundedMoney(new BigDecimal("1.2351"),null,new BigDecimal("1.235"),"USD"));
        assertEquals(new BigDecimal("1.24"),rounding.boundedMoney(new BigDecimal("1.2349"),new BigDecimal("1.235"),null,"USD"));
        assertEquals("RATING_CURRENCY_UNSUPPORTED",error(()->rounding.money(BigDecimal.ONE,"ZZZ")));
    }
    @Test void revisionAffectsNewPreviewButCannotMutatePriorResult() {
        var before=calculator.calculate(policy(),date,miles("10"),"USD",List.of(observation(date,"3","US")));
        var after=calculator.calculate(policy(),date,miles("10"),"USD",List.of(observation(date,"4","US")));
        assertEquals(new BigDecimal("3.33"),before.total()); assertEquals(new BigDecimal("6.67"),after.total());
        assertEquals(new BigDecimal("3"),before.index().value());
    }
}
