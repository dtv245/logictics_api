package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.optimization.OptimizationApplicationService.CreateRequest;
import com.company.logicstic.service.optimization.domain.OptimizationAudit.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationScopeTest {
    final UUID policy=UUID.randomUUID();
    final Target target=new Target(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    final UUID driver=UUID.randomUUID(),truck=UUID.randomUUID();
    CreateRequest request(){return new CreateRequest("explicit-key",policy,List.of(target),List.of(driver),List.of(truck),List.of());}
    RunRequest normalize(CreateRequest r){return OptimizationApplicationService.normalize(r,"EXPLICIT_TEST_TENANT");}
    @Test void explicitScopeHasNoStatusRateResourceOrTenantDefault() {
        var normalized=normalize(request());assertEquals(List.of(driver),normalized.driverIds());assertEquals(List.of(truck),normalized.truckIds());
        assertEquals(policy,normalized.policyId());assertEquals("EXPLICIT_TEST_TENANT",normalized.tenantScope());assertTrue(normalized.sourceSelections().isEmpty());
        assertThrows(ApiException.class,()->normalize(new CreateRequest("key",null,List.of(target),List.of(driver),List.of(truck),List.of())));
    }
    @Test void existingTripAndAuthoritativePickupStopAndAcceptedRatingAreRequired() {
        for(Target invalid:List.of(new Target(target.loadId(),null,target.ratingSnapshotId(),target.pickupStopId()),
                new Target(target.loadId(),target.tripId(),null,target.pickupStopId()),new Target(target.loadId(),target.tripId(),target.ratingSnapshotId())))
            assertEquals("INVALID_OPTIMIZATION_SCOPE",assertThrows(ApiException.class,()->normalize(new CreateRequest("key",policy,List.of(invalid),List.of(driver),List.of(truck),List.of()))).getCode());
    }
    @Test void generationIsExactDeterministicCartesianSetWithoutDemoCandidates() {
        UUID secondDriver=UUID.randomUUID(),secondTruck=UUID.randomUUID();var r=new CreateRequest("key",policy,List.of(target),List.of(driver,secondDriver),List.of(truck,secondTruck),List.of());
        var generated=OptimizationApplicationService.scope(normalize(r));assertEquals(4,generated.size());
        assertEquals(Set.of(new Scope(target.loadId(),target.tripId(),driver,truck),new Scope(target.loadId(),target.tripId(),driver,secondTruck),
                new Scope(target.loadId(),target.tripId(),secondDriver,truck),new Scope(target.loadId(),target.tripId(),secondDriver,secondTruck)),generated.stream().map(c->c.scope()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(generated,OptimizationApplicationService.scope(normalize(r)));
    }
    @Test void repeatedResourcesOrTargetContextsCannotDuplicateCandidateIdentity() {
        for(CreateRequest r:List.of(new CreateRequest("key",policy,List.of(target),List.of(driver,driver),List.of(truck),List.of()),
                new CreateRequest("key",policy,List.of(target),List.of(driver),List.of(truck,truck),List.of()),
                new CreateRequest("key",policy,List.of(target,target),List.of(driver),List.of(truck),List.of())))assertThrows(ApiException.class,()->normalize(r));
    }
    @Test void oversizedExplicitScopeFailsInsteadOfSilentlySamplingOrTruncating() {
        var drivers=new ArrayList<UUID>();for(int i=0;i<201;i++)drivers.add(UUID.randomUUID());
        assertEquals("OPTIMIZATION_SCOPE_TOO_LARGE",assertThrows(ApiException.class,()->normalize(new CreateRequest("key",policy,List.of(target),drivers,List.of(truck),List.of()))).getCode());
    }
    @Test void sourceSelectionCannotBindForeignCandidateOrOmitEvidenceIdentity() {
        var foreign=new SourceSelection(new Scope(target.loadId(),target.tripId(),UUID.randomUUID(),truck),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        assertThrows(ApiException.class,()->normalize(new CreateRequest("key",policy,List.of(target),List.of(driver),List.of(truck),List.of(foreign))));
        var missing=new SourceSelection(new Scope(target.loadId(),target.tripId(),driver,truck),null,UUID.randomUUID(),UUID.randomUUID());
        assertThrows(ApiException.class,()->normalize(new CreateRequest("key",policy,List.of(target),List.of(driver),List.of(truck),List.of(missing))));
    }
    @Test void candidateCannotSilentlyChooseBetweenTwoSourceSelections() {
        var source=new SourceSelection(new Scope(target.loadId(),target.tripId(),driver,truck),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        assertThrows(ApiException.class,()->normalize(new CreateRequest("key",policy,List.of(target),List.of(driver),List.of(truck),List.of(source,source))));
    }
    @Test void reorderingSetsNormalizesIdenticallyButDoesNotCreateRankOrWinner() {
        UUID otherDriver=UUID.randomUUID(),otherTruck=UUID.randomUUID();var a=new CreateRequest("key",policy,List.of(target),List.of(driver,otherDriver),List.of(truck,otherTruck),List.of());
        var b=new CreateRequest("key",policy,List.of(target),List.of(otherDriver,driver),List.of(otherTruck,truck),List.of());assertEquals(normalize(a),normalize(b));
        assertEquals(4,OptimizationApplicationService.scope(normalize(a)).size());
    }
}
