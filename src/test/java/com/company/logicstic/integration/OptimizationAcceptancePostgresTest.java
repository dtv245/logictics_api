package com.company.logicstic.integration;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.integration.optimization.TrustedOptimizationHttpAdapter;
import com.company.logicstic.repository.*;
import com.company.logicstic.repository.OptimizationAcceptanceRepository.Accepted;
import com.company.logicstic.service.optimization.*;
import com.company.logicstic.service.optimization.OptimizationAcceptanceService.AcceptRequest;
import com.company.logicstic.service.optimization.OptimizationApplicationService.CreateRequest;
import com.company.logicstic.service.optimization.OptimizationQualifiedInputService.*;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
class OptimizationAcceptancePostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;
    @Autowired OptimizationAcceptanceService acceptance;
    @Autowired OptimizationApplicationService optimization;
    @Autowired OptimizationQualifiedInputService sources;
    @Autowired ApplicationContext app;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Autowired ObjectMapper json;
    @MockitoBean TrustedOptimizationHttpAdapter provider;
    OptimizationRunPostgresTest support;
    @BeforeEach void fixtureSupport(){support=new OptimizationRunPostgresTest();app.getAutowireCapableBeanFactory().autowireBean(support);support.provider=provider;support.qualifiedAdapterFixture();}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    record Ready(OptimizationRunPostgresTest.Fixture fixture,Outcome run,Candidate candidate){}
    Ready ready(){var f=support.fixture();var run=optimization.create(support.command(f),f.entities().actor(),"acceptance-test");var candidate=run.candidates().getFirst();assertTrue(candidate.feasible(),candidate.rejectionCodes().toString());return new Ready(f,run,candidate);}
    AcceptRequest request(Candidate c){return new AcceptRequest("accept-"+UUID.randomUUID(),c.inputFingerprint());}
    Accepted accept(Ready r,AcceptRequest body){return acceptance.accept(r.run().run().id(),r.candidate().id(),body,r.fixture().entities().actor());}
    void stale(Ready r){assertEquals("OPTIMIZATION_CANDIDATE_STALE",assertThrows(ApiException.class,()->accept(r,request(r.candidate()))).getCode());assertEquals(0,jdbc.queryForObject("select count(*) from optimization_acceptances where run_id=?",Integer.class,r.run().run().id()));}
    @Test void acceptAtomicallyAssignsExistingTripWithoutDispatchFakeEndOrFinancialSideEffects() {
        var r=ready();long version=jdbc.queryForObject("select version from trips where id=?",Long.class,r.fixture().entities().trip());var accepted=accept(r,request(r.candidate()));assertEquals(version+1,jdbc.queryForObject("select version from trips where id=?",Long.class,r.fixture().entities().trip()));assertEquals(r.candidate().id(),accepted.candidateId());assertEquals(r.fixture().entities().actor(),accepted.acceptedBy());
        assertEquals(r.fixture().entities().truck(),jdbc.queryForObject("select truck_id from trips where id=?",UUID.class,r.fixture().entities().trip()));
        assertEquals("PLANNED",jdbc.queryForObject("select status from trips where id=?",String.class,r.fixture().entities().trip()));assertNull(jdbc.queryForObject("select dispatched_at from trips where id=?",OffsetDateTime.class,r.fixture().entities().trip()));
        assertNull(jdbc.queryForObject("select effective_to from trip_driver_assignments where id=?",OffsetDateTime.class,accepted.driverAssignmentId()));
        assertEquals(accepted.acceptedAt(),jdbc.queryForObject("select effective_from from trip_driver_assignments where id=?",OffsetDateTime.class,accepted.driverAssignmentId()).toInstant());
        assertEquals(r.run(),optimization.get(r.run().run().id()));assertEquals(0,jdbc.queryForObject("select count(*) from invoices where load_id=?",Integer.class,r.fixture().entities().load()));
        assertEquals(1,jdbc.queryForObject("select count(*) from accepted_rating_snapshots where load_id=?",Integer.class,r.fixture().entities().load()));
        assertThrows(DataAccessException.class,()->jdbc.update("update optimization_acceptances set accepted_at=now() where id=?",accepted.id()));
    }
    @Test void retriesAndNewKeyAliasesPreserveOriginalOutcomeAndDifferentPayloadKeyConflicts() {
        var r=ready();var body=request(r.candidate());var original=accept(r,body);clearInvocations(provider);assertEquals(original,accept(r,body));assertEquals(original,accept(r,request(r.candidate())));verifyNoInteractions(provider);
        assertEquals(2,jdbc.queryForObject("select count(*) from optimization_accept_commands where acceptance_id=?",Integer.class,original.id()));
        assertEquals("OPTIMIZATION_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->accept(r,new AcceptRequest(body.idempotencyKey(),"f".repeat(64)))).getCode());
        assertEquals(1,jdbc.queryForObject("select count(*) from trip_driver_assignments where trip_id=?",Integer.class,r.fixture().entities().trip()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from optimization_accept_commands where acceptance_id=?",original.id()));
    }
    @Test void concurrentSameCandidateAcceptHasOneAssignmentAndStableImmutableActorAudit() throws Exception {
        var r=ready();var body=request(r.candidate());var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Accepted> call=()->{start.await();return accept(r,body);};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from optimization_acceptances where run_id=?",Integer.class,r.run().run().id()));
        assertEquals(1,jdbc.queryForObject("select count(*) from trip_driver_assignments where trip_id=?",Integer.class,r.fixture().entities().trip()));
    }
    @Test void twoDifferentRunsCannotAcceptSameLoadTwice() throws Exception {
        var a=ready();var run=optimization.create(support.command(a.fixture()),a.fixture().entities().actor(),"second-run");var b=new Ready(a.fixture(),run,run.candidates().getFirst());var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> first=()->{start.await();try{accept(a,request(a.candidate()));return "ACCEPTED";}catch(ApiException e){return e.getCode();}};
            Callable<String> second=()->{start.await();try{accept(b,request(b.candidate()));return "ACCEPTED";}catch(ApiException e){return e.getCode();}};
            var x=pool.submit(first);var y=pool.submit(second);start.countDown();assertEquals(Set.of("ACCEPTED","OPTIMIZATION_CANDIDATE_STALE"),Set.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS)));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from optimization_acceptances where load_id=?",Integer.class,a.fixture().entities().load()));
    }
    @Test void competingLoadsCannotConcurrentlyClaimSameDriverOrTruck() throws Exception {
        for(boolean shareDriver:List.of(true,false)) {
            var first=ready();var f=support.fixture();Scope scope=new Scope(f.target().loadId(),f.target().tripId(),shareDriver?first.candidate().driverId():f.entities().actor(),shareDriver?f.entities().truck():first.candidate().truckId());
            var selection=support.selection(f.entities(),f.policy(),scope);var request=new CreateRequest("resource-"+UUID.randomUUID(),f.policy().id(),List.of(f.target()),List.of(scope.driverId()),List.of(scope.truckId()),List.of(selection));
            var run=optimization.create(request,f.entities().actor(),"shared-resource");var second=new Ready(f,run,run.candidates().getFirst());assertTrue(second.candidate().feasible(),second.candidate().rejectionCodes().toString());var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                Callable<String> a=()->{start.await();try{accept(first,request(first.candidate()));return "ACCEPTED";}catch(ApiException e){return e.getCode();}};
                Callable<String> b=()->{start.await();try{accept(second,request(second.candidate()));return "ACCEPTED";}catch(ApiException e){return e.getCode();}};
                var x=pool.submit(a);var y=pool.submit(b);start.countDown();assertEquals(Set.of("ACCEPTED","OPTIMIZATION_CANDIDATE_STALE"),Set.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS)));
            }
        }
    }
    @Test void matchingExistingAssignmentIsReusedWithoutChangingItsHistoricalDates() {
        var f=support.fixture();UUID assignment=UUID.randomUUID();Instant original=support.now().minusSeconds(60);
        jdbc.update("insert into trip_driver_assignments(id,trip_id,driver_id,assignment_type,effective_from) values (?,?,?,'PRIMARY',?)",assignment,f.entities().trip(),f.entities().actor(),original.atOffset(ZoneOffset.UTC));
        jdbc.update("update trips set truck_id=? where id=?",f.entities().truck(),f.entities().trip());var run=optimization.create(support.command(f),f.entities().actor(),"existing-assignment");
        var r=new Ready(f,run,run.candidates().getFirst());assertTrue(r.candidate().feasible(),r.candidate().rejectionCodes().toString());var accepted=accept(r,request(r.candidate()));assertEquals(assignment,accepted.driverAssignmentId());
        assertEquals(original,jdbc.queryForObject("select effective_from from trip_driver_assignments where id=?",OffsetDateTime.class,assignment).toInstant());assertEquals(1,jdbc.queryForObject("select count(*) from trip_driver_assignments where trip_id=?",Integer.class,f.entities().trip()));
    }
    @Test void driverTruckHosAndForecastChangesRequireExplicitRerunRatherThanImplicitAccept() {
        var driver=ready();jdbc.update("update employees set status='UNAVAILABLE' where id=?",driver.candidate().driverId());stale(driver);
        var truck=ready();jdbc.update("update trucks set status='MAINTENANCE_BLOCKED' where id=?",truck.candidate().truckId());stale(truck);
        var forecast=ready();var cost=sources.get(forecast.fixture().selection().forecastInputId()).payload().forecast().costs().getFirst();jdbc.update("update shipment_costs set amount=amount+1,version=version+1 where id=?",cost.costId());stale(forecast);
        var hos=ready();doAnswer(invocation->{CandidateContext c=invocation.getArgument(0);return support.input(new Assessment("FULL_TEST_HOS_RULES","rules-v1","APPROVED_TEST_PLAN","plan-v1",true,false,true,true,true,true,new BigDecimal(".20")),"RATIO",Kind.HOS,c);}).when(provider).evaluate(any(),any());stale(hos);
    }
    @Test void sourceCorrectionDuringExternalRevalidationIsCaughtInsideCommitWithoutNetworkInTransaction() {
        var r=ready();var original=sources.get(r.fixture().selection().capacityInputId());
        doAnswer(invocation->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            sources.capture(new CaptureRequest(UUID.randomUUID(),original.policyId(),Kind.CAPACITY,original.scope(),original.source(),original.unit(),support.now(),support.now().plusSeconds(7200),null,original.id(),
                    new CapacityRequest(new WeightRequest(new BigDecimal("1000"),"POUND"),new WeightRequest(new BigDecimal("40000"),"POUND")),null,null,"TEST_APPROVAL","SOURCE_CORRECTION","Current source correction"),r.fixture().entities().actor());
            CandidateContext c=invocation.getArgument(0);return support.input(new Assessment("FULL_TEST_HOS_RULES","rules-v1","APPROVED_TEST_PLAN","plan-v1",true,true,true,true,true,true,new BigDecimal(".20")),"RATIO",Kind.HOS,c);}).when(provider).evaluate(any(),any());
        stale(r);assertNull(jdbc.queryForObject("select truck_id from trips where id=?",UUID.class,r.candidate().tripId()));
    }
    @Test void databaseGuardsStopLegacyWritersFromTakingClaimedResourcesButAllowRealSoftClose() {
        var r=ready();var accepted=accept(r,request(r.candidate()));var other=support.fixture();
        assertThrows(DataAccessException.class,()->jdbc.update("insert into trip_driver_assignments(id,trip_id,driver_id,assignment_type,effective_from) values (?,?,?,'PRIMARY',now())",UUID.randomUUID(),other.entities().trip(),r.candidate().driverId()));
        assertThrows(DataAccessException.class,()->jdbc.update("update trips set truck_id=? where id=?",r.candidate().truckId(),other.entities().trip()));
        assertThrows(DataAccessException.class,()->jdbc.update("update trips set truck_id=null where id=?",r.candidate().tripId()));
        assertThrows(DataAccessException.class,()->jdbc.update("update trip_driver_assignments set effective_from=effective_from-interval '1 minute' where id=?",accepted.driverAssignmentId()));
        jdbc.update("update trip_driver_assignments set effective_to=now() where id=?",accepted.driverAssignmentId());assertNotNull(jdbc.queryForObject("select effective_to from trip_driver_assignments where id=?",OffsetDateTime.class,accepted.driverAssignmentId()));
        assertEquals(accepted,new OptimizationAcceptanceRepository(jdbc,json).run(r.run().run().id()).orElseThrow());
    }
    @Test void issuedRunCannotChangeSelectionAndForeignCandidateOrDriverApiIsRejected() throws Exception {
        var r=ready();var accepted=accept(r,request(r.candidate()));
        assertEquals("OPTIMIZATION_ALREADY_ACCEPTED",assertThrows(ApiException.class,()->acceptance.accept(r.run().run().id(),UUID.randomUUID(),request(r.candidate()),r.fixture().entities().actor())).getCode());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();String path="/api/optimization/runs/"+r.run().run().id()+"/assignments/"+r.candidate().id()+"/accept";
        mvc.perform(post(path).with(user(r.fixture().entities().email()).roles("DRIVER")).contentType("application/json").content(json.writeValueAsString(request(r.candidate())))).andExpect(status().isForbidden());
        var response=mvc.perform(post(path).with(user(r.fixture().entities().email()).roles("DISPATCHER")).contentType("application/json").content(json.writeValueAsString(request(r.candidate())))).andExpect(status().isOk()).andReturn();
        assertEquals(accepted.id().toString(),json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asText());
        assertEquals(404,assertThrows(ApiException.class,()->acceptance.accept(UUID.randomUUID(),r.candidate().id(),request(r.candidate()),r.fixture().entities().actor())).getStatus().value());
    }
    @Test void physicalPostgresTenantCannotReadOrAttachNeighborOptimizationFinancialOrAcceptanceFacts() {
        var r=ready();var accepted=accept(r,request(r.candidate()));String database="codex_optimization_isolation_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("create database "+database);
        var ds=new org.postgresql.ds.PGSimpleDataSource();ds.setURL(System.getenv("TASK_DB_URL"));ds.setDatabaseName(database);ds.setUser(System.getenv("TASK_DB_USER"));ds.setPassword(System.getenv("TASK_DB_PASSWORD"));
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration/tenant").load();flyway.migrate();assertTrue(flyway.validateWithResult().validationSuccessful);var peer=new JdbcTemplate(ds);
        assertTrue(new OptimizationAuditRepository(peer,json).run(r.run().run().id()).isEmpty());assertTrue(new OptimizationAuditRepository(peer,json).candidates(r.run().run().id()).isEmpty());
        assertTrue(new OptimizationAcceptanceRepository(peer,json).run(r.run().run().id()).isEmpty());assertTrue(new OptimizationQualifiedInputRepository(peer,json).find(r.fixture().selection().forecastInputId()).isEmpty());
        assertTrue(new RatingSnapshotRepository(peer,json).find(r.fixture().target().ratingSnapshotId()).isEmpty());
        assertThrows(DataAccessException.class,()->peer.update("insert into optimization_accept_commands(operation,idempotency_key,normalized_input_hash,acceptance_id,recorded_by,recorded_at) values ('OPTIMIZATION_ACCEPT','foreign',?,?,?,now())","a".repeat(64),accepted.id(),accepted.acceptedBy()));
        assertEquals(0,peer.queryForObject("select count(*) from optimization_acceptances",Integer.class));assertEquals(accepted,accept(r,request(r.candidate())));
        // Retain explicitly named disposable tenant database; never drop user data.
    }
}
