package com.company.logicstic.integration;

import com.company.logicstic.dto.load.*;
import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.integration.optimization.*;
import com.company.logicstic.integration.optimization.OptimizationInputProvider.*;
import com.company.logicstic.repository.OptimizationCandidateRepository;
import com.company.logicstic.service.optimization.*;
import com.company.logicstic.service.optimization.OptimizationApplicationService.*;
import com.company.logicstic.service.optimization.OptimizationQualifiedInputService.*;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import com.company.logicstic.service.rating.*;
import com.company.logicstic.service.rating.domain.RatingMethod;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false","app.optimization.single-tenant-scope=TEST_TENANT_ONLY"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class OptimizationRunPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;
    @Autowired OptimizationApplicationService service;
    @Autowired OptimizationPolicyService policies;
    @Autowired OptimizationQualifiedInputService sources;
    @Autowired OptimizationCandidateRepository states;
    @Autowired RatePolicyService ratePolicies;
    @Autowired RatingPreviewService previews;
    @Autowired RatingSnapshotService snapshots;
    @Autowired LoadPickupBusinessDateService dates;
    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper json;
    @MockitoBean TrustedOptimizationHttpAdapter provider;
    record Fixture(OptimizationAuditPostgresTest.Fixture entities,PublishedPolicy policy,Target target,SourceSelection selection) {}
    Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    static BigDecimal d(String s){return new BigDecimal(s);}
    @BeforeEach void qualifiedAdapterFixture() {
        when(provider.fetch(any(),any())).thenAnswer(invocation->{CandidateContext c=invocation.getArgument(0);RoutingContext r=invocation.getArgument(1);
            return new DynamicEvidence(input(new Location(d("10"),d("106")),"WGS84",Kind.VEHICLE_LOCATION,c),
                    input(new Availability(true,c.planningStart(),c.planningEnd()),"INTERVAL",Kind.DRIVER_AVAILABILITY,c),
                    input(new Availability(true,c.planningStart(),c.planningEnd()),"INTERVAL",Kind.TRUCK_AVAILABILITY,c),
                    input(new Route(new Distance(d("10"),"MILE",d("10")),new Distance(d("100"),"MILE",d("100")),r.appointmentStart(),r.appointmentStart().minusSeconds(3600),true,"APPROVED_TEST_PLAN","plan-v1"),"MILE",Kind.ROUTE,c));});
        when(provider.evaluate(any(),any())).thenAnswer(invocation->{CandidateContext c=invocation.getArgument(0);return input(new Assessment("FULL_TEST_HOS_RULES","rules-v1","APPROVED_TEST_PLAN","plan-v1",true,true,true,true,true,true,d(".20")),"RATIO",Kind.HOS,c);});
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    <T> Input<T> input(T value,String unit,Kind kind,CandidateContext c) {return new Input<>(value,new Provenance(
            new Source("QUALIFIED_TEST_ONLY",kind.name(),"test-v1",SourceClass.TRUSTED_ADAPTER),c,"APPROVED_TEST_EVIDENCE","evidence-v1",unit,c.planningStart(),c.planningStart().plusSeconds(7200),null));}
    Fixture fixture() {
        var support=new OptimizationAuditPostgresTest();support.jdbc=jdbc;support.policies=policies;var entities=support.fixture();var policy=support.publish(entities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(entities.email(),"test",List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        LocalDate date=LocalDate.of(2026,10,5);dates.remediate(entities.load(),new SetPickupBusinessDateRequest(date,new PickupBusinessDateProvenance("CUSTOMER_PROMISE","Explicit test pickup promise","Approved customer agreement"),null));
        UUID customer=jdbc.queryForObject("select customer_id from loads where id=?",UUID.class,entities.load());
        var contract=ratePolicies.createContract(new RatingContractRequest(customer,"USD",date,date.plusDays(7)),entities.actor());
        ratePolicies.createRule(new RateRuleRequest(10,customer,contract.contractId(),1,null,null,null,null,"USD",date,date.plusDays(7),RatingMethod.FLAT,d("1000"),null,null,null,null),entities.actor());
        var request=new RatingPreviewRequest(contract.contractId(),1,null,null,null,null,"USD","Approved test agreement",null,null,List.of());
        var preview=previews.preview(entities.load(),request,"optimizer-fixture");
        var accepted=snapshots.accept(entities.load(),new RatingAcceptRequest("opt-rating-"+UUID.randomUUID(),request,preview.inputHash(),preview.resultHash(),null,null,null),entities.actor(),"optimizer-fixture");
        UUID stop=jdbc.queryForObject("select id from trip_stops where trip_id=? and load_id=?",UUID.class,entities.trip(),entities.load());
        Instant appointment=now().plusSeconds(7200);jdbc.update("update trip_stops set appointment_start=?,appointment_end=? where id=?",appointment.atOffset(ZoneOffset.UTC),appointment.plusSeconds(3600).atOffset(ZoneOffset.UTC),stop);
        Scope scope=new Scope(entities.load(),entities.trip(),entities.actor(),entities.truck());
        return new Fixture(entities,policy,new Target(entities.load(),entities.trip(),accepted.snapshotId(),stop),selection(entities,policy,scope));
    }
    SourceSelection selection(OptimizationAuditPostgresTest.Fixture entities,PublishedPolicy policy,Scope scope) {
        Instant at=now();Source capacitySource=policy.eligibilitySourcePolicy().qualifiedSources().get(Kind.CAPACITY).iterator().next();
        var cap=sources.capture(new CaptureRequest(UUID.randomUUID(),policy.id(),Kind.CAPACITY,scope,capacitySource,"POUND",at,at.plusSeconds(7200),null,null,
                new CapacityRequest(new WeightRequest(d("1000"),"POUND"),new WeightRequest(d("40000"),"POUND")),null,null,"TEST_APPROVAL","SOURCE_CONFIRMED","Approved capacity source"),entities.actor());
        return selectionRest(entities,policy,scope,cap.id());
    }
    SourceSelection selectionRest(OptimizationAuditPostgresTest.Fixture entities,PublishedPolicy policy,Scope scope,UUID capacityId) {
        Instant at=now();var q=new QualificationRequest(true,true,false,true,false,true,true,false,at.minusSeconds(1),at.plusSeconds(7*24*3600));
        var qualification=sources.capture(new CaptureRequest(UUID.randomUUID(),policy.id(),Kind.QUALIFICATION,scope,policy.eligibilitySourcePolicy().qualifiedSources().get(Kind.QUALIFICATION).iterator().next(),"QUALIFICATION",at,at.plusSeconds(7200),null,null,null,q,null,"TEST_APPROVAL","SOURCE_CONFIRMED","Approved effective qualifications"),entities.actor());
        var costs=new ArrayList<CostRequest>();
        for(var category:Map.of("FUEL","100","DRIVER","200","TOLL","10").entrySet()) {
            UUID id=UUID.randomUUID();jdbc.update("""
                    insert into shipment_costs(id,load_id,trip_id,driver_id,truck_id,category,cost_basis,status,source_type,amount,currency,approved_by,approved_at)
                    values (?,?,?,?,?,?,'ESTIMATE','APPROVED','APPROVED_TEST_FORECAST',?,'USD',?,?)
                    """,id,scope.loadId(),scope.tripId(),scope.driverId(),scope.truckId(),category.getKey(),d(category.getValue()),entities.actor(),at.atOffset(ZoneOffset.UTC));
            costs.add(new CostRequest(id,"APPROVED_TEST_FORECAST",1,null));
        }
        var forecast=sources.capture(new CaptureRequest(UUID.randomUUID(),policy.id(),Kind.FORECAST_COST,scope,policy.eligibilitySourcePolicy().qualifiedSources().get(Kind.FORECAST_COST).iterator().next(),"USD",at,at.plusSeconds(7200),null,null,null,null,new ForecastRequest(false,false,costs),"TEST_APPROVAL","SOURCE_CONFIRMED","Approved candidate variable forecasts"),entities.actor());
        return new SourceSelection(scope,capacityId,qualification.id(),forecast.id());
    }
    CreateRequest command(Fixture f){return new CreateRequest("run-"+UUID.randomUUID(),f.policy().id(),List.of(f.target()),List.of(f.entities().actor()),List.of(f.entities().truck()),List.of(f.selection()));}
    @Test void realAcceptedRatingAndQualifiedLedgerProduceExplainableImmutableRunWithoutAssignment() {
        var f=fixture();var outcome=service.create(command(f),f.entities().actor(),"TEST_CORRELATION");assertEquals(outcome,service.get(outcome.run().id()));
        var c=outcome.candidates().getFirst();assertTrue(c.feasible(),c.rejectionCodes().toString());assertEquals(1,c.rank());assertEquals(d(".80500000"),c.finalScore());
        assertEquals(f.target().ratingSnapshotId(),c.explanation().forecast().ratingSnapshotId());assertEquals(d("1000.00"),c.explanation().forecast().expectedRevenue());
        assertEquals(0,d("310").compareTo(c.explanation().forecast().expectedVariableCost()));assertEquals(c.finalScore(),c.explanation().score().components().values().stream().map(OptimizationScoringEngine.ComponentResult::contribution).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals("TEST_TENANT_ONLY",outcome.run().requestSnapshot().tenantScope());assertEquals(72,Duration.between(outcome.run().createdAt(),outcome.run().planningUntil()).toHours());
        assertEquals(0,jdbc.queryForObject("select count(*) from trip_driver_assignments where trip_id=?",Integer.class,f.entities().trip()));assertNull(jdbc.queryForObject("select truck_id from trips where id=?",UUID.class,f.entities().trip()));
        assertNull(jdbc.queryForObject("select dispatched_at from trips where id=?",OffsetDateTime.class,f.entities().trip()));
    }
    @Test void retryAndConcurrentSameRunKeyHaveSingleOutcomeAndPayloadDriftConflicts() throws Exception {
        var f=fixture();var command=command(f);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Outcome> call=()->{start.await();return service.create(command,f.entities().actor(),"concurrent");};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
        var original=service.create(command,f.entities().actor(),"retry");clearInvocations(provider);assertEquals(original,service.create(command,f.entities().actor(),"retry-2"));verifyNoInteractions(provider);
        var drift=new CreateRequest(command.idempotencyKey(),command.policyId(),command.targets(),command.driverIds(),command.truckIds(),List.of());
        assertEquals("OPTIMIZATION_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->service.create(drift,f.entities().actor(),"changed")).getCode());
        assertEquals(1,jdbc.queryForObject("select count(*) from optimization_runs where idempotency_key=?",Integer.class,command.idempotencyKey()));
    }
    @Test void missingSourceHistoricalDateUnknownStatusAndStartedTripAreUnscoredWithReasons() {
        var f=fixture();var r=command(f);var missing=new CreateRequest(r.idempotencyKey(),r.policyId(),r.targets(),r.driverIds(),r.truckIds(),List.of());
        var rejected=service.create(missing,f.entities().actor(),"missing").candidates().getFirst();assertFalse(rejected.feasible());assertNull(rejected.finalScore());assertNull(rejected.rank());
        assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_INPUT_EVIDENCE_REQUIRED"));
        jdbc.update("update employees set status='UNKNOWN_OPT_STATE' where id=?",f.entities().actor());clearInvocations(provider);
        rejected=service.create(command(f),f.entities().actor(),"status").candidates().getFirst();assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_STATUS_NOT_ALLOWED"));verifyNoInteractions(provider);
        jdbc.update("update employees set status='ACTIVE' where id=?",f.entities().actor());jdbc.update("update trip_stops set arrived_at=now() where id=?",f.target().pickupStopId());
        rejected=service.create(command(f),f.entities().actor(),"started").candidates().getFirst();assertFalse(rejected.feasible());assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_STATUS_NOT_ALLOWED"));
    }
    @Test void qualifiedSourceSupersessionAndLedgerDriftExcludeCandidateRatherThanRerate() {
        var f=fixture();var record=sources.get(f.selection().capacityInputId());
        var corrected=new CaptureRequest(UUID.randomUUID(),f.policy().id(),Kind.CAPACITY,record.scope(),record.source(),"POUND",now(),now().plusSeconds(7200),null,record.id(),
                new CapacityRequest(new WeightRequest(d("1000"),"POUND"),new WeightRequest(d("40000"),"POUND")),null,null,"TEST_APPROVAL","SOURCE_CORRECTION","Explicit new source version");
        sources.capture(corrected,f.entities().actor());var rejected=service.create(command(f),f.entities().actor(),"superseded").candidates().getFirst();assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_CANDIDATE_STALE"));assertNull(rejected.finalScore());
        var second=fixture();UUID cost=sources.get(second.selection().forecastInputId()).payload().forecast().costs().getFirst().costId();jdbc.update("update shipment_costs set amount=amount+1,version=version+1 where id=?",cost);
        rejected=service.create(command(second),second.entities().actor(),"ledger").candidates().getFirst();assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_CANDIDATE_STALE"));assertNull(rejected.finalScore());
    }
    @Test void fullHosFailureAndUnavailableProviderNeverScoreOrMakeAssignment() {
        var f=fixture();doAnswer(invocation->{CandidateContext c=invocation.getArgument(0);return input(new Assessment("FULL_TEST_HOS_RULES","rules-v1","APPROVED_TEST_PLAN","plan-v1",true,false,false,false,false,false,d(".20")),"RATIO",Kind.HOS,c);}).when(provider).evaluate(any(),any());
        var rejected=service.create(command(f),f.entities().actor(),"hos").candidates().getFirst();assertTrue(rejected.rejectionCodes().contains("HOS_INFEASIBLE"));assertNull(rejected.finalScore());
        doThrow(new com.company.logicstic.exception.BadRequestException("OPTIMIZATION_SOURCE_UNAVAILABLE","Configured source unavailable")).when(provider).fetch(any(),any());
        rejected=service.create(command(f),f.entities().actor(),"source").candidates().getFirst();assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_SOURCE_UNAVAILABLE"));assertNull(rejected.finalScore());
        assertEquals(0,jdbc.queryForObject("select count(*) from trip_driver_assignments where trip_id=?",Integer.class,f.entities().trip()));
    }
    @Test void resourceChangeDuringExternalForecastRejectsInsteadOfOverwritingNewState() {
        var f=fixture();doAnswer(invocation->{jdbc.update("update trucks set status='UNAVAILABLE_NEW_STATE' where id=?",f.entities().truck());
            CandidateContext c=invocation.getArgument(0);RoutingContext r=invocation.getArgument(1);return new DynamicEvidence(input(new Location(d("10"),d("106")),"WGS84",Kind.VEHICLE_LOCATION,c),
                    input(new Availability(true,c.planningStart(),c.planningEnd()),"INTERVAL",Kind.DRIVER_AVAILABILITY,c),input(new Availability(true,c.planningStart(),c.planningEnd()),"INTERVAL",Kind.TRUCK_AVAILABILITY,c),
                    input(new Route(new Distance(d("10"),"MILE",d("10")),new Distance(d("100"),"MILE",d("100")),r.appointmentStart(),r.appointmentStart().minusSeconds(3600),true,"APPROVED_TEST_PLAN","plan-v1"),"MILE",Kind.ROUTE,c));}).when(provider).fetch(any(),any());
        var c=service.create(command(f),f.entities().actor(),"changed-during-provider").candidates().getFirst();assertFalse(c.feasible());assertTrue(c.rejectionCodes().containsAll(List.of("OPTIMIZATION_CANDIDATE_STALE","OPTIMIZATION_STATUS_NOT_ALLOWED")));assertNull(c.finalScore());
    }
    @Test void crossTenantIdsAndForeignRatingCannotCreateFeasibleCandidateOrReadRun() {
        var f=fixture();var r=command(f);var foreign=new CreateRequest(r.idempotencyKey(),r.policyId(),r.targets(),List.of(UUID.randomUUID()),r.truckIds(),List.of());
        assertEquals(404,assertThrows(ApiException.class,()->service.create(foreign,f.entities().actor(),"foreign")).getStatus().value());
        var other=fixture();var wrong=new CreateRequest("wrong-rating-"+UUID.randomUUID(),r.policyId(),List.of(new Target(f.target().loadId(),f.target().tripId(),other.target().ratingSnapshotId(),f.target().pickupStopId())),r.driverIds(),r.truckIds(),r.sourceSelections());
        var rejected=service.create(wrong,f.entities().actor(),"foreign-rating").candidates().getFirst();assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_ACCEPTED_RATING_REQUIRED"));assertFalse(rejected.feasible());assertNull(rejected.finalScore());
        assertEquals(404,assertThrows(ApiException.class,()->service.get(UUID.randomUUID())).getStatus().value());
    }
    @Test void scopedApiUsesExistingDispatcherAuthorizationAndNoFinancialWrite() throws Exception {
        var f=fixture();var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();var r=command(f);
        mvc.perform(post("/api/optimization/runs").with(user(f.entities().email()).roles("ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(r))).andExpect(status().isForbidden());
        var response=mvc.perform(post("/api/optimization/runs").with(user(f.entities().email()).roles("DISPATCHER")).contentType("application/json").content(json.writeValueAsString(r))).andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("data").path("run").path("id").asText());
        mvc.perform(get("/api/optimization/runs/"+id).with(user(f.entities().email()).roles("DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/optimization/runs/"+id).with(user(f.entities().email()).roles("DRIVER"))).andExpect(status().isForbidden());
        assertEquals(1,jdbc.queryForObject("select count(*) from accepted_rating_snapshots where load_id=?",Integer.class,f.entities().load()));
        assertEquals(0,jdbc.queryForObject("select count(*) from invoices where load_id=?",Integer.class,f.entities().load()));
    }
    @Test void sameScoreCandidatesPersistEqualDenseRankAndNextLowerScoreHasRankTwo() {
        var f=fixture();UUID second=UUID.randomUUID(),third=UUID.randomUUID();
        for(UUID id:List.of(second,third))jdbc.update("""
                insert into trucks(id,number,type,vehicle_capacity,status,is_hazmat_placarded,adr_equipment_allowed_classes,adr_equipment_is_adr_certified)
                values (?,?,'SEMI',1,'ACTIVE',false,'',false)
                """,id,id.toString());
        var selections=List.of(f.selection(),selection(f.entities(),f.policy(),new Scope(f.target().loadId(),f.target().tripId(),f.entities().actor(),second)),
                selection(f.entities(),f.policy(),new Scope(f.target().loadId(),f.target().tripId(),f.entities().actor(),third)));
        doAnswer(invocation->{CandidateContext c=invocation.getArgument(0);RoutingContext r=invocation.getArgument(1);BigDecimal miles=c.truckId().equals(third)?d("60"):d("10");
            return new DynamicEvidence(input(new Location(d("10"),d("106")),"WGS84",Kind.VEHICLE_LOCATION,c),
                    input(new Availability(true,c.planningStart(),c.planningEnd()),"INTERVAL",Kind.DRIVER_AVAILABILITY,c),input(new Availability(true,c.planningStart(),c.planningEnd()),"INTERVAL",Kind.TRUCK_AVAILABILITY,c),
                    input(new Route(new Distance(miles,"MILE",miles),new Distance(d("100"),"MILE",d("100")),r.appointmentStart(),r.appointmentStart().minusSeconds(3600),true,"APPROVED_TEST_PLAN","plan-v1"),"MILE",Kind.ROUTE,c));}).when(provider).fetch(any(),any());
        var request=new CreateRequest("tied-"+UUID.randomUUID(),f.policy().id(),List.of(f.target()),List.of(f.entities().actor()),List.of(f.entities().truck(),second,third),selections);
        var result=service.create(request,f.entities().actor(),"dense-rank");assertTrue(result.candidates().stream().allMatch(Candidate::feasible));
        assertEquals(List.of(1,1,2),result.candidates().stream().map(Candidate::rank).toList());assertEquals(result,service.get(result.run().id()));
        assertEquals(result,service.create(new CreateRequest(request.idempotencyKey(),request.policyId(),request.targets(),request.driverIds(),request.truckIds().reversed(),request.sourceSelections().reversed()),f.entities().actor(),"normalized-retry"));
    }
    @Test void historicalMissingBusinessDateRemainsNullAndTimestampIsNeverUsedForOptimizerEligibility() {
        var f=fixture();var support=new OptimizationAuditPostgresTest();support.jdbc=jdbc;support.policies=policies;var historical=support.fixture();
        UUID stop=jdbc.queryForObject("select id from trip_stops where trip_id=? and load_id=?",UUID.class,historical.trip(),historical.load());
        jdbc.update("update loads set requested_pickup_date=now() where id=?",historical.load());
        var request=new CreateRequest("historical-"+UUID.randomUUID(),f.policy().id(),List.of(new Target(historical.load(),historical.trip(),f.target().ratingSnapshotId(),stop)),List.of(historical.actor()),List.of(historical.truck()),List.of());
        var rejected=service.create(request,historical.actor(),"historical-date").candidates().getFirst();assertFalse(rejected.feasible());assertTrue(rejected.rejectionCodes().contains("RATING_PRICING_DATE_REQUIRED"));assertNull(rejected.finalScore());
        assertNull(jdbc.queryForObject("select requested_pickup_business_date from loads where id=?",LocalDate.class,historical.load()));
    }
    @Test void acceptedRevenueCurrencyMustMatchEveryQualifiedCostWithoutImplicitFxOrHistoricalMutation() {
        var f=fixture();UUID customer=jdbc.queryForObject("select customer_id from loads where id=?",UUID.class,f.entities().load());LocalDate date=LocalDate.of(2026,10,5);
        var contract=ratePolicies.createContract(new RatingContractRequest(customer,"EUR",date,date.plusDays(7)),f.entities().actor());
        ratePolicies.createRule(new RateRuleRequest(10,customer,contract.contractId(),1,null,null,null,null,"EUR",date,date.plusDays(7),RatingMethod.FLAT,d("1000"),null,null,null,null),f.entities().actor());
        var request=new RatingPreviewRequest(contract.contractId(),1,null,null,null,null,"EUR","Approved EUR agreement",null,null,List.of());var preview=previews.preview(f.entities().load(),request,"currency-fixture");
        var eur=snapshots.accept(f.entities().load(),new RatingAcceptRequest("eur-"+UUID.randomUUID(),request,preview.inputHash(),preview.resultHash(),null,null,null),f.entities().actor(),"currency-fixture");
        var command=new CreateRequest("currency-"+UUID.randomUUID(),f.policy().id(),List.of(new Target(f.target().loadId(),f.target().tripId(),eur.snapshotId(),f.target().pickupStopId())),List.of(f.entities().actor()),List.of(f.entities().truck()),List.of(f.selection()));
        var rejected=service.create(command,f.entities().actor(),"currency").candidates().getFirst();assertFalse(rejected.feasible());assertTrue(rejected.rejectionCodes().contains("OPTIMIZATION_CURRENCY_MISMATCH"));assertNull(rejected.finalScore());
        assertEquals("USD",snapshots.get(f.target().ratingSnapshotId()).calculation().currency());assertEquals("EUR",snapshots.get(eur.snapshotId()).calculation().currency());
    }
}
