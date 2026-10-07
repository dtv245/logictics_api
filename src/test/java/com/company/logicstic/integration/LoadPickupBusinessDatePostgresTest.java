package com.company.logicstic.integration;

import com.company.logicstic.dto.load.*;
import com.company.logicstic.exception.*;
import com.company.logicstic.service.LoadService;
import com.company.logicstic.service.rating.LoadPickupBusinessDateService;
import com.company.logicstic.service.rating.LoadRatingContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/tenant",
        "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL", matches="jdbc:postgresql:.*codex_.*")
class LoadPickupBusinessDatePostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("TASK_DB_URL"));
        p.add("spring.datasource.username", () -> System.getenv("TASK_DB_USER"));
        p.add("spring.datasource.password", () -> System.getenv("TASK_DB_PASSWORD"));
    }
    @Autowired LoadService loads;
    @Autowired LoadPickupBusinessDateService dates;
    @Autowired LoadRatingContextService rating;
    @Autowired JdbcTemplate jdbc;
    private record Fixture(UUID customer, UUID actor, String email) { }
    private Fixture fixture() {
        UUID customer = UUID.randomUUID(), actor = UUID.randomUUID(); String email = actor + "@pickup-fixture.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Pickup fixture','ACTIVE',false)", customer);
        jdbc.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                values (?,?,'Pickup','Fixture','HOURLY','ACTIVE',now(),0,'USD')
                """, actor, email);
        authenticate(email, "ACCOUNTANT"); return new Fixture(customer, actor, email);
    }
    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "fixture",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }
    private PickupBusinessDateProvenance provenance() { return new PickupBusinessDateProvenance("CUSTOMER_CONFIRMED_PICKUP", "Verified promised pickup date", "fixture agreement reference"); }
    private CreateLoadRequest request(UUID customer, String timestamp, LocalDate date) {
        return new CreateLoadRequest("Date fixture", "FTL", "DRAFT", 0.0, false, customer, null, null, "MANUAL",
                timestamp == null ? null : OffsetDateTime.parse(timestamp), null, null, false, null, null, null, null, null,
                null, null, null, BigDecimal.ZERO, "USD", "Origin", null, "City", "TX", "00000", "US", 0.0, 0.0,
                "Destination", null, "City", "TX", "00000", "US", 0.0, 0.0, date, date == null ? null : provenance());
    }
    @Test void createCapturesExactLocalDateIndependentlyOfAppointmentOffsetAndAuditsActor() {
        var f = fixture(); var expected = LocalDate.of(2026, 2, 1);
        var load = loads.create(request(f.customer(), "2026-02-01T00:30:00+14:00", expected));
        assertEquals(expected, load.requestedPickupBusinessDate());
        assertEquals(expected, jdbc.queryForObject("select requested_pickup_business_date from loads where id=?", LocalDate.class, load.id()));
        assertEquals(f.actor(), jdbc.queryForObject("select actor_id from load_pickup_business_date_changes where id=?", UUID.class, load.pickupBusinessDateChangeId()));
        assertEquals("CUSTOMER_CONFIRMED_PICKUP", jdbc.queryForObject("select reason_code from load_pickup_business_date_changes where id=?", String.class, load.pickupBusinessDateChangeId()));
        assertEquals(expected, rating.pricingDate(load.id()).pricingDate());
        assertEquals("LOAD_REQUESTED_PICKUP_DATE", rating.pricingDate(load.id()).pricingDateSource());
    }
    @Test void updateExplicitLocalDateHasItsOwnChainedAuditAndKeepsCapturedInput() {
        var f = fixture(); var first = loads.create(request(f.customer(), "2026-01-01T00:00:00Z", LocalDate.of(2026,1,1)));
        var capturedInput = rating.pricingDate(first.id());
        var next = loads.update(first.id(), com.company.logicstic.dto.load.UpdateLoadRequest.from(request(f.customer(), "2026-01-01T00:00:00Z", LocalDate.of(2026,1,2)), first.version()));
        assertEquals(LocalDate.of(2026,1,2), next.requestedPickupBusinessDate());
        assertNotEquals(first.pickupBusinessDateChangeId(), next.pickupBusinessDateChangeId());
        assertEquals(first.pickupBusinessDateChangeId(), jdbc.queryForObject("select previous_change_id from load_pickup_business_date_changes where id=?", UUID.class, next.pickupBusinessDateChangeId()));
        assertEquals(LocalDate.of(2026,1,1), capturedInput.pricingDate()); // immutable input, not a fake accepted financial snapshot
        assertEquals(first.pickupBusinessDateChangeId(), capturedInput.sourceChangeId());
    }
    @Test void appointmentEditDoesNotRecomputeOrClearPromisedDate() {
        var f = fixture(); var first = loads.create(request(f.customer(), "2026-01-01T12:00:00Z", LocalDate.of(2026,1,1)));
        var next = loads.update(first.id(), com.company.logicstic.dto.load.UpdateLoadRequest.from(request(f.customer(), "2027-12-31T23:45:00-12:00", null), first.version()));
        assertEquals(first.requestedPickupBusinessDate(), next.requestedPickupBusinessDate());
        assertEquals(first.pickupBusinessDateChangeId(), next.pickupBusinessDateChangeId());
        assertEquals(1, jdbc.queryForObject("select count(*) from load_pickup_business_date_changes where load_id=?", Integer.class, first.id()));
    }
    @Test void legacyTimestampOnlyLoadStaysNullAndRatingRejectsWithoutAnyFallback() {
        var f = fixture(); var load = loads.create(request(f.customer(), "2026-02-01T23:45:00-12:00", null));
        assertNull(load.requestedPickupBusinessDate());
        assertEquals("RATING_PRICING_DATE_REQUIRED", assertThrows(BadRequestException.class, () -> rating.pricingDate(load.id())).getCode());
        jdbc.update("update loads set requested_pickup_date='2030-01-01T00:00:00Z' where id=?", load.id());
        assertNull(loads.getById(load.id()).requestedPickupBusinessDate());
        assertEquals(0, jdbc.queryForObject("select count(*) from load_pickup_business_date_changes where load_id=?", Integer.class, load.id()));
    }
    @Test void explicitHistoricalRemediationIsAuditedAndRejectsStaleCorrections() {
        var f = fixture(); var load = loads.create(request(f.customer(), "2026-01-01T00:00:00Z", null));
        var corrected = dates.remediate(load.id(), new SetPickupBusinessDateRequest(LocalDate.of(2026,1,2), provenance(), null));
        assertEquals(LocalDate.of(2026,1,2), corrected.requestedPickupBusinessDate());
        assertEquals("LOAD_PICKUP_DATE_CONFLICT", assertThrows(ApiException.class, () -> dates.remediate(load.id(),
                new SetPickupBusinessDateRequest(LocalDate.of(2026,1,3), provenance(), null))).getCode());
        assertEquals("LOAD_PICKUP_DATE_PROVENANCE_REQUIRED", assertThrows(BadRequestException.class, () -> dates.remediate(load.id(),
                new SetPickupBusinessDateRequest(LocalDate.of(2026,1,3), null, corrected.pickupBusinessDateChangeId()))).getCode());
    }
    @Test void databaseBlocksUnauditedDateRewriteAndAuditMutation() {
        var f = fixture(); var load = loads.create(request(f.customer(), null, LocalDate.of(2026,1,1)));
        assertThrows(DataAccessException.class, () -> jdbc.update("update loads set requested_pickup_business_date='2026-02-01' where id=?", load.id()));
        assertThrows(DataAccessException.class, () -> jdbc.update("update load_pickup_business_date_changes set reason='rewritten' where id=?", load.pickupBusinessDateChangeId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from load_pickup_business_date_changes where id=?", load.pickupBusinessDateChangeId()));
    }
    @Test void unauthorizedDateInputRollsBackCreateAndCannotFabricateAudit() {
        var f = fixture(); authenticate(f.email(), "DRIVER");
        assertThrows(ForbiddenException.class, () -> loads.create(request(f.customer(), null, LocalDate.of(2026,1,1))));
        assertEquals(0, jdbc.queryForObject("select count(*) from loads where customer_id=?", Integer.class, f.customer()));
    }
}
