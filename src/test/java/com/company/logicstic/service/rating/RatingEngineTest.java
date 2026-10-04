package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RatingEngineTest {
    private final RatingV1Rounding rounding=new RatingV1Rounding(new CurrencyScaleProvider());
    private final RatingEngine engine=new RatingEngine(rounding,new FuelSurchargeCalculator(rounding,new FuelIndexSelector()));
    private final UUID load=UUID.randomUUID(),customer=UUID.randomUUID();
    private final LocalDate date=LocalDate.of(2026,1,12);
    private RatingInputs inputs(RatingMethod method,String rate,String miles,String minimum,String maximum,List<RatingAccessorialInput> charges){
        var rule=new RateRule(UUID.randomUUID(),2,10,customer,null,null,null,null,null,null,"USD",date,null,method,new BigDecimal(rate),
                method==RatingMethod.PER_MILE?RatingMileageBasis.CONTRACT_MILES:null,minimum==null?null:new BigDecimal(minimum),maximum==null?null:new BigDecimal(maximum),
                null,"RatingPolicyV1",1,UUID.randomUUID(),OffsetDateTime.now());
        var mileage=miles==null?null:new ResolvedRatingMileage(RatingMileageComponent.LINEHAUL,"CONTRACT_MILES","CONTRACT","agreement",1,new BigDecimal(miles),"MILE",new BigDecimal(miles),UUID.randomUUID(),"test agreement",UUID.randomUUID(),OffsetDateTime.now());
        return new RatingInputs(load,new RatingPricingDate(date,"LOAD_REQUESTED_PICKUP_DATE",UUID.randomUUID()),
                new RateMatchContext(customer,null,null,null,null,null,null,"USD",date),"explicit agreement",rule,mileage,null,charges);
    }
    private RatingAccessorialInput charge(String amount){return new RatingAccessorialInput(UUID.randomUUID(),load,"DETENTION","APPROVED",BigDecimal.ONE,"HOUR",new BigDecimal(amount),new BigDecimal(amount),"USD",UUID.randomUUID(),OffsetDateTime.now(),1L);}
    @Test void flatLinehaulAndAccessorialEachRoundBeforeExactSubtotal(){
        var input=inputs(RatingMethod.FLAT,"10.005",null,null,null,List.of(charge("1.005"),charge("1.005")));
        var r=engine.calculate(input,List.of(),"trace");
        assertEquals(new BigDecimal("12.03"),r.subtotal());assertEquals(3,r.lines().size());
        assertNull(r.fuelSurcharge());assertNull(r.inputs().linehaulMileage());assertEquals("NOT_INCLUDED_SEPARATE_POLICY",r.taxAvailability());
        assertSame(input,r.inputs());assertEquals(date,r.inputs().pricingDate().pricingDate());assertEquals("trace",r.correlationId());
        assertThrows(UnsupportedOperationException.class,()->r.lines().clear());
    }
    @Test void perMileNeverRoundsDistanceBeforeMultiplication(){
        var r=engine.calculate(inputs(RatingMethod.PER_MILE,"2.123456789","123.456",null,null,List.of()),List.of(),"trace");
        assertEquals(new BigDecimal("262.153481342784"),r.rawLinehaul());assertEquals(new BigDecimal("262.15"),r.subtotal());
        assertEquals(new BigDecimal("123.456"),r.inputs().linehaulMileage().eligibleMiles());
    }
    @Test void minimumAndMaximumBoundRawLinehaulThenRound(){
        var min=engine.calculate(inputs(RatingMethod.FLAT,"1.2349",null,"1.235",null,List.of()),List.of(),"trace");
        assertEquals(new BigDecimal("1.235"),min.boundedLinehaul());assertEquals(new BigDecimal("1.24"),min.subtotal());
        var max=engine.calculate(inputs(RatingMethod.FLAT,"20",null,null,"1.2349",List.of()),List.of(),"trace");
        assertEquals(new BigDecimal("1.2349"),max.boundedLinehaul());assertEquals(new BigDecimal("1.23"),max.subtotal());
    }
    @Test void missingPerMileSourceIsValidationNotZero(){
        assertEquals("RATING_VALIDATION_REQUIRED",assertThrows(BadRequestException.class,()->engine.calculate(inputs(RatingMethod.PER_MILE,"2",null,null,null,List.of()),List.of(),"trace")).getCode());
    }
}
