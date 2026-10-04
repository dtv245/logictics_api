package com.company.logicstic.integration;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.OptimizationQualifiedInputRepository;
import com.company.logicstic.service.optimization.*;
import com.company.logicstic.service.optimization.OptimizationQualifiedInputService.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.PublishedPolicy;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class OptimizationQualifiedInputPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;
    @Autowired OptimizationPolicyService policies;
    @Autowired OptimizationQualifiedInputService service;
    @Autowired OptimizationQualifiedInputRepository repository;
    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper json;
    record Fixture(OptimizationAuditPostgresTest.Fixture entities, PublishedPolicy policy) {
        Scope scope(){return new Scope(entities.load(),entities.trip(),entities.actor(),entities.truck());}
    }
    Fixture fixture() {
        // Reuse the real domain fixture, not mock production repositories or fake financial totals.
        var support=new OptimizationAuditPostgresTest(); support.jdbc=jdbc; support.policies=policies;
        var entities=support.fixture(); return new Fixture(entities,support.publish(entities));
    }
    Instant now(){return Instant.now().truncatedTo(ChronoUnit.MICROS);}
    CaptureRequest request(Fixture f,Kind kind,CapacityRequest capacity,QualificationRequest qualification,ForecastRequest forecast,String unit,UUID supersedes) {
        Instant at=now(); return new CaptureRequest(UUID.randomUUID(),f.policy().id(),kind,f.scope(),
                f.policy().eligibilitySourcePolicy().qualifiedSources().get(kind).iterator().next(),unit,at,at.plusSeconds(7200),null,supersedes,
                capacity,qualification,forecast,"TEST_ONLY_QUALIFIED_APPROVAL","SOURCE_CONFIRMED","Test-only source with explicit provenance");
    }
    CaptureRequest capacity(Fixture f,UUID supersedes) {
        return request(f,Kind.CAPACITY,new CapacityRequest(new WeightRequest(new BigDecimal("1000"),"KILOGRAM"),
                new WeightRequest(new BigDecimal("40000"),"POUND")),null,null,"POUND",supersedes);
    }
    CandidateContext context(Fixture f){Instant at=now();return new CandidateContext(f.scope().loadId(),f.scope().tripId(),f.scope().driverId(),f.scope().truckId(),at,at.plusSeconds(72*3600));}
    UUID cost(Fixture f,String category,String basis,String status,String currency,String amount) {
        UUID id=UUID.randomUUID();jdbc.update("""
                insert into shipment_costs(id,load_id,trip_id,driver_id,truck_id,category,cost_basis,status,source_type,amount,currency,approved_by,approved_at)
                values (?,?,?,?,?,?,?,?,?,?,?, ?,?)
                """,id,f.scope().loadId(),f.scope().tripId(),f.scope().driverId(),f.scope().truckId(),category,basis,status,"QUALIFIED_TEST_FORECAST",new BigDecimal(amount),currency,
                f.entities().actor(),now().atOffset(ZoneOffset.UTC));return id;
    }
    ForecastRequest forecast(Fixture f,String tollAmount,String zeroReason) {
        return new ForecastRequest(false,false,List.of(new CostRequest(cost(f,"FUEL","ESTIMATE","APPROVED","USD","100"),"TEST_FORECAST_V1",1,null),
                new CostRequest(cost(f,"DRIVER","ESTIMATE","APPROVED","USD","200"),"TEST_FORECAST_V1",1,null),
                new CostRequest(cost(f,"TOLL","ESTIMATE","APPROVED","USD",tollAmount),"TEST_FORECAST_V1",1,zeroReason)));
    }
    @Test void capacityCapturesOriginalUnitAndDecimalConversionWithoutReadingBareLegacyCapacity() {
        var f=fixture();var captured=service.capture(capacity(f,null),f.entities().actor());assertEquals(captured,service.get(captured.id()));
        assertEquals("KILOGRAM",captured.payload().capacity().cargo().originalUnit());
        assertEquals(new OptimizationEvidenceValidator().weight(new BigDecimal("1000"),"KILOGRAM"),captured.payload().capacity().cargo());
        assertEquals(1,jdbc.queryForObject("select vehicle_capacity from trucks where id=?",Integer.class,f.scope().truckId()));
        assertNull(jdbc.queryForObject("select requested_pickup_business_date from loads where id=?",LocalDate.class,f.scope().loadId()));
        assertEquals(captured,service.resolve(captured.id(),f.policy().id(),Kind.CAPACITY,context(f),now()));
    }
    @Test void qualificationRequiresEveryExplicitAssertionAndPreservesEffectiveVersionedDbProof() {
        var f=fixture();Instant at=now();var q=new QualificationRequest(true,true,false,true,false,true,true,false,at,at.plusSeconds(1000000));
        var request=request(f,Kind.QUALIFICATION,null,q,null,"QUALIFICATION",null);var captured=service.capture(request,f.entities().actor());
        assertEquals(SourceClass.AUTHORITATIVE_DB,captured.source().classification());assertEquals(at,captured.payload().qualification().effectiveFrom());
        var missing=new QualificationRequest(true,true,false,true,false,true,true,null,at,at.plusSeconds(1000000));
        assertEquals("OPTIMIZATION_INPUT_EVIDENCE_INVALID",assertThrows(ApiException.class,()->service.capture(request(f,Kind.QUALIFICATION,null,missing,null,"QUALIFICATION",null),f.entities().actor())).getCode());
    }
    @Test void missingFreshnessUnknownSourceOrForeignCandidateFailClosed() {
        var f=fixture();var r=capacity(f,null);
        var stale=new CaptureRequest(r.id(),r.policyId(),r.kind(),r.scope(),r.source(),r.unit(),now().minusSeconds(100),now().minusSeconds(1),null,null,r.capacity(),null,null,r.approvalReference(),r.reasonCode(),r.reason());
        assertEquals("OPTIMIZATION_INPUT_STALE",assertThrows(ApiException.class,()->service.capture(stale,f.entities().actor())).getCode());
        var source=new Source("UNREGISTERED",r.source().reference(),r.source().version(),r.source().classification());
        var unregistered=new CaptureRequest(r.id(),r.policyId(),r.kind(),r.scope(),source,r.unit(),r.observedAt(),r.expiresAt(),null,null,r.capacity(),null,null,r.approvalReference(),r.reasonCode(),r.reason());
        assertEquals("OPTIMIZATION_INPUT_EVIDENCE_INVALID",assertThrows(ApiException.class,()->service.capture(unregistered,f.entities().actor())).getCode());
        var foreign=new CaptureRequest(r.id(),r.policyId(),r.kind(),new Scope(UUID.randomUUID(),r.scope().tripId(),r.scope().driverId(),r.scope().truckId()),r.source(),r.unit(),r.observedAt(),r.expiresAt(),null,null,r.capacity(),null,null,r.approvalReference(),r.reasonCode(),r.reason());
        assertEquals(404,assertThrows(ApiException.class,()->service.capture(foreign,f.entities().actor())).getStatus().value());
    }
    @Test void captureRetryReturnsOriginalAuditAndDriftConflicts() {
        var f=fixture();var request=capacity(f,null);var captured=service.capture(request,f.entities().actor());assertEquals(captured,service.capture(request,f.entities().actor()));
        var drift=new CaptureRequest(request.id(),request.policyId(),request.kind(),request.scope(),request.source(),request.unit(),request.observedAt(),request.expiresAt(),null,null,request.capacity(),null,null,request.approvalReference(),request.reasonCode(),"Changed");
        assertEquals("OPTIMIZATION_INPUT_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->service.capture(drift,f.entities().actor())).getCode());
        assertEquals(1,jdbc.queryForObject("select count(*) from optimization_qualified_inputs where id=?",Integer.class,request.id()));
    }
    @Test void immutableSourceCorrectionAppendsVersionAndInvalidatesOldEvidence() {
        var f=fixture();var old=service.capture(capacity(f,null),f.entities().actor());var correction=service.capture(capacity(f,old.id()),f.entities().actor());
        assertEquals(2,correction.evidenceVersion());assertEquals(old,service.get(old.id()));
        assertEquals("OPTIMIZATION_CANDIDATE_STALE",assertThrows(ApiException.class,()->service.resolve(old.id(),f.policy().id(),Kind.CAPACITY,context(f),now())).getCode());
        assertThrows(DataAccessException.class,()->jdbc.update("update optimization_qualified_inputs set reason='Changed' where id=?",old.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from optimization_qualified_inputs where id=?",correction.id()));
    }
    @Test void concurrentCorrectionsCannotBranchOneEvidenceVersion() throws Exception {
        var f=fixture();var old=service.capture(capacity(f,null),f.entities().actor());var a=capacity(f,old.id());var b=capacity(f,old.id());var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> first=()->{start.await();try {service.capture(a,f.entities().actor());return "CAPTURED";}catch(ApiException e){return e.getCode();}};
            Callable<String> second=()->{start.await();try {service.capture(b,f.entities().actor());return "CAPTURED";}catch(ApiException e){return e.getCode();}};
            var x=pool.submit(first);var y=pool.submit(second);start.countDown();assertEquals(Set.of("CAPTURED","OPTIMIZATION_INPUT_ALREADY_SUPERSEDED"),Set.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS)));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from optimization_qualified_inputs where supersedes_input_id=?",Integer.class,old.id()));
    }
    @Test void approvedForecastPinsRealLedgerAndExplicitZeroAuditWithNoAccountingMutation() {
        var f=fixture();var forecast=forecast(f,"0","No toll on approved route");var request=request(f,Kind.FORECAST_COST,null,null,forecast,"USD",null);
        var captured=service.capture(request,f.entities().actor());var costs=captured.payload().forecast().costs();
        assertEquals(3,costs.size());var zero=costs.stream().filter(c->c.amount().signum()==0).findFirst().orElseThrow().zeroEvidence();
        assertEquals("ZERO_COST_CONFIRMED",zero.reasonCode());assertEquals(captured.capturedAt(),zero.confirmedAt());assertEquals(f.entities().actor(),zero.confirmedBy());
        assertEquals(3,jdbc.queryForObject("select count(*) from optimization_qualified_forecast_costs where qualified_input_id=?",Integer.class,captured.id()));
        assertEquals(0L,jdbc.queryForObject("select max(version) from shipment_costs where load_id=?",Long.class,f.scope().loadId()));
        assertEquals(captured,service.resolve(captured.id(),f.policy().id(),Kind.FORECAST_COST,context(f),now()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from optimization_qualified_forecast_costs where qualified_input_id=?",captured.id()));
        var reordered=new ForecastRequest(false,false,forecast.costs().reversed());
        var retry=new CaptureRequest(request.id(),request.policyId(),request.kind(),request.scope(),request.source(),request.unit(),request.observedAt(),request.expiresAt(),null,null,null,null,reordered,request.approvalReference(),request.reasonCode(),request.reason());
        assertEquals(captured,service.capture(retry,f.entities().actor()));
    }
    @Test void missingConditionalCoverageZeroEvidenceOrNonApprovedEstimateIsRejected() {
        var f=fixture();var noZero=forecast(f,"0",null);
        assertEquals("FORECAST_COST_INCOMPLETE",assertThrows(ApiException.class,()->service.capture(request(f,Kind.FORECAST_COST,null,null,noZero,"USD",null),f.entities().actor())).getCode());
        var rows=forecast(f,"10",null);var partial=new ForecastRequest(false,true,rows.costs());
        assertEquals("FORECAST_COST_INCOMPLETE",assertThrows(ApiException.class,()->service.capture(request(f,Kind.FORECAST_COST,null,null,partial,"USD",null),f.entities().actor())).getCode());
        jdbc.update("update shipment_costs set cost_basis='ACTUAL',status='POSTED' where id=?",rows.costs().getFirst().costId());
        assertEquals("FORECAST_COST_INCOMPLETE",assertThrows(ApiException.class,()->service.capture(request(f,Kind.FORECAST_COST,null,null,rows,"USD",null),f.entities().actor())).getCode());
        var unknown=new ForecastRequest(null,false,rows.costs());assertThrows(ApiException.class,()->service.capture(request(f,Kind.FORECAST_COST,null,null,unknown,"USD",null),f.entities().actor()));
    }
    @Test void currencyAndLiveLedgerDriftAreNotSilentlyConvertedOrResnapshotted() {
        var f=fixture();var forecast=forecast(f,"10",null);UUID first=forecast.costs().getFirst().costId();
        jdbc.update("update shipment_costs set currency='EUR' where id=?",first);
        assertEquals("OPTIMIZATION_CURRENCY_MISMATCH",assertThrows(ApiException.class,()->service.capture(request(f,Kind.FORECAST_COST,null,null,forecast,"USD",null),f.entities().actor())).getCode());
        jdbc.update("update shipment_costs set currency='USD' where id=?",first);var captured=service.capture(request(f,Kind.FORECAST_COST,null,null,forecast,"USD",null),f.entities().actor());
        jdbc.update("update shipment_costs set amount=101,version=version+1 where id=?",first);
        assertEquals("OPTIMIZATION_CANDIDATE_STALE",assertThrows(ApiException.class,()->service.resolve(captured.id(),f.policy().id(),Kind.FORECAST_COST,context(f),now())).getCode());
        assertEquals(captured,service.get(captured.id()));
    }
    @Test void sqlGuardsRejectUnregisteredSourceWrongContextAndIncompleteFinancialReferences() {
        var f=fixture();var captured=service.capture(capacity(f,null),f.entities().actor());
        String copy="""
                insert into optimization_qualified_inputs select ?,policy_id,input_kind,load_id,trip_id,driver_id,truck_id,
                ?,source_reference,source_version,source_class,unit,observed_at,expires_at,max_age_seconds,evidence_version,supersedes_input_id,
                payload,approval_reference,reason_code,reason,captured_by,captured_at,normalized_input_hash from optimization_qualified_inputs where id=?
                """;
        assertThrows(DataAccessException.class,()->jdbc.update(copy,UUID.randomUUID(),"UNREGISTERED",captured.id()));
        var forecast=service.capture(request(f,Kind.FORECAST_COST,null,null,forecast(f,"10",null),"USD",null),f.entities().actor());
        assertThrows(DataAccessException.class,()->jdbc.update(copy,UUID.randomUUID(),forecast.source().type(),forecast.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("""
                insert into optimization_qualified_forecast_costs select ?,cost_id,ledger_version,category,amount,currency from optimization_qualified_forecast_costs where qualified_input_id=?
                """,captured.id(),forecast.id()));
    }
    @Test void authorizationProtectsSourceAuthoringAndAccountingCannotForgeQualification() throws Exception {
        var f=fixture();var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();var request=capacity(f,null);
        for(String role:List.of("DISPATCHER","ACCOUNTANT","DRIVER"))mvc.perform(post("/api/optimization/qualified-inputs").with(user(f.entities().email()).roles(role)).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isForbidden());
        mvc.perform(post("/api/optimization/qualified-inputs").with(user(f.entities().email()).roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isCreated());
        mvc.perform(post("/api/optimization/qualified-inputs/forecasts").with(user(f.entities().email()).roles("ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        var forecast=request(f,Kind.FORECAST_COST,null,null,forecast(f,"10",null),"USD",null);
        mvc.perform(post("/api/optimization/qualified-inputs/forecasts").with(user(f.entities().email()).roles("ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(forecast))).andExpect(status().isCreated());
        mvc.perform(get("/api/optimization/qualified-inputs/"+request.id()).with(user(f.entities().email()).roles("DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/optimization/qualified-inputs/"+request.id()).with(user(f.entities().email()).roles("DRIVER"))).andExpect(status().isForbidden());
    }
}
