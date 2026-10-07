package com.company.logicstic.controller;

import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import com.company.logicstic.dto.load.SetPickupBusinessDateRequest;
import com.company.logicstic.dto.rating.ContractMileageRequest;
import com.company.logicstic.dto.rating.RateRuleRequest;
import com.company.logicstic.dto.rating.RatingAcceptRequest;
import com.company.logicstic.dto.rating.RatingContractRequest;
import com.company.logicstic.service.fleet.FleetHistoryService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ControllerRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void ratingContractRequestFailsWhenRequiredFieldsMissing() {
        RatingContractRequest empty = new RatingContractRequest(null, null, null, null);
        Set<ConstraintViolation<RatingContractRequest>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("currency")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("effectiveFrom")));

        RatingContractRequest valid = new RatingContractRequest(UUID.randomUUID(), "USD", LocalDate.now(), null);
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void rateRuleRequestFailsWhenRequiredFieldsMissing() {
        RateRuleRequest empty = new RateRuleRequest(null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        Set<ConstraintViolation<RateRuleRequest>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("priority")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("currency")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("method")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("baseRate")));
    }

    @Test
    void ratingAcceptRequestFailsWhenRequiredFieldsMissing() {
        RatingAcceptRequest empty = new RatingAcceptRequest(null, null, null, null, null, null, null);
        Set<ConstraintViolation<RatingAcceptRequest>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("idempotencyKey")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("rating")));
    }

    @Test
    void contractMileageRequestFailsWhenRequiredFieldsMissing() {
        ContractMileageRequest empty = new ContractMileageRequest(null, null, null, null, null, null);
        Set<ConstraintViolation<ContractMileageRequest>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("componentType")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("originalValue")));
    }

    @Test
    void taxAssessmentRequestFailsWhenRequiredFieldsMissing() {
        TaxAssessmentRequest empty = new TaxAssessmentRequest(null, null, null, null, null, null,
                null, null, null, null, null, null);
        Set<ConstraintViolation<TaxAssessmentRequest>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("loadId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("jurisdiction")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("taxableBasis")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("taxAmount")));
    }

    @Test
    void setPickupBusinessDateRequestFailsWhenRequiredFieldsMissing() {
        SetPickupBusinessDateRequest empty = new SetPickupBusinessDateRequest(null, null, null);
        Set<ConstraintViolation<SetPickupBusinessDateRequest>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("requestedPickupBusinessDate")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("provenance")));
    }

    @Test
    void fleetCaptureRequestFailsWhenRequiredFieldsMissing() {
        FleetHistoryService.Capture empty = new FleetHistoryService.Capture(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );
        Set<ConstraintViolation<FleetHistoryService.Capture>> violations = validator.validate(empty);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("truckId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("kind")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("occurredAt")));
    }
}
