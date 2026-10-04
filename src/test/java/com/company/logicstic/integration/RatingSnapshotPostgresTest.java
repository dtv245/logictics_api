package com.company.logicstic.integration;

import com.company.logicstic.dto.load.*;
import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.rating.*;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.*;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class RatingSnapshotPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;@Autowired RatingPreviewService previews;@Autowired RatingSnapshotService snapshots;
    @Autowired RatePolicyService policies;@Autowired RatingMileageService miles;@Autowired LoadPickupBusinessDateService dates;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean FuelIndexProvider indexes;
    private static final LocalDate DATE=LocalDate.of(2026,1,12);
    private record Fixture(UUID load,UUID customer,UUID actor,RatingContract contract,RateRule rule,RatingPreviewRequest request){}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private Fixture fixture(boolean fsc){
        UUID load=UUID.randomUUID(),c=UUID.randomUUID(),actor=UUID.randomUUID();String email=actor+"@rating-snapshot.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Rating snapshot','ACTIVE',false)",c);
        jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Rating','Snapshot','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        jdbc.update("""
                insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
                destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
                destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude,requested_pickup_date)
                values (?,'Rating snapshot','FTL','DELIVERED',999999,false,?,'MANUAL',false,0,'USD',
                'City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0,'2026-01-12T00:30:00+14')
                """,load,c);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"test",List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        dates.remediate(load,new SetPickupBusinessDateRequest(DATE,new PickupBusinessDateProvenance("CUSTOMER_CONFIRMED_PICKUP","Agreement promise","fixture agreement"),null));
        var contract=policies.createContract(new RatingContractRequest(c,"USD",DATE,DATE.plusMonths(1)),actor);
        var policy=fsc?new IndexBasedFscPolicy(RatingMileageBasis.CONTRACT_MILES,"EIA","US",7,new BigDecimal("3"),new BigDecimal("2"),"USD","GALLON"):null;
        var rule=policies.createRule(new RateRuleRequest(10,c,contract.contractId(),1,null,null,null,null,"USD",DATE,DATE.plusMonths(1),RatingMethod.FLAT,new BigDecimal("100.005"),null,null,null,policy),actor);
        UUID fuelEvidence=fsc?miles.capture(load,new ContractMileageRequest(RatingMileageComponent.FSC,contract.contractId(),1,new BigDecimal("10"),"MILE","fixture fuel miles"),actor).id():null;
        return new Fixture(load,c,actor,contract,rule,new RatingPreviewRequest(contract.contractId(),1,null,null,null,null,"USD","fixture agreement",null,fuelEvidence,List.of()));
    }
    private RatingAcceptRequest accept(Fixture f,String key){var p=previews.preview(f.load(),f.request(),"preview");return new RatingAcceptRequest(key,f.request(),p.inputHash(),p.resultHash(),null,null,null);}
    private AcceptedRatingSnapshot accept(Fixture f){return snapshots.accept(f.load(),accept(f,"snapshot-"+UUID.randomUUID()),f.actor(),"accept");}
    private FuelIndexObservation observation(String value){return new FuelIndexObservation("EIA","US",EiaFuelIndexProvider.seriesFor("US"),DATE,new BigDecimal(value),"USD","GALLON","WEEKLY","ULSD",true,Instant.now(),"2.1.test","a".repeat(64));}
    @Test void acceptedSnapshotStoresExactBusinessLocalDateSourceAndSurvivesLoadAndRuleChanges(){
        var f=fixture(false);var accepted=accept(f);var date=accepted.calculation().inputs().pricingDate();
        assertEquals(DATE,date.pricingDate());assertEquals("LOAD_REQUESTED_PICKUP_DATE",date.pricingDateSource());assertNotNull(date.sourceChangeId());
        assertEquals(DATE,jdbc.queryForObject("select pricing_date from accepted_rating_snapshots where id=?",LocalDate.class,accepted.snapshotId()));
        assertEquals(new BigDecimal("100.01"),accepted.calculation().subtotal());assertEquals(f.actor(),accepted.acceptedBy());assertNotNull(accepted.acceptedAt());
        assertEquals(2,accepted.calculation().currencyScale());
        assertEquals(2,jdbc.queryForObject("select (calculation->>'currencyScale')::integer from accepted_rating_snapshots where id=?",Integer.class,accepted.snapshotId()));
        dates.remediate(f.load(),new SetPickupBusinessDateRequest(DATE.plusDays(1),new PickupBusinessDateProvenance("PROMISE_CORRECTION","Explicit new promise","customer correction"),date.sourceChangeId()));
        policies.createRule(new RateRuleRequest(5,f.customer(),f.contract().contractId(),1,null,null,null,null,"USD",DATE,DATE.plusMonths(1),RatingMethod.FLAT,new BigDecimal("200"),null,null,null,null),f.actor());
        var old=snapshots.get(accepted.snapshotId());assertEquals(DATE,old.calculation().inputs().pricingDate().pricingDate());assertEquals(new BigDecimal("100.01"),old.calculation().subtotal());
        var retry=new RatingAcceptRequest(old.idempotencyKey(),f.request(),old.calculation().inputHash(),old.calculation().resultHash(),null,null,null);
        assertEquals(old.snapshotId(),snapshots.accept(f.load(),retry,f.actor(),"historical-retry").snapshotId());
        var p=previews.preview(f.load(),f.request(),"correction");
        var correction=new RatingAcceptRequest("correction-"+UUID.randomUUID(),f.request(),p.inputHash(),p.resultHash(),old.snapshotId(),"CUSTOMER_APPROVED_CORRECTION","New promise/rate accepted");
        var corrected=snapshots.accept(f.load(),correction,f.actor(),"correction");assertEquals(old.snapshotId(),corrected.supersedesSnapshotId());assertEquals(new BigDecimal("200.00"),corrected.calculation().subtotal());
        assertEquals(DATE,snapshots.get(old.snapshotId()).calculation().inputs().pricingDate().pricingDate());
    }
    @Test void concurrentSameCommandReturnsSingleStableSnapshotAndInputDriftConflicts() throws Exception {
        var f=fixture(false);var command=accept(f,"concurrent-"+UUID.randomUUID());
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<AcceptedRatingSnapshot> call=()->{start.await();return snapshots.accept(f.load(),command,f.actor(),"concurrent");};
            var one=pool.submit(call);var two=pool.submit(call);start.countDown();var a=one.get(20,TimeUnit.SECONDS);var b=two.get(20,TimeUnit.SECONDS);
            assertEquals(a.snapshotId(),b.snapshotId());assertEquals(a.acceptedAt(),b.acceptedAt());
            assertEquals(1,jdbc.queryForObject("select count(*) from accepted_rating_snapshots where idempotency_key=?",Integer.class,command.idempotencyKey()));
            var drift=new RatingAcceptRequest(command.idempotencyKey(),f.request(),command.expectedInputHash(),"c".repeat(64),null,null,null);
            assertEquals("RATING_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->snapshots.accept(f.load(),drift,f.actor(),"drift")).getCode());
        }finally{pool.shutdownNow();}
    }
    @Test void databaseProtectsSnapshotHistoryAndReconciledPayload(){
        var f=fixture(false);var accepted=accept(f);
        assertThrows(DataAccessException.class,()->jdbc.update("update accepted_rating_snapshots set subtotal=1 where id=?",accepted.snapshotId()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from accepted_rating_snapshots where id=?",accepted.snapshotId()));
        assertThrows(DataAccessException.class,()->jdbc.update("""
                insert into accepted_rating_snapshots
                select ?,load_id,customer_id,currency,pricing_date,pricing_date_source,pricing_date_change_id,rule_id,rule_version,
                rounding_policy_code,rounding_policy_version,subtotal+1,jsonb_set(calculation,'{subtotal}',to_jsonb(subtotal+1)),
                input_hash,result_hash,accepted_by,accepted_at,supersedes_snapshot_id,reason_code,reason,operation,?,command_hash
                from accepted_rating_snapshots where id=?
                """,UUID.randomUUID(),"corrupt-"+UUID.randomUUID(),accepted.snapshotId()));
        assertEquals(new BigDecimal("100.01"),snapshots.get(accepted.snapshotId()).calculation().subtotal());
    }
    @Test void indexRevisionRequiresFreshAcceptanceAndNeverReratesAcceptedSnapshot(){
        var f=fixture(true);when(indexes.observations("US",DATE)).thenReturn(List.of(observation("3")));
        var oldCommand=accept(f,"fuel-"+UUID.randomUUID());when(indexes.observations("US",DATE)).thenReturn(List.of(observation("4")));
        assertEquals("RATING_PREVIEW_STALE",assertThrows(ApiException.class,()->snapshots.accept(f.load(),oldCommand,f.actor(),"revision")).getCode());
        assertEquals(0,jdbc.queryForObject("select count(*) from accepted_rating_snapshots where load_id=?",Integer.class,f.load()));
        var accepted=accept(f);assertEquals(new BigDecimal("4"),accepted.calculation().fuelSurcharge().index().value());
        when(indexes.observations("US",DATE)).thenReturn(List.of(observation("9")));
        assertEquals(new BigDecimal("4"),snapshots.get(accepted.snapshotId()).calculation().fuelSurcharge().index().value());
        assertEquals(new BigDecimal("106.68"),snapshots.get(accepted.snapshotId()).calculation().subtotal());
    }
    @Test void staleBusinessDateCannotBeAcceptedAndCrossLoadSupersedingIsRejected(){
        var f=fixture(false);var command=accept(f,"date-stale-"+UUID.randomUUID());
        UUID head=jdbc.queryForObject("select pickup_business_date_change_id from loads where id=?",UUID.class,f.load());
        dates.remediate(f.load(),new SetPickupBusinessDateRequest(DATE.plusDays(1),new PickupBusinessDateProvenance("PROMISE_CORRECTION","Explicit date","customer"),head));
        assertEquals("RATING_PREVIEW_STALE",assertThrows(ApiException.class,()->snapshots.accept(f.load(),command,f.actor(),"date-stale")).getCode());
        var prior=accept(f);var other=fixture(false);var p=previews.preview(other.load(),other.request(),"other");
        var invalid=new RatingAcceptRequest("wrong-parent-"+UUID.randomUUID(),other.request(),p.inputHash(),p.resultHash(),prior.snapshotId(),"CORRECTION","Wrong Load");
        assertEquals("RATING_VALIDATION_REQUIRED",assertThrows(ApiException.class,()->snapshots.accept(other.load(),invalid,other.actor(),"other")).getCode());
    }
    @Test void acceptedApiDerivesActorAndSnapshotReadRequiresAccounting() throws Exception {
        var f=fixture(false);var command=accept(f,"api-"+UUID.randomUUID());
        String email=jdbc.queryForObject("select email from employees where id=?",String.class,f.actor());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/loads/"+f.load()+"/rating/accept")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email).roles("ACCOUNTANT"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(json.writeValueAsString(command)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.acceptedBy").value(f.actor().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.calculation.inputs.pricingDate.pricingDate").value("2026-01-12"))
                .andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("data").path("snapshotId").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rating/snapshots/"+id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email).roles("DRIVER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rating/snapshots/"+id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email).roles("ACCOUNTANT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
