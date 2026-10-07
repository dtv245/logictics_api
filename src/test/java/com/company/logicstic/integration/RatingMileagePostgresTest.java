package com.company.logicstic.integration;

import com.company.logicstic.dto.load.*;
import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.*;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/tenant",
        "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL", matches="jdbc:postgresql:.*codex_.*")
class RatingMileagePostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("TASK_DB_URL")); p.add("spring.datasource.username", () -> System.getenv("TASK_DB_USER"));
        p.add("spring.datasource.password", () -> System.getenv("TASK_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RatePolicyService policies;
    @Autowired RatingMileageService mileage;
    @Autowired LoadPickupBusinessDateService dates;
    @Autowired WebApplicationContext web;
    private static final LocalDate DATE = LocalDate.of(2026,1,1);
    private record Fixture(UUID load, UUID customer, UUID actor, String email, RatingContract contract) { }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }
    private Fixture fixture() {
        UUID load = UUID.randomUUID(), c = UUID.randomUUID(), actor = UUID.randomUUID(); String email = actor + "@mileage-fixture.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Mileage fixture','ACTIVE',false)", c);
        jdbc.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                values (?,?,'Mileage','Fixture','HOURLY','ACTIVE',now(),0,'USD')
                """, actor, email);
        jdbc.update("""
                insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
                destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
                destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude)
                values (?,'Mileage fixture','FTL','DELIVERED',999999,false,?,'MANUAL',false,0,'USD',
                'City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0)
                """, load, c);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"fixture",List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        dates.remediate(load,new SetPickupBusinessDateRequest(DATE,new PickupBusinessDateProvenance("CUSTOMER_CONFIRMED_PICKUP","Agreement pickup date","fixture agreement"),null));
        var contract = policies.createContract(new RatingContractRequest(c,"USD",DATE,DATE.plusMonths(1)),actor);
        return new Fixture(load,c,actor,email,contract);
    }
    private ContractMileageRequest input(Fixture f, RatingMileageComponent component, String value) {
        return new ContractMileageRequest(component,f.contract().contractId(),f.contract().version(),new BigDecimal(value),"MILE","fixture contract mileage schedule");
    }
    private RateRule rule(Fixture f, RatingMileageBasis basis) {
        var fsc = new IndexBasedFscPolicy(RatingMileageBasis.CONTRACT_MILES,"EIA","US",7,new BigDecimal("5.25"),new BigDecimal("2.5"),"USD","GALLON");
        return policies.createRule(new RateRuleRequest(10,f.customer(),f.contract().contractId(),f.contract().version(),null,null,null,null,
                "USD",DATE,DATE.plusMonths(1),RatingMethod.PER_MILE,BigDecimal.ONE,basis,null,null,fsc),f.actor());
    }
    @Test void independentlySelectedComponentEvidencePreservesMileageAndFullProvenance() {
        var f=fixture(); var r=rule(f,RatingMileageBasis.CONTRACT_MILES);
        var line=mileage.capture(f.load(),input(f,RatingMileageComponent.LINEHAUL,"123.456"),f.actor());
        var fuel=mileage.capture(f.load(),input(f,RatingMileageComponent.FSC,"100.000"),f.actor());
        var one=mileage.resolve(f.load(),RatingMileageComponent.LINEHAUL,RatingMileageBasis.CONTRACT_MILES,line.id(),r);
        var two=mileage.resolve(f.load(),RatingMileageComponent.FSC,RatingMileageBasis.CONTRACT_MILES,fuel.id(),r);
        assertEquals(0,new BigDecimal("123.456").compareTo(one.eligibleMiles())); assertEquals(0,new BigDecimal("100").compareTo(two.eligibleMiles()));
        assertEquals("CONTRACT",one.mileageSourceType()); assertEquals(f.contract().contractId().toString(),one.sourceReference());
        assertEquals(f.contract().version(),one.sourceVersion()); assertEquals(f.actor(),one.capturedBy()); assertNotNull(one.capturedAt());
        assertEquals("fixture contract mileage schedule",one.provenance());
        assertThrows(DataAccessException.class,()->jdbc.update("update contract_load_rating_mileage set normalized_miles=1 where id=?",line.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from contract_load_rating_mileage where id=?",line.id()));
    }
    @Test void zeroMilesIsValidAndDatabaseRejectsPrecisionLossOrNegativeEvidence() {
        var f=fixture(); var r=rule(f,RatingMileageBasis.CONTRACT_MILES);
        var zero=mileage.capture(f.load(),input(f,RatingMileageComponent.LINEHAUL,"0"),f.actor());
        assertEquals(0,BigDecimal.ZERO.compareTo(mileage.resolve(f.load(),RatingMileageComponent.LINEHAUL,RatingMileageBasis.CONTRACT_MILES,zero.id(),r).eligibleMiles()));
        for (String amount : List.of("-1","1.0001")) {
            assertThrows(BadRequestException.class,()->mileage.capture(f.load(),input(f,RatingMileageComponent.LINEHAUL,amount),f.actor()));
            assertThrows(DataAccessException.class,()->jdbc.update("""
                    insert into contract_load_rating_mileage(id,load_id,component_type,contract_id,contract_version,currency,
                    original_value,original_unit,normalized_miles,provenance,captured_by,captured_at)
                    values (?,?,'LINEHAUL',?,1,'USD',?,'MILE',?,'fixture agreement',?,now())
                    """,UUID.randomUUID(),f.load(),f.contract().contractId(),new BigDecimal(amount),new BigDecimal(amount),f.actor()));
        }
    }
    @Test void differentLoadOrComponentEvidenceAndChangedEffectiveDateReject() {
        var f=fixture(); var other=fixture(); var r=rule(f,RatingMileageBasis.CONTRACT_MILES);
        var e=mileage.capture(f.load(),input(f,RatingMileageComponent.LINEHAUL,"12"),f.actor());
        assertEquals("RATING_VALIDATION_REQUIRED",assertThrows(BadRequestException.class,()->mileage.resolve(other.load(),RatingMileageComponent.LINEHAUL,RatingMileageBasis.CONTRACT_MILES,e.id(),r)).getCode());
        assertEquals("RATING_VALIDATION_REQUIRED",assertThrows(BadRequestException.class,()->mileage.resolve(f.load(),RatingMileageComponent.FSC,RatingMileageBasis.CONTRACT_MILES,e.id(),r)).getCode());
        var current=jdbc.queryForObject("select pickup_business_date_change_id from loads where id=?",UUID.class,f.load());
        dates.remediate(f.load(),new SetPickupBusinessDateRequest(DATE.plusMonths(2),new PickupBusinessDateProvenance("CUSTOMER_CORRECTION","Later promised date","fixture correction"),current));
        assertEquals("RATING_VALIDATION_REQUIRED",assertThrows(BadRequestException.class,()->mileage.resolve(f.load(),RatingMileageComponent.LINEHAUL,RatingMileageBasis.CONTRACT_MILES,e.id(),r)).getCode());
        assertEquals(0,new BigDecimal("12").compareTo(jdbc.queryForObject("select normalized_miles from contract_load_rating_mileage where id=?",BigDecimal.class,e.id())));
    }
    @Test void multiLoadTripRejectsInventedAllocationDespiteExistingTripTotals() {
        var f=fixture(); var other=fixture(); UUID trip=UUID.randomUUID();
        jdbc.update("insert into trips(id,name,total_distance,status,actual_distance_miles,loaded_miles,empty_miles) values (?,'Fixture multi-load',1000,'COMPLETED',1000,900,100)",trip);
        int order=0;
        for (UUID load : List.of(f.load(),other.load())) jdbc.update("""
                insert into trip_stops(id,type,trip_id,"order",load_id,address_city,address_country,address_line1,address_state,address_zip_code,location_latitude,location_longitude)
                values (?,'PICKUP',?,?,?,'City','US','Address','TX','00000',0,0)
                """,UUID.randomUUID(),trip,order++,load);
        var r=rule(f,RatingMileageBasis.ACTUAL_ALL_MILES);
        assertEquals("RATE_MILEAGE_ATTRIBUTION_REQUIRED",assertThrows(BadRequestException.class,()->mileage.resolve(f.load(),RatingMileageComponent.LINEHAUL,RatingMileageBasis.ACTUAL_ALL_MILES,null,r)).getCode());
        var contractRule=rule(f,RatingMileageBasis.CONTRACT_MILES);
        var e=mileage.capture(f.load(),input(f,RatingMileageComponent.LINEHAUL,"300"),f.actor());
        assertEquals(0,new BigDecimal("300").compareTo(mileage.resolve(f.load(),RatingMileageComponent.LINEHAUL,RatingMileageBasis.CONTRACT_MILES,e.id(),contractRule).eligibleMiles()));
    }
    @Test void apiRejectsUnauthorizedCaptureAndDerivesActorFromAuthentication() throws Exception {
        var f=fixture(); SecurityContextHolder.clearContext();
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(input(f,RatingMileageComponent.LINEHAUL,"10"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/loads/"+f.load()+"/rating/contract-mileage")
                .contentType("application/json").content(json)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/loads/"+f.load()+"/rating/contract-mileage")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("ACCOUNTANT"))
                .contentType("application/json").content(json)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(f.actor().toString(),tools.jackson.databind.json.JsonMapper.builder().build().readTree(result).get("data").get("capturedBy").asText());
    }
}
