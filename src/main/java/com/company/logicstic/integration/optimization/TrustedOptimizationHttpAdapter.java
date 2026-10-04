package com.company.logicstic.integration.optimization;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.integration.hos.HosFeasibilityService;
import com.company.logicstic.service.optimization.OptimizationEvidenceValidator;
import com.company.logicstic.service.optimization.OptimizationTenantScope;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Configured authenticated aggregator/full-HOS adapter, never a locally invented route or legal engine. */
@Component
public class TrustedOptimizationHttpAdapter implements OptimizationInputProvider, HosFeasibilityService {
    private final ObjectMapper json;
    private final OptimizationTenantScope tenants;
    private final OptimizationEvidenceValidator units;
    private final String dynamicEndpoint, dynamicToken, hosEndpoint, hosToken;
    private final HttpClient http;
    private final boolean testOnlyLoopbackHttp;
    public record DynamicRequest(String tenantScope, CandidateContext context, RoutingContext routing) {}
    public record DynamicResponse(String tenantScope, CandidateContext context, DynamicEvidence evidence) {}
    public record HosRequest(String tenantScope, CandidateContext context, Input<Route> simulatedRoute) {}
    public record HosResponse(String tenantScope, CandidateContext context, Input<Assessment> assessment) {}
    @Autowired
    public TrustedOptimizationHttpAdapter(ObjectMapper json, OptimizationTenantScope tenants, OptimizationEvidenceValidator units,
                                         @Value("${app.optimization.providers.dynamic.endpoint:}") String dynamicEndpoint,
                                         @Value("${app.optimization.providers.dynamic.token:}") String dynamicToken,
                                         @Value("${app.optimization.providers.hos.endpoint:}") String hosEndpoint,
                                         @Value("${app.optimization.providers.hos.token:}") String hosToken) {
        this(json,tenants,units,dynamicEndpoint,dynamicToken,hosEndpoint,hosToken,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),false);
    }
    TrustedOptimizationHttpAdapter(ObjectMapper json, OptimizationTenantScope tenants, OptimizationEvidenceValidator units,
                                  String dynamicEndpoint,String dynamicToken,String hosEndpoint,String hosToken,HttpClient http,boolean testOnlyLoopbackHttp) {
        this.json=json; this.tenants=tenants; this.units=units; this.dynamicEndpoint=dynamicEndpoint; this.dynamicToken=dynamicToken;
        this.hosEndpoint=hosEndpoint; this.hosToken=hosToken; this.http=http; this.testOnlyLoopbackHttp=testOnlyLoopbackHttp;
    }
    @Override public DynamicEvidence fetch(CandidateContext context, RoutingContext route) {
        String tenant = tenants.require();
        if(context==null || route==null || route.origin()==null || route.destination()==null || route.pickupStopId()==null
                || route.appointmentStart()==null) throw invalid("OPTIMIZATION_ROUTING_CONTEXT_REQUIRED");
        DynamicResponse response = post(dynamicEndpoint,dynamicToken,new DynamicRequest(tenant,context,route),DynamicResponse.class);
        if(!tenant.equals(response.tenantScope()) || !context.equals(response.context()) || response.evidence()==null) throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID");
        DynamicEvidence e=response.evidence();
        bound(e.location(),context); bound(e.driverAvailability(),context); bound(e.truckAvailability(),context); bound(e.route(),context);
        Route r=e.route().value();
        if(!Objects.equals(route.appointmentStart(),r.appointmentStart()) || r.deadhead()==null || r.loadAttributedLoadedMiles()==null)
            throw invalid("OPTIMIZATION_ROUTE_EVIDENCE_INVALID");
        // Canonical conversion at the adapter boundary retains the original values/units for explainability.
        Route normalized=new Route(distance(r.deadhead()),distance(r.loadAttributedLoadedMiles()),r.appointmentStart(),r.predictedArrivalAtPickup(),
                r.pickupReachable(),r.simulatedRoutePlanReference(),r.simulatedRoutePlanVersion());
        return new DynamicEvidence(e.location(),e.driverAvailability(),e.truckAvailability(),new Input<>(normalized,e.route().provenance()));
    }
    @Override public Input<Assessment> evaluate(CandidateContext context, Input<Route> route) {
        String tenant=tenants.require();
        if(context==null || route==null || route.value()==null || route.provenance()==null || !context.equals(route.provenance().context()))
            throw invalid("HOS_EVIDENCE_CONTEXT_MISMATCH");
        HosResponse response=post(hosEndpoint,hosToken,new HosRequest(tenant,context,route),HosResponse.class);
        if(!tenant.equals(response.tenantScope()) || !context.equals(response.context())) throw invalid("HOS_EVIDENCE_CONTEXT_MISMATCH");
        bound(response.assessment(),context);
        Assessment h=response.assessment().value();
        if(!Objects.equals(route.value().simulatedRoutePlanReference(),h.simulatedRoutePlanReference())
                || !Objects.equals(route.value().simulatedRoutePlanVersion(),h.simulatedRoutePlanVersion())) throw invalid("HOS_EVIDENCE_CONTEXT_MISMATCH");
        return response.assessment();
    }
    private Distance distance(Distance input) {
        Distance normalized=units.distance(input.originalValue(),input.originalUnit());
        if(input.normalizedMiles()!=null && input.normalizedMiles().compareTo(normalized.normalizedMiles())!=0) throw invalid("OPTIMIZATION_ROUTE_EVIDENCE_INVALID");
        return normalized;
    }
    private static void bound(Input<?> input, CandidateContext context) {
        if(input==null || input.value()==null || input.provenance()==null || !context.equals(input.provenance().context())
                || input.provenance().source()==null || input.provenance().source().classification()!=SourceClass.TRUSTED_ADAPTER)
            throw invalid("OPTIMIZATION_INPUT_EVIDENCE_INVALID");
    }
    private <T> T post(String endpoint, String token, Object request, Class<T> responseType) {
        // Never include URI, response body, transport error/cause or token in the public failure/log.
        if(endpoint==null || endpoint.isBlank() || token==null || token.isBlank() || token.indexOf('\r')>=0 || token.indexOf('\n')>=0) throw unavailable();
        try {
            URI uri=URI.create(endpoint);
            boolean https="https".equalsIgnoreCase(uri.getScheme());
            boolean fixture=testOnlyLoopbackHttp && "http".equalsIgnoreCase(uri.getScheme()) && "127.0.0.1".equals(uri.getHost());
            if((!https && !fixture) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getRawQuery()!=null) throw unavailable();
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token)
                    .header("Content-Type","application/json").header("Accept","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200 || response.body()==null || response.body().length()>1_000_000) throw unavailable();
            T result=json.readValue(response.body(),responseType); if(result==null) throw unavailable(); return result;
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch(Exception e) { throw unavailable(); }
    }
    private static BadRequestException unavailable() {return new BadRequestException("OPTIMIZATION_SOURCE_UNAVAILABLE","Configured qualified optimization source is unavailable or returned invalid data");}
    private static BadRequestException invalid(String code) {return new BadRequestException(code,"Qualified provider evidence must match tenant, candidate and authoritative route context");}
}
