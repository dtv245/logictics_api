package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OptimizationEvidenceValidator {
    public List<String> validate(Input<?> input, Kind kind, CandidateContext context, Policy policy, Instant evaluatedAt) {
        var errors = new ArrayList<String>();
        if (input == null || input.value() == null || input.provenance() == null) return List.of(missing(kind));
        Provenance proof = input.provenance();
        if (policy == null || evaluatedAt == null || context == null || !context.equals(proof.context())
                || proof.source() == null || !policy.qualifiedSources().get(kind).contains(proof.source())
                || blank(proof.evidenceReference()) || blank(proof.evidenceVersion()) || blank(proof.unit())) {
            errors.add("OPTIMIZATION_INPUT_EVIDENCE_INVALID");
        }
        if (proof.observedAt() == null || evaluatedAt == null || proof.observedAt().isAfter(evaluatedAt)) errors.add(stale(kind));
        else {
            Long policyCap = switch (kind) {
                case VEHICLE_LOCATION, HOS -> 300L;
                case DRIVER_AVAILABILITY -> proof.source() != null && proof.source().classification() == SourceClass.TRUSTED_ADAPTER ? 300L : null;
                case ROUTE -> 900L;
                default -> null;
            };
            if (proof.expiresAt() == null && proof.maxAgeSeconds() == null) errors.add("OPTIMIZATION_INPUT_VALIDITY_REQUIRED");
            if (proof.expiresAt() != null && (!proof.expiresAt().isAfter(proof.observedAt())
                    || !evaluatedAt.isBefore(proof.expiresAt()))) errors.add(stale(kind));
            if (proof.maxAgeSeconds() != null && (proof.maxAgeSeconds() <= 0
                    || Duration.between(proof.observedAt(), evaluatedAt).compareTo(Duration.ofSeconds(proof.maxAgeSeconds())) > 0)) {
                errors.add(stale(kind));
            }
            if (policyCap != null && Duration.between(proof.observedAt(), evaluatedAt).compareTo(Duration.ofSeconds(policyCap)) > 0) {
                errors.add(stale(kind));
            }
        }
        return errors.stream().distinct().toList();
    }

    public Distance distance(BigDecimal originalValue, String originalUnit) {
        nonnegative(originalValue, "RATE_MILEAGE_UNAVAILABLE");
        BigDecimal miles = switch (originalUnit == null ? "" : originalUnit) {
            case "MILE" -> originalValue;
            case "KILOMETER" -> originalValue.divide(new BigDecimal("1.609344"), MathContext.DECIMAL128);
            default -> throw fail("RATE_MILEAGE_UNAVAILABLE", "Explicit supported distance unit required");
        };
        return new Distance(originalValue, originalUnit, miles);
    }

    public Weight weight(BigDecimal originalValue, String originalUnit) {
        nonnegative(originalValue, "CAPACITY_EVIDENCE_UNAVAILABLE");
        BigDecimal pounds = switch (originalUnit == null ? "" : originalUnit) {
            case "POUND" -> originalValue;
            case "KILOGRAM" -> originalValue.divide(new BigDecimal("0.45359237"), MathContext.DECIMAL128);
            default -> throw fail("CAPACITY_EVIDENCE_UNAVAILABLE", "Explicit supported weight unit required");
        };
        return new Weight(originalValue, originalUnit, pounds);
    }

    public boolean valid(Distance distance) {
        try { return distance != null && distance(distance.originalValue(), distance.originalUnit()).normalizedMiles()
                .compareTo(distance.normalizedMiles()) == 0; }
        catch (BadRequestException | NullPointerException ex) { return false; }
    }
    public boolean valid(Weight weight) {
        try { return weight != null && weight(weight.originalValue(), weight.originalUnit()).normalizedPounds()
                .compareTo(weight.normalizedPounds()) == 0; }
        catch (BadRequestException | NullPointerException ex) { return false; }
    }
    private static String missing(Kind kind) {
        return switch (kind) {
            case CAPACITY -> "CAPACITY_EVIDENCE_UNAVAILABLE";
            case HOS -> "HOS_UTILITY_EVIDENCE_REQUIRED";
            case VEHICLE_LOCATION -> "VEHICLE_LOCATION_STALE";
            case ROUTE -> "ON_TIME_UTILITY_UNAVAILABLE";
            case FORECAST_COST -> "FORECAST_COST_INCOMPLETE";
            default -> "OPTIMIZATION_INPUT_EVIDENCE_REQUIRED";
        };
    }
    private static String stale(Kind kind) {
        return switch (kind) {
            case VEHICLE_LOCATION -> "VEHICLE_LOCATION_STALE";
            case ROUTE -> "ETA_FORECAST_STALE";
            default -> "OPTIMIZATION_INPUT_STALE";
        };
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static void nonnegative(BigDecimal value, String code) {
        if (value == null || value.signum() < 0) throw fail(code, "Nonnegative qualified value required");
    }
    private static BadRequestException fail(String code, String message) { return new BadRequestException(code, message); }
}
