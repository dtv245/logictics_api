package com.company.logicstic.integration.optimization;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.integration.optimization.OptimizationInputProvider.*;
import com.company.logicstic.integration.optimization.TrustedOptimizationHttpAdapter.*;
import com.company.logicstic.service.optimization.OptimizationEvidenceValidator;
import com.company.logicstic.service.optimization.OptimizationTenantScope;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Local deterministic transport fixture, never an external live ELD/routing dependency. */
class TrustedOptimizationHttpAdapterTest {
    final ObjectMapper json=new JacksonJsonHttpMessageConverter().getMapper();
    final OptimizationEvidenceValidator validator=new OptimizationEvidenceValidator();
    final Instant at=Instant.parse("2026-10-05T00:00:00Z");
    final CandidateContext context=new CandidateContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),at,at.plusSeconds(72*3600));
    final Source source=new Source("TRUSTED_TEST_SOURCE","test-registration","version-1",SourceClass.TRUSTED_ADAPTER);
    final RoutingContext routing=new RoutingContext(new Location(d("10"),d("106")),new Location(d("21"),d("105")),UUID.randomUUID(),at.plusSeconds(7200),at.plusSeconds(10800));
    final AtomicReference<Object> body=new AtomicReference<>();
    final AtomicReference<String> received=new AtomicReference<>();
    final AtomicReference<String> authorization=new AtomicReference<>();
    final AtomicReference<Integer> responseStatus=new AtomicReference<>(200);
    HttpServer server;
    String endpoint;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/approved-source",exchange->{
            received.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=(body.get() instanceof String s?s:json.writeValueAsString(body.get())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(responseStatus.get(),bytes.length);
            try(var stream=exchange.getResponseBody()){stream.write(bytes);}
        });
        server.start();endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/approved-source";
    }
    @AfterEach void stop(){server.stop(0);TenantContext.clear();}
    static BigDecimal d(String s){return new BigDecimal(s);}
    <T> Input<T> input(T value,String unit){return new Input<>(value,new Provenance(source,context,"source-evidence","evidence-v1",unit,at,at.plusSeconds(7200),null));}
    Route route(){return new Route(new Distance(d("16.09344"),"KILOMETER",null),new Distance(d("100"),"MILE",null),routing.appointmentStart(),at.plusSeconds(3600),true,"trusted-simulation","plan-v1");}
    DynamicEvidence evidence(){return new DynamicEvidence(input(new Location(d("10"),d("106")),"WGS84"),
            input(new Availability(true,at,context.planningEnd()),"INTERVAL"),input(new Availability(true,at,context.planningEnd()),"INTERVAL"),input(route(),"MILE"));}
    TrustedOptimizationHttpAdapter adapter(){return new TrustedOptimizationHttpAdapter(json,new OptimizationTenantScope(false,"TEST_TENANT"),validator,
            endpoint,"FIXTURE_TOKEN_NOT_PRODUCTION",endpoint,"FIXTURE_HOS_TOKEN_NOT_PRODUCTION",HttpClient.newHttpClient(),true);}
    Assessment assessment(){return new Assessment("QUALIFIED_HOS_RULES","rules-v1","trusted-simulation","plan-v1",true,true,true,true,true,true,d(".20"));}
    void dynamic(){body.set(new DynamicResponse("TEST_TENANT",context,evidence()));}
    @Test void dynamicRequestIsAuthenticatedTenantBoundAndKmIsExplicitlyConverted() throws Exception {
        dynamic();var e=adapter().fetch(context,routing);
        assertEquals("Bearer FIXTURE_TOKEN_NOT_PRODUCTION",authorization.get());var request=json.readValue(received.get(),DynamicRequest.class);
        assertEquals("TEST_TENANT",request.tenantScope());assertEquals(context,request.context());assertEquals(routing,request.routing());
        assertEquals("KILOMETER",e.route().value().deadhead().originalUnit());assertEquals(0,d("10").compareTo(e.route().value().deadhead().normalizedMiles()));
        assertEquals(0,d("100").compareTo(e.route().value().loadAttributedLoadedMiles().normalizedMiles()));
        assertEquals(at,e.location().provenance().observedAt());assertEquals("version-1",e.location().provenance().source().version());
    }
    @Test void anotherTenantCannotSupplyEvidenceEvenForSameUuidContext() {
        body.set(new DynamicResponse("OTHER_TENANT",context,evidence()));
        assertEquals("OPTIMIZATION_INPUT_EVIDENCE_INVALID",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
    }
    @Test void anotherCandidateOrWrongInputProvenanceIsRejected() {
        var other=new CandidateContext(UUID.randomUUID(),context.tripId(),context.driverId(),context.truckId(),context.planningStart(),context.planningEnd());
        body.set(new DynamicResponse("TEST_TENANT",other,evidence()));assertThrows(ApiException.class,()->adapter().fetch(context,routing));
        var e=evidence();var proof=e.location().provenance();var wrong=new Input<>(e.location().value(),new Provenance(source,other,proof.evidenceReference(),proof.evidenceVersion(),proof.unit(),at,proof.expiresAt(),null));
        body.set(new DynamicResponse("TEST_TENANT",context,new DynamicEvidence(wrong,e.driverAvailability(),e.truckAvailability(),e.route())));
        assertEquals("OPTIMIZATION_INPUT_EVIDENCE_INVALID",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
    }
    @Test void providerCannotChangeAuthoritativePickupAppointmentOrFalsifyNormalizedDistance() {
        var e=evidence();var r=route();var wrong=new Route(r.deadhead(),r.loadAttributedLoadedMiles(),routing.appointmentStart().plusSeconds(1),r.predictedArrivalAtPickup(),true,r.simulatedRoutePlanReference(),r.simulatedRoutePlanVersion());
        body.set(new DynamicResponse("TEST_TENANT",context,new DynamicEvidence(e.location(),e.driverAvailability(),e.truckAvailability(),input(wrong,"MILE"))));
        assertEquals("OPTIMIZATION_ROUTE_EVIDENCE_INVALID",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
        wrong=new Route(new Distance(d("16.09344"),"KILOMETER",d("1")),r.loadAttributedLoadedMiles(),r.appointmentStart(),r.predictedArrivalAtPickup(),true,r.simulatedRoutePlanReference(),r.simulatedRoutePlanVersion());
        body.set(new DynamicResponse("TEST_TENANT",context,new DynamicEvidence(e.location(),e.driverAvailability(),e.truckAvailability(),input(wrong,"MILE"))));
        assertEquals("OPTIMIZATION_ROUTE_EVIDENCE_INVALID",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
    }
    @Test void networkAdapterCannotAssertAuthoritativeDbQualificationOrAvailability() {
        var e=evidence();var p=e.driverAvailability().provenance();var db=new Source(source.type(),source.reference(),source.version(),SourceClass.AUTHORITATIVE_DB);
        var forged=new Input<>(e.driverAvailability().value(),new Provenance(db,context,p.evidenceReference(),p.evidenceVersion(),p.unit(),at,p.expiresAt(),null));
        body.set(new DynamicResponse("TEST_TENANT",context,new DynamicEvidence(e.location(),forged,e.truckAvailability(),e.route())));
        assertEquals("OPTIMIZATION_INPUT_EVIDENCE_INVALID",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
    }
    @Test void malformedNonSuccessOrEmptyResponseNeverLeaksProviderSecrets() {
        for(Object response:List.of("SECRET_RESPONSE_INVALID_JSON","null")) {
            body.set(response);var failure=assertThrows(ApiException.class,()->adapter().fetch(context,routing));
            assertEquals("OPTIMIZATION_SOURCE_UNAVAILABLE",failure.getCode());assertFalse(failure.getMessage().contains("SECRET"));assertNull(failure.getCause());
        }
        dynamic();responseStatus.set(503);assertEquals("OPTIMIZATION_SOURCE_UNAVAILABLE",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
    }
    @Test void unconfiguredProvidersHaveNoDefaultUrlTokenOrSuccessfulFallback() {
        var missing=new TrustedOptimizationHttpAdapter(json,new OptimizationTenantScope(false,"TEST_TENANT"),validator,"","","","",HttpClient.newHttpClient(),true);
        assertEquals("OPTIMIZATION_SOURCE_UNAVAILABLE",assertThrows(ApiException.class,()->missing.fetch(context,routing)).getCode());assertNull(received.get());
        assertEquals("OPTIMIZATION_SOURCE_UNAVAILABLE",assertThrows(ApiException.class,()->missing.evaluate(context,input(route(),"MILE"))).getCode());
    }
    @Test void productionTransportRequiresHttpsAndNeverFollowsRedirectToAnotherSource() {
        dynamic();var production=new TrustedOptimizationHttpAdapter(json,new OptimizationTenantScope(false,"TEST_TENANT"),validator,endpoint,"token",endpoint,"token");
        assertEquals("OPTIMIZATION_SOURCE_UNAVAILABLE",assertThrows(ApiException.class,()->production.fetch(context,routing)).getCode());assertNull(received.get());
        responseStatus.set(302);assertEquals("OPTIMIZATION_SOURCE_UNAVAILABLE",assertThrows(ApiException.class,()->adapter().fetch(context,routing)).getCode());
    }
    @Test void hosAdapterPreservesFullSimulationChecksAndHeadroomNotRemainingDriveShortcut() throws Exception {
        var result=input(assessment(),"RATIO");body.set(new HosResponse("TEST_TENANT",context,result));
        assertEquals(result,adapter().evaluate(context,input(route(),"MILE")));assertEquals("Bearer FIXTURE_HOS_TOKEN_NOT_PRODUCTION",authorization.get());
        var request=json.readValue(received.get(),HosRequest.class);assertEquals("TEST_TENANT",request.tenantScope());assertEquals("trusted-simulation",request.simulatedRoute().value().simulatedRoutePlanReference());
        var failed=new Assessment("QUALIFIED_HOS_RULES","rules-v1","trusted-simulation","plan-v1",true,false,false,false,false,false,d(".20"));
        body.set(new HosResponse("TEST_TENANT",context,input(failed,"RATIO")));assertEquals(failed,adapter().evaluate(context,input(route(),"MILE")).value());
    }
    @Test void hosRequiresExactTenantCandidateAndSimulatedPlan() {
        body.set(new HosResponse("OTHER_TENANT",context,input(assessment(),"RATIO")));
        assertEquals("HOS_EVIDENCE_CONTEXT_MISMATCH",assertThrows(ApiException.class,()->adapter().evaluate(context,input(route(),"MILE"))).getCode());
        var wrong=new Assessment("RULES","v1","different-plan","plan-v1",true,true,true,true,true,true,d(".20"));
        body.set(new HosResponse("TEST_TENANT",context,input(wrong,"RATIO")));
        assertEquals("HOS_EVIDENCE_CONTEXT_MISMATCH",assertThrows(ApiException.class,()->adapter().evaluate(context,input(route(),"MILE"))).getCode());
    }
    @Test void endpointFreshnessCapsStillRejectOldProviderProofWithLaterExpiry() {
        dynamic();var e=adapter().fetch(context,routing);var sources=new EnumMap<Kind,Set<Source>>(Kind.class);for(Kind k:Kind.values())sources.put(k,Set.of(k==Kind.QUALIFICATION?new Source("DB","ref","v1",SourceClass.AUTHORITATIVE_DB):source));
        var statuses=new EnumMap<EntityKind,Set<String>>(EntityKind.class);for(EntityKind k:EntityKind.values())statuses.put(k,Set.of("EXPLICIT_TEST_ONLY"));
        var policy=new Policy("OPT_ELIGIBILITY_V1",1,72,"OPT_SOURCE_V1",1,statuses,sources);
        assertTrue(validator.validate(e.location(),Kind.VEHICLE_LOCATION,context,policy,at.plusSeconds(301)).contains("VEHICLE_LOCATION_STALE"));
        assertTrue(validator.validate(e.route(),Kind.ROUTE,context,policy,at.plusSeconds(901)).contains("ETA_FORECAST_STALE"));
    }
    @Test void tenantScopeNeverFallsBackToUnboundRegistryOrImplicitSingleTenant() {
        assertEquals("OPTIMIZATION_TENANT_SCOPE_REQUIRED",assertThrows(ApiException.class,()->new OptimizationTenantScope(true,"SHOULD_NOT_FALLBACK").require()).getCode());
        assertThrows(ApiException.class,()->new OptimizationTenantScope(false,"").require());
        TenantContext.setTenantId("BOUND_TENANT");assertEquals("BOUND_TENANT",new OptimizationTenantScope(true,"OTHER_TENANT").require());
        assertEquals("EXPLICIT_SINGLE_TENANT",new OptimizationTenantScope(false,"EXPLICIT_SINGLE_TENANT").require());
    }
}
