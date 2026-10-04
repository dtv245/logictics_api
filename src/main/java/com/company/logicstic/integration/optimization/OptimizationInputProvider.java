package com.company.logicstic.integration.optimization;

import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.time.Instant;
import java.util.UUID;

/** Port to approved routing/ELD/availability forecasts; DB qualification/costs are resolved separately. */
public interface OptimizationInputProvider {
    DynamicEvidence fetch(CandidateContext context, RoutingContext route);
    record RoutingContext(Location origin, Location destination, UUID pickupStopId,
                          Instant appointmentStart, Instant appointmentEnd) {}
    record DynamicEvidence(Input<Location> location, Input<Availability> driverAvailability,
                           Input<Availability> truckAvailability, Input<Route> route) {}
}
