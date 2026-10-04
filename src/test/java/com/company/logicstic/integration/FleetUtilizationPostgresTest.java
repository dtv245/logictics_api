package com.company.logicstic.integration;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.FleetHistoryRepository;
import com.company.logicstic.service.fleet.FleetHistory.*;
import com.company.logicstic.service.fleet.FleetHistoryService;
import com.company.logicstic.service.fleet.FleetHistoryService.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class FleetUtilizationPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;@Autowired FleetHistoryService fleet;@Autowired FleetHistoryRepository history;
    @Autowired WebApplicationContext web;@Autowired ObjectMapper json;@Autowired org.springframework.context.ApplicationContext app;
    static final Source SOURCE=new Source("AUDITED_TEST_ONLY","actual-test-feed","test-v1");
    record Fixture(UUID actor,String email,UUID load,UUID trip,UUID truck,Policy policy,Instant from,Instant to){}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    Publish publish(String code){return new Publish(code,1,Map.of("IN_TEST",true,"OUT_TEST",false),Map.of("READY_TEST",true,"PLANNED_UNAVAILABLE_TEST",false),Map.of("LOADED_TEST",Activity.PRODUCTIVE,"IDLE_TEST",Activity.NON_PRODUCTIVE,"EXCLUDED_TEST",Activity.EXCLUDED),List.of(SOURCE),"TEST_ONLY_APPROVAL");}
    Fixture fixture() {
        var support=new OptimizationAuditPostgresTest();app.getAutowireCapableBeanFactory().autowireBean(support);var f=support.fixture();
        Instant from=jdbc.queryForObject("select coalesce(max(completed_at), '2020-01-01'::timestamptz) from trips where completed_at<'2024-01-01'::timestamptz",OffsetDateTime.class).toInstant().plusSeconds(86400);
        return new Fixture(f.actor(),f.email(),f.load(),f.trip(),f.truck(),fleet.publish(publish("fleet-test-"+UUID.randomUUID()),f.actor()),from,from.plusSeconds(3600));
    }
    Capture command(Fixture f,Kind kind,String status,Instant from,Instant to) {return new Capture(f.policy().id(),f.truck(),kind,status,from,to,SOURCE,"event-"+UUID.randomUUID(),kind==Kind.ACTIVITY?f.trip():null,kind==Kind.ACTIVITY?f.load():null,kind==Kind.ACTIVITY?"actual-execution-test":null,null,"SOURCE_CAPTURE","Qualified actual test evidence");}
    Event event(Fixture f,Kind kind,String status,Instant from,Instant to){return fleet.capture(command(f,kind,status,from,to),f.actor());}
    void membership(Fixture f){event(f,Kind.MEMBERSHIP,"IN_TEST",f.from(),f.to());}
    void capacity(Fixture f){event(f,Kind.CAPACITY,"READY_TEST",f.from(),f.to());}
    void complete(Fixture f){membership(f);capacity(f);event(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),f.to());}
    Report report(Fixture f){return fleet.report(f.policy().id(),List.of(f.truck()),f.from(),f.to(),null,null,null,"fleet-test");}
    void completed(Fixture f,String loaded,String empty,String actual,Instant at){jdbc.update("update trips set completed_at=?,loaded_miles=?,empty_miles=?,actual_distance_miles=? where id=?",OffsetDateTime.ofInstant(at,ZoneOffset.UTC),new BigDecimal(loaded),new BigDecimal(empty),new BigDecimal(actual),f.trip());}
    Attribute attribution(Fixture f,String loaded,String empty,String actual,Instant at){return new Attribute(f.policy().id(),f.truck(),f.trip(),at,new BigDecimal(loaded),new BigDecimal(empty),new BigDecimal(actual),SOURCE,"mileage-"+UUID.randomUUID(),null,"PROVEN_COMPLETION_TRUCK","Explicit source proves actual execution truck, not current FK");}
    @Test void explicitPublishedPolicyIsImmutableAndNewVersionDoesNotChangeHistory() {
        var f=fixture();complete(f);assertEquals(f.policy(),fleet.publish(publish(f.policy().code()),f.actor()));
        assertThrows(DataAccessException.class,()->jdbc.update("update fleet_policy_versions set approval_reference='changed' where id=?",f.policy().id()));
        var next=new Publish(f.policy().code(),2,f.policy().membershipStates(),f.policy().capacityStates(),Map.of("LOADED_TEST",Activity.NON_PRODUCTIVE),List.of(SOURCE),"TEST_ONLY_NEW_APPROVAL");fleet.publish(next,f.actor());assertEquals(new BigDecimal("100.00"),report(f).utilization().value());
        assertThrows(ApiException.class,()->fleet.publish(new Publish("bad",1,Map.of(),Map.of(),Map.of(),List.of(),"test"),f.actor()));
    }
    @Test void fullyUtilizedRealHistoryReportsHundredPercentWithoutSnapshotWrites() {
        var f=fixture();complete(f);int before=jdbc.queryForObject("select count(*) from vehicle_status_events",Integer.class);var r=report(f);report(f);
        assertEquals(new BigDecimal("100.00"),r.utilization().value());assertEquals(0,r.coverage().getFirst().gapSeconds().signum());assertEquals(before,jdbc.queryForObject("select count(*) from vehicle_status_events",Integer.class));
    }
    @Test void partiallyUtilizedActualActivityUsesEligibleDurationNotTruckStatus() {
        var f=fixture();membership(f);capacity(f);var mid=f.from().plusSeconds(1800);event(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),mid);event(f,Kind.ACTIVITY,"IDLE_TEST",mid,f.to());jdbc.update("update trucks set status='UNRELATED_CURRENT_STATUS' where id=?",f.truck());assertEquals(new BigDecimal("50.00"),report(f).utilization().value());
    }
    @Test void plannedUnavailableWindowsDoNotInventTwentyFourHourCapacity() {
        var f=fixture();membership(f);var mid=f.from().plusSeconds(1800);event(f,Kind.CAPACITY,"READY_TEST",f.from(),mid);event(f,Kind.CAPACITY,"PLANNED_UNAVAILABLE_TEST",mid,f.to());event(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),mid);var r=report(f);assertEquals(new BigDecimal("100.00"),r.utilization().value());assertEquals(0,r.utilization().denominator().compareTo(new BigDecimal("1800")));
    }
    @Test void vehicleEntryAndExitUseExplicitMembershipIntervals() {
        var f=fixture();var entry=f.from().plusSeconds(1200);var exit=f.from().plusSeconds(2400);event(f,Kind.MEMBERSHIP,"OUT_TEST",f.from(),entry);event(f,Kind.MEMBERSHIP,"IN_TEST",entry,exit);event(f,Kind.MEMBERSHIP,"OUT_TEST",exit,f.to());event(f,Kind.CAPACITY,"READY_TEST",entry,exit);event(f,Kind.ACTIVITY,"LOADED_TEST",entry,exit);var r=report(f);assertEquals(new BigDecimal("100.00"),r.utilization().value());assertEquals(0,r.coverage().getFirst().membershipSeconds().compareTo(new BigDecimal("1200")));
    }
    @Test void missingHistoryOrInitialStateIsUnavailableWithoutCurrentStatusBackfill() {
        var f=fixture();var r=report(f);assertNull(r.utilization().value());assertTrue(r.utilization().reason().contains("NO_AVAILABILITY_HISTORY"));assertEquals(0,jdbc.queryForObject("select count(*) from vehicle_status_events where truck_id=?",Integer.class,f.truck()));event(f,Kind.MEMBERSHIP,"IN_TEST",f.from().plusSeconds(1200),f.to());assertNull(report(f).utilization().value());
    }
    @Test void openEvidenceCannotExtendBeyondExplicitValidity() {
        var f=fixture();membership(f);capacity(f);event(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),f.from().plusSeconds(1800));assertNull(report(f).utilization().value());assertEquals(0,report(f).coverage().getFirst().gapSeconds().compareTo(new BigDecimal("1800")));
    }
    @Test void duplicateIdentityReplaysAuditAndDifferentPayloadConflicts() {
        var f=fixture();var request=command(f,Kind.MEMBERSHIP,"IN_TEST",f.from(),f.to());var original=fleet.capture(request,f.actor());assertEquals(original,fleet.capture(request,f.actor()));
        var changed=new Capture(request.policyId(),request.truckId(),request.kind(),"OUT_TEST",request.occurredAt(),request.validUntil(),request.source(),request.sourceEventId(),null,null,null,null,request.reasonCode(),request.reason());assertEquals("FLEET_EVENT_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->fleet.capture(changed,f.actor())).getCode());
        assertThrows(DataAccessException.class,()->jdbc.update("delete from vehicle_status_events where id=?",original.id()));
    }
    @Test void sameTimestampContradictionRemainsUnavailableUntilAuditedCorrection() {
        var f=fixture();complete(f);var bad=event(f,Kind.ACTIVITY,"IDLE_TEST",f.from(),f.to());assertNull(report(f).utilization().value());
        var request=command(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),f.to());var fix=new Capture(request.policyId(),request.truckId(),request.kind(),request.status(),request.occurredAt(),request.validUntil(),request.source(),request.sourceEventId(),request.tripId(),request.loadId(),request.executionReference(),bad.id(),"CORRECT_SOURCE","Actual activity correction");fleet.capture(fix,f.actor());assertEquals(new BigDecimal("100.00"),report(f).utilization().value());assertEquals("NON_PRODUCTIVE",history.event(bad.id()).orElseThrow().classification());
    }
    @Test void outOfOrderNonOverlappingEventsAreOrderedByActualOccurrence() {
        var f=fixture();membership(f);capacity(f);var mid=f.from().plusSeconds(1800);event(f,Kind.ACTIVITY,"IDLE_TEST",mid,f.to());event(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),mid);assertEquals(new BigDecimal("50.00"),report(f).utilization().value());
    }
    @Test void unknownStatusAndContradictoryOverlapFailClosedWithoutWinningInsertionOrder() {
        var f=fixture();complete(f);var unknown=event(f,Kind.ACTIVITY,"NEW_UNKNOWN_STATUS",f.from().plusSeconds(300),f.from().plusSeconds(600));assertEquals("UNAVAILABLE",unknown.classification());assertNull(report(f).utilization().value());assertTrue(report(f).coverage().getFirst().conflictSeconds().signum()>0);
    }
    @Test void productiveOutsideCapacityIsConflictNotSilentlyClipped() {
        var f=fixture();membership(f);event(f,Kind.CAPACITY,"PLANNED_UNAVAILABLE_TEST",f.from(),f.to());event(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),f.to());assertNull(report(f).utilization().value());assertTrue(report(f).utilization().reason().contains("SOURCE_CAPACITY_CONFLICT"));
    }
    @Test void zeroEligibleCapacityIsUnavailableRatherThanZeroPercent() {
        var f=fixture();membership(f);event(f,Kind.CAPACITY,"PLANNED_UNAVAILABLE_TEST",f.from(),f.to());assertNull(report(f).utilization().value());assertEquals(MetricAvailability.UNAVAILABLE,report(f).utilization().availability());
    }
    @Test void provenCompletionMileageKeepsOriginalTruckAndDoesNotUseDeadheadComplement() {
        var f=fixture();complete(f);var at=f.from().plusSeconds(1800);completed(f,"80","20","120",at);var request=attribution(f,"80","20","120",at);var m=fleet.attribute(request,f.actor());assertEquals(m,fleet.attribute(request,f.actor()));jdbc.update("update trips set truck_id=null,loaded_miles=1,empty_miles=1,actual_distance_miles=2 where id=?",f.trip());var r=report(f);assertEquals(new BigDecimal("80.00"),r.loadedMilesPercent().value());assertEquals(new BigDecimal("16.67"),r.deadheadPercent().value());assertEquals(new BigDecimal("80.000"),history.mileage(m.id()).orElseThrow().loadedMiles());assertThrows(DataAccessException.class,()->jdbc.update("update fleet_mileage_attributions set loaded_miles=0 where id=?",m.id()));
    }
    @Test void completionAtExclusiveBoundaryCountsOnceInFollowingPeriod() {
        var f=fixture();var at=f.to();completed(f,"80","20","100",at);fleet.attribute(attribution(f,"80","20","100",at),f.actor());assertNull(report(f).loadedMilesPercent().value());var r=fleet.report(f.policy().id(),List.of(f.truck()),f.to(),f.to().plusSeconds(3600),null,null,null,"boundary");assertEquals(new BigDecimal("80.00"),r.loadedMilesPercent().value());
    }
    @Test void missingOrZeroActualMileageCannotBecomePlannedDistanceOrFakeZeroKpi() {
        var f=fixture();var at=f.from().plusSeconds(1800);completed(f,"0","0","0",at);assertNull(report(f).loadedMilesPercent().value());assertEquals("FLEET_MILEAGE_ATTRIBUTION_REQUIRED",report(f).loadedMilesPercent().reason());fleet.attribute(attribution(f,"0","0","0",at),f.actor());assertNull(report(f).loadedMilesPercent().value());
    }
    @Test void concurrentCaptureAndCorrectionHaveSingleIdentityAndNoBranch() throws Exception {
        var f=fixture();var request=command(f,Kind.MEMBERSHIP,"IN_TEST",f.from(),f.to());var start=new CountDownLatch(1);Event original;
        try(var pool=Executors.newFixedThreadPool(2)){Callable<Event> call=()->{start.await();return fleet.capture(request,f.actor());};var a=pool.submit(call);var b=pool.submit(call);start.countDown();original=a.get(30,TimeUnit.SECONDS);assertEquals(original,b.get(30,TimeUnit.SECONDS));}
        var barrier=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){Callable<String> call=()->{barrier.await();var r=command(f,Kind.MEMBERSHIP,"OUT_TEST",f.from(),f.to());var correction=new Capture(r.policyId(),r.truckId(),r.kind(),r.status(),r.occurredAt(),r.validUntil(),r.source(),r.sourceEventId(),null,null,null,original.id(),"CORRECTION","Approved correction");try{fleet.capture(correction,f.actor());return "CREATED";}catch(ApiException e){return e.getCode();}};var a=pool.submit(call);var b=pool.submit(call);barrier.countDown();assertEquals(Set.of("CREATED","FLEET_CORRECTION_CONFLICT"),Set.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)));}
    }
    @Test void sourceActorTenantAndPrecisionGuardsRejectUnqualifiedEvidence() {
        var f=fixture();var r=command(f,Kind.MEMBERSHIP,"IN_TEST",f.from(),f.to());assertThrows(ApiException.class,()->fleet.capture(r,UUID.randomUUID()));
        var foreign=new Capture(r.policyId(),UUID.randomUUID(),r.kind(),r.status(),r.occurredAt(),r.validUntil(),r.source(),r.sourceEventId(),null,null,null,null,r.reasonCode(),r.reason());assertThrows(ApiException.class,()->fleet.capture(foreign,f.actor()));
        var source=new Capture(r.policyId(),r.truckId(),r.kind(),r.status(),r.occurredAt(),r.validUntil(),new Source("UNQUALIFIED","x","v0"),r.sourceEventId(),null,null,null,null,r.reasonCode(),r.reason());assertThrows(ApiException.class,()->fleet.capture(source,f.actor()));
        assertThrows(ApiException.class,()->fleet.attribute(attribution(f,"0.0001","0","1",f.from()),f.actor()));
    }
    @Test void reportApiPreservesExistingRolesAndUnsupportedHealthIsNullUnavailable() throws Exception {
        var f=fixture();complete(f);var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        mvc.perform(get("/api/reports/fleet/utilization-history").param("policyId",f.policy().id().toString()).param("truckIds",f.truck().toString()).param("from",f.from().toString()).param("to",f.to().toString()).with(user(f.email()).roles("DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/fleet/health").param("policyId",f.policy().id().toString()).param("truckIds",f.truck().toString()).param("from",f.from().toString()).param("to",f.to().toString()).with(user(f.email()).roles("ACCOUNTANT"))).andExpect(status().isOk());
        mvc.perform(post("/api/fleet/policies").contentType("application/json").content(json.writeValueAsString(publish("api-test-"+UUID.randomUUID()))).with(user(f.email()).roles("DISPATCHER"))).andExpect(status().isForbidden());
        assertTrue(report(f).health().stream().allMatch(m->m.value()==null && m.availability()==MetricAvailability.UNAVAILABLE));
    }
    @Test void physicalPostgresTenantCannotReadNeighborFleetFactsOrReferenceThem() {
        var f=fixture();complete(f);String database="codex_fleet_isolation_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("create database "+database);
        var ds=new org.postgresql.ds.PGSimpleDataSource();ds.setURL(System.getenv("TASK_DB_URL"));ds.setDatabaseName(database);ds.setUser(System.getenv("TASK_DB_USER"));ds.setPassword(System.getenv("TASK_DB_PASSWORD"));
        var fw=org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration/tenant").load();fw.migrate();assertTrue(fw.validateWithResult().validationSuccessful);var peer=new JdbcTemplate(ds);var repo=new FleetHistoryRepository(peer,json);assertTrue(repo.policy(f.policy().id()).isEmpty());assertFalse(repo.trucksExist(List.of(f.truck())));assertEquals(0,peer.queryForObject("select count(*) from vehicle_status_events",Integer.class));assertThrows(DataAccessException.class,()->peer.update("insert into fleet_policy_versions values (?,?,1,?::jsonb,?::jsonb,'test',?,now())",UUID.randomUUID(),"foreign",json.writeValueAsString(Map.of("membershipStates",Map.of("IN",true),"capacityStates",Map.of("READY",true),"activityStates",Map.of("LOADED","PRODUCTIVE"))),json.writeValueAsString(List.of(SOURCE)),f.actor()));assertEquals(new BigDecimal("100.00"),report(f).utilization().value());
    }
    @Test void mismatchedRealTripAndLoadCannotForgeProductiveExecutionContext() {
        var f=fixture();var other=fixture();var r=command(f,Kind.ACTIVITY,"LOADED_TEST",f.from(),f.to());
        var mismatch=new Capture(r.policyId(),r.truckId(),r.kind(),r.status(),r.occurredAt(),r.validUntil(),r.source(),r.sourceEventId(),f.trip(),other.load(),r.executionReference(),null,r.reasonCode(),r.reason());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ApiException.class,()->fleet.capture(mismatch,f.actor())).getCode());
        assertThrows(DataAccessException.class,()->jdbc.update("""
          insert into vehicle_status_events(id,policy_id,truck_id,kind,status,classification,occurred_at,valid_until,source_type,source_reference,source_version,source_event_id,trip_id,load_id,execution_reference,reason_code,reason,captured_by,captured_at,normalized_input_hash)
          values (?,?,?,'ACTIVITY','LOADED_TEST','PRODUCTIVE',?,?,'AUDITED_TEST_ONLY','actual-test-feed','test-v1',?,?,?,'test','TEST','wrong context',?,now(),?)
          """,UUID.randomUUID(),f.policy().id(),f.truck(),OffsetDateTime.ofInstant(f.from(),ZoneOffset.UTC),OffsetDateTime.ofInstant(f.to(),ZoneOffset.UTC),UUID.randomUUID().toString(),f.trip(),other.load(),f.actor(),"a".repeat(64)));
    }
    @Test void futureActualActivityIsRejectedWhileExplicitFutureCapacityWindowIsAllowed() {
        var f=fixture();var future=Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertThrows(ApiException.class,()->event(f,Kind.ACTIVITY,"LOADED_TEST",future,future.plusSeconds(3600)));
        assertNotNull(event(f,Kind.CAPACITY,"READY_TEST",future,future.plusSeconds(3600)));
    }
}
