package com.company.logicstic.integration.hos;

import com.company.logicstic.service.optimization.domain.OptimizationEvidence.CandidateContext;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.Input;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.Route;
import java.math.BigDecimal;

/** Port to a qualified full HOS subsystem. No legal rules or remaining-hours shortcut live in the optimizer. */
public interface HosFeasibilityService {
    Input<Assessment> evaluate(CandidateContext context, Input<Route> route);

    record Assessment(String ruleSetCode, String ruleSetVersion, String simulatedRoutePlanReference,
                      String simulatedRoutePlanVersion, boolean driveFeasible, boolean dutyFeasible,
                      boolean breakFeasible, boolean cycleFeasible, boolean serviceFeasible,
                      boolean nextAvailableFeasible, BigDecimal minimumHosHeadroomRatio) {}
}
