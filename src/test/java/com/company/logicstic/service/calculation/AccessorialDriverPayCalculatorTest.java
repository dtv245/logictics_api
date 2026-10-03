package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.payroll.AccessorialDriverPayCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessorialDriverPayCalculatorTest {
    final TripDriverAssignmentRepository assignments = mock(TripDriverAssignmentRepository.class);
    final AccessorialDriverPayCalculator calculator = new AccessorialDriverPayCalculator(assignments,mock(TripStopRepository.class),TestRoundingPolicies.standard());
    final LocalDate from = LocalDate.of(2026,1,1), to = from.plusDays(30);
    AccessorialCharge charge() {
        var c = new AccessorialCharge(); c.setId(UUID.randomUUID()); c.setStatus("APPROVED"); c.setCurrency("USD");
        c.setOccurredAt(OffsetDateTime.parse("2026-01-15T12:00:00Z")); c.setDriverPayAmount(new BigDecimal("25"));
        c.setCustomerAmount(new BigDecimal("999")); var trip = new Trip(); trip.setId(UUID.randomUUID()); c.setTrip(trip); return c;
    }
    TripDriverAssignment assignment(AccessorialCharge c, UUID driver) {
        var a = new TripDriverAssignment(); a.setId(UUID.randomUUID()); a.setTrip(c.getTrip()); var d = new Employee(); d.setId(driver); a.setDriver(d);
        a.setEffectiveFrom(c.getOccurredAt().minusHours(1)); a.setEffectiveTo(c.getOccurredAt().plusHours(1)); return a;
    }
    @Test void approvedAmountUsesProvenHistoricalRecipientNotCustomerAmount() {
        var c = charge(); var driver = UUID.randomUUID(); var a = assignment(c,driver);
        when(assignments.findByTripIdOrderByEffectiveFromDesc(c.getTrip().getId())).thenReturn(List.of(a));
        var r = calculator.calculate(c,driver,from,to,"USD").orElseThrow(); assertEquals(new BigDecimal("25.00"),r.amount());
        assertEquals(c.getId(),r.chargeId()); assertEquals(List.of(a.getId()),r.assignmentIds());
        assertTrue(calculator.calculate(c,UUID.randomUUID(),from,to,"USD").isEmpty());
    }
    @Test void nonApprovedZeroAndOutsidePeriodNeverUseCustomerAmount() {
        var c = charge(); var driver = UUID.randomUUID(); c.setStatus("DRAFT");
        assertTrue(calculator.calculate(c,driver,from,to,"USD").isEmpty());
        c.setStatus("APPROVED"); c.setDriverPayAmount(BigDecimal.ZERO); assertTrue(calculator.calculate(c,driver,from,to,"USD").isEmpty());
        c.setDriverPayAmount(BigDecimal.TEN); c.setOccurredAt(c.getOccurredAt().plusMonths(1));
        assertTrue(calculator.calculate(c,driver,from,to,"USD").isEmpty());
    }
    @Test void missingOrAmbiguousOccurrenceRecipientFailsClosed() {
        var c = charge(); var driver = UUID.randomUUID();
        assertThrows(BadRequestException.class, () -> calculator.calculate(c,driver,from,to,"USD"));
        when(assignments.findByTripIdOrderByEffectiveFromDesc(c.getTrip().getId())).thenReturn(List.of(assignment(c,driver),assignment(c,UUID.randomUUID())));
        assertEquals("ACCESSORIAL_PAY_VALIDATION_REQUIRED",assertThrows(BadRequestException.class, () -> calculator.calculate(c,driver,from,to,"USD")).getCode());
        c.setOccurredAt(null); assertThrows(BadRequestException.class, () -> calculator.calculate(c,driver,from,to,"USD"));
    }
}
