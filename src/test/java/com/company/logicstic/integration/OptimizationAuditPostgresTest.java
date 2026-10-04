package com.company.logicstic.integration;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.OptimizationAuditRepository;
import com.company.logicstic.service.optimization.OptimizationPolicyService;
import com.company.logicstic.service.optimization.OptimizationPolicyService.PublishRequest;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy;
import java.time.*;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class OptimizationAuditPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;
    @Autowired OptimizationAuditRepository audit;
    @Autowired OptimizationPolicyService policies;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext web;
    @Autowired org.springframework.transaction.PlatformTransactionManager tx;
    record Fixture(UUID actor, String email, UUID load, UUID trip, UUID truck) {}
    Fixture fixture() {
        UUID actor=UUID.randomUUID(), customer=UUID.randomUUID(), load=UUID.randomUUID(), trip=UUID.randomUUID(), truck=UUID.randomUUID(); String email=actor+"@optimizer.invalid";
        jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Opt','Audit','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Opt audit','ACTIVE',false)",customer);
        jdbc.update("""
                insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
                destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
                destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude)
                values (?,'Opt audit','FTL','OPEN',999999,false,?,'MANUAL',false,0,'USD','City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0)
                """,load,customer);
        jdbc.update("""
                insert into trucks(id,number,type,vehicle_capacity,status,is_hazmat_placarded,adr_equipment_allowed_classes,adr_equipment_is_adr_certified)
                values (?,?,'SEMI',1,'ACTIVE',false,'',false)
                """,truck,truck.toString());
        jdbc.update("insert into trips(id,name,total_distance,status) values (?,'Opt audit',999999,'PLANNED')",trip);
        jdbc.update("""
                insert into trip_stops(id,type,trip_id,"order",load_id,address_city,address_country,address_line1,address_state,address_zip_code,location_latitude,location_longitude)
                values (?,'PICKUP',?,1,?,'City','US','Address','TX','00000',0,0)
                """,UUID.randomUUID(),trip,load);
        return new Fixture(actor,email,load,trip,truck);
    }
    PublishRequest request(String code) {
        var statuses=new EnumMap<EntityKind,Set<String>>(EntityKind.class);
        statuses.put(EntityKind.LOAD,Set.of("OPEN")); statuses.put(EntityKind.TRIP,Set.of("PLANNED"));
        statuses.put(EntityKind.DRIVER,Set.of("ACTIVE")); statuses.put(EntityKind.TRUCK,Set.of("ACTIVE"));
        var sources=new EnumMap<Kind,Set<Source>>(Kind.class);
        for(Kind kind:Kind.values()) sources.put(kind,Set.of(new Source("QUALIFIED_TEST_ONLY",kind.name(),"test-v1",
                kind==Kind.QUALIFICATION?SourceClass.AUTHORITATIVE_DB:SourceClass.TRUSTED_ADAPTER)));
        return new PublishRequest(code,1,new Policy("OPT_ELIGIBILITY_V1",1,72,"OPT_SOURCE_V1",1,statuses,sources),OptimizationScoringPolicy.confirmedV1(),"TEST_ONLY_BUSINESS_APPROVAL");
    }
    PublishedPolicy publish(Fixture f) { return policies.publish(request("test-"+UUID.randomUUID()),f.actor()); }
    Run run(Fixture f,PublishedPolicy p,String key) {
        Instant at=Instant.parse("2026-10-05T00:00:00Z");
        var request=new RunRequest(key,p.id(),List.of(new Target(f.load(),f.trip(),null)),List.of(f.actor()),List.of(f.truck()));
        return new Run(UUID.randomUUID(),p.id(),at,at.plusSeconds(72*3600),f.actor(),key,"a".repeat(64),request,
                new PolicySnapshot(p.eligibilitySourcePolicy(),p.scoringPolicy()),at.plusSeconds(1),1000,"test-correlation");
    }
    Run persistRun(Fixture f,PublishedPolicy p) { var run=run(f,p,"run-"+UUID.randomUUID());new TransactionTemplate(tx).executeWithoutResult(s->audit.insert(run));return run; }
    Candidate rejected(Fixture f,Run run) {
        var context=new CandidateContext(f.load(),f.trip(),f.actor(),f.truck(),run.createdAt(),run.planningUntil());
        var explanation=new Explanation(context,null,null,null,null,false,List.of("OPTIMIZATION_INPUT_EVIDENCE_REQUIRED"),null);
        return new Candidate(UUID.randomUUID(),run.id(),f.load(),f.trip(),f.actor(),f.truck(),null,false,explanation.rejectionCodes(),null,null,"b".repeat(64),explanation);
    }
    @Test void explicitPolicyRoundTripsExactWeightsCurvesSourceVersionsAndActor() {
        var f=fixture();var p=publish(f);assertEquals(p,policies.get(p.id()));
        assertEquals(f.actor(),p.publishedBy());assertEquals("OPT_NUMERIC_V1",p.scoringPolicy().numericPolicy().code());
        assertEquals(new java.math.BigDecimal("0.350000"),p.scoringPolicy().weights().get(OptimizationScoringPolicy.Utility.MARGIN));
    }
    @Test void publishedPolicyCannotBeUpdatedOrDeletedBySql() {
        var f=fixture();var p=publish(f);
        assertThrows(DataAccessException.class,()->jdbc.update("update optimization_policy_versions set approval_reference='changed' where id=?",p.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from optimization_policy_versions where id=?",p.id()));
        assertEquals(p,policies.get(p.id()));
    }
    @Test void samePolicyCommandRetriesReturnOriginalAuditButPayloadDriftConflicts() {
        var f=fixture();var request=request("retry-"+UUID.randomUUID());var p=policies.publish(request,f.actor());assertEquals(p,policies.publish(request,f.actor()));
        var drift=new PublishRequest(request.code(),1,request.eligibilitySourcePolicy(),request.scoringPolicy(),"different-approval");
        assertEquals("OPTIMIZATION_POLICY_IMMUTABLE",assertThrows(ApiException.class,()->policies.publish(drift,f.actor())).getCode());
    }
    @Test void concurrentPolicyPublicationHasOneVersionAndStableActorAudit() throws Exception {
        var f=fixture();var request=request("concurrent-"+UUID.randomUUID());var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<PublishedPolicy> call=()->{start.await();return policies.publish(request,f.actor());};
            var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from optimization_policy_versions where policy_code=?",Integer.class,request.code()));
    }
    @Test void postgresRejectsPolicyWeightCurveOrSourceDefaultsEvenOutsideJava() {
        var f=fixture();var p=publish(f);
        String insert="""
                insert into optimization_policy_versions select ?,?,policy_version,eligibility_source_policy,
                jsonb_set(scoring_policy,?::text[],?::jsonb),approval_reference,published_by,published_at from optimization_policy_versions where id=?
                """;
        assertThrows(DataAccessException.class,()->jdbc.update(insert,UUID.randomUUID(),"invalid-"+UUID.randomUUID(),"{weights,MARGIN}","0.25",p.id()));
        assertThrows(DataAccessException.class,()->jdbc.update(insert,UUID.randomUUID(),"invalid-"+UUID.randomUUID(),"{curves,MARGIN}","[]",p.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("""
                insert into optimization_policy_versions select ?,?,policy_version,'{}'::jsonb,scoring_policy,approval_reference,published_by,published_at
                from optimization_policy_versions where id=?
                """,UUID.randomUUID(),"missing-"+UUID.randomUUID(),p.id()));
    }
    @Test void runPinsExactPolicyScopeInstantAndHorizonWithoutTimestampDateDerivation() {
        var f=fixture();var p=publish(f);var run=persistRun(f,p);assertEquals(run,audit.run(run.id()).orElseThrow());
        assertEquals(72,Duration.between(run.createdAt(),run.planningUntil()).toHours());
        assertNull(jdbc.queryForObject("select requested_pickup_business_date from loads where id=?",LocalDate.class,f.load()));
        assertThrows(DataAccessException.class,()->jdbc.update("update optimization_runs set correlation_id='changed' where id=?",run.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from optimization_runs where id=?",run.id()));
    }
    @Test void postgresRejectsWrongRunPolicySnapshotAndDuplicateCommand() {
        var f=fixture();var p=publish(f);var run=persistRun(f,p);
        assertThrows(DataAccessException.class,()->jdbc.update("""
                insert into optimization_runs select ?,policy_id,created_at,planning_until,created_by,?,normalized_input_hash,request_snapshot,'{}'::jsonb,calculated_at,duration_millis,correlation_id
                from optimization_runs where id=?
                """,UUID.randomUUID(),"wrong-"+UUID.randomUUID(),run.id()));
        assertThrows(DataAccessException.class,()->audit.insert(run(f,p,run.idempotencyKey())));
    }
    @Test void infeasibleCandidateHasImmutableReasonsAndNoFakeScoreOrRank() {
        var f=fixture();var run=persistRun(f,publish(f));var candidate=rejected(f,run);audit.insert(candidate);
        assertEquals(candidate,audit.candidates(run.id()).getFirst());assertNull(candidate.finalScore());assertNull(candidate.rank());
        assertThrows(DataAccessException.class,()->jdbc.update("update optimization_assignments set final_score=0 where id=?",candidate.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from optimization_assignments where id=?",candidate.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("""
                insert into optimization_assignments select ?,run_id,load_id,trip_id,driver_id,truck_id,rating_snapshot_id,feasible,rejection_codes,0.00000000,1,input_fingerprint,explanation
                from optimization_assignments where id=?
                """,UUID.randomUUID(),candidate.id()));
    }
    @Test void candidateFkAndContextCannotReferenceForeignTenantIds() {
        var f=fixture();var run=persistRun(f,publish(f));var original=rejected(f,run);
        var foreign=new Candidate(original.id(),run.id(),UUID.randomUUID(),f.trip(),f.actor(),f.truck(),null,false,original.rejectionCodes(),null,null,original.inputFingerprint(),original.explanation());
        assertThrows(DataAccessException.class,()->audit.insert(foreign));assertTrue(audit.candidates(run.id()).isEmpty());
        assertTrue(audit.policy(UUID.randomUUID()).isEmpty());assertTrue(audit.run(UUID.randomUUID()).isEmpty());
    }
    @Test void postgresHalfEvenBoundaryMatchesApprovedNumericPolicyNotHalfUp() {
        assertEquals(new java.math.BigDecimal("0.10000000"),jdbc.queryForObject("select optimization_half_even_8(0.100000005)",java.math.BigDecimal.class));
        assertEquals(new java.math.BigDecimal("0.10000002"),jdbc.queryForObject("select optimization_half_even_8(0.100000015)",java.math.BigDecimal.class));
    }
    @Test void apiPublicationIsAdminOnlyAndReadUsesExistingDispatcherRole() throws Exception {
        var f=fixture();var request=request("api-"+UUID.randomUUID());var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        for(String role:List.of("DISPATCHER","ACCOUNTANT","DRIVER")) mvc.perform(post("/api/optimization/policies").with(user(f.email()).roles(role)).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isForbidden());
        var response=mvc.perform(post("/api/optimization/policies").with(user(f.email()).roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asText());
        mvc.perform(get("/api/optimization/policies/"+id).with(user(f.email()).roles("DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/optimization/policies/"+id).with(user(f.email()).roles("DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/optimization/policies/"+UUID.randomUUID()).with(user(f.email()).roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
