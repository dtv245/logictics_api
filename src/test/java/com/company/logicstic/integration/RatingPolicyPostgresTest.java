package com.company.logicstic.integration;

import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.RatePolicyService;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/tenant", "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL", matches="jdbc:postgresql:.*codex_.*")
class RatingPolicyPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("TASK_DB_URL"));
        p.add("spring.datasource.username", () -> System.getenv("TASK_DB_USER"));
        p.add("spring.datasource.password", () -> System.getenv("TASK_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RatePolicyService policies;
    @Autowired com.company.logicstic.service.rating.RateRuleResolver resolver;
    @Autowired WebApplicationContext web;
    private static final LocalDate DATE = LocalDate.of(2026, 1, 1);
    private record Fixture(UUID customer, UUID actor, String email) { }
    private Fixture fixture() {
        var c = UUID.randomUUID(); var a = UUID.randomUUID(); String email = a + "@rating-fixture.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Rating test customer','ACTIVE',false)", c);
        jdbc.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                values (?,?,'Rating','Fixture','HOURLY','ACTIVE',now(),0,'USD')
                """, a, email);
        return new Fixture(c, a, email);
    }
    private RateRuleRequest rule(Fixture f, UUID contract, Integer contractVersion, String amount) {
        return new RateRuleRequest(10, f.customer(), contract, contractVersion, "TEST-LANE", "DRY_VAN", null, null,
                "USD", DATE, DATE.plusMonths(1), RatingMethod.PER_MILE, new BigDecimal(amount),
                RatingMileageBasis.CONTRACT_MILES, null, null, null);
    }
    @Test void appendVersionsPreservesFullDecimalAuditAndBlocksSqlHistoryMutation() {
        var f = fixture(); var c = policies.createContract(new RatingContractRequest(f.customer(), "USD", DATE, DATE.plusYears(1)), f.actor());
        var r = policies.createRule(rule(f, c.contractId(), c.version(), "1.123456789123"), f.actor());
        assertEquals(0, new BigDecimal("1.123456789123").compareTo(r.baseRate()));
        assertEquals(f.actor(), r.createdBy()); assertNotNull(r.createdAt()); assertEquals("RatingPolicyV1", r.roundingPolicyCode());
        var v2 = policies.newRuleVersion(r.ruleId(), 1, rule(f, c.contractId(), c.version(), "2"), f.actor());
        assertEquals(2, v2.version()); assertEquals(r, policies.getRule(r.ruleId(), 1));
        assertThrows(DataAccessException.class, () -> jdbc.update("update customer_rate_rule_versions set base_rate=3 where rule_id=?", r.ruleId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from customer_rating_contract_versions where contract_id=?", c.contractId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("update customer_rating_contracts set customer_id=? where id=?", f.customer(), c.contractId()));
    }
    @Test void concurrentAppendFromSameVersionHasOneWinnerAndOneConflict() throws Exception {
        var f = fixture(); var r = policies.createRule(rule(f, null, null, "1"), f.actor());
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { policies.newRuleVersion(r.ruleId(), 1, rule(f, null, null, "2"), f.actor()); return "APPENDED"; }
                catch (ApiException e) { return e.getCode(); }
            }));
            var results = tasks.stream().map(t -> {
                try { return t.get(20, TimeUnit.SECONDS); } catch (Exception e) { throw new AssertionError(e); }
            }).sorted().toList();
            assertEquals(java.util.List.of("APPENDED", "RATE_POLICY_VERSION_STALE"), results);
        }
        assertEquals(2, jdbc.queryForObject("select count(*) from customer_rate_rule_versions where rule_id=?", Integer.class, r.ruleId()));
    }
    @Test void databaseRejectsInvalidMethodsInputsAndContractIdentityEvenOutsideService() {
        var f = fixture(); var c = policies.createContract(new RatingContractRequest(f.customer(), "USD", DATE, DATE.plusYears(1)), f.actor());
        var r = policies.createRule(rule(f, c.contractId(), 1, "1"), f.actor());
        for (String change : new String[] {"'TIERED'", "NULL"})
            assertThrows(DataAccessException.class, () -> jdbc.update("""
                    insert into customer_rate_rule_versions(rule_id,rule_version,priority,currency,effective_from,rating_method,
                    base_rate,rounding_policy_code,rounding_policy_version,created_by,created_at)
                    select rule_id,3,priority,currency,effective_from,
                    """ + change + ",base_rate,rounding_policy_code,rounding_policy_version,created_by,created_at from customer_rate_rule_versions where rule_id=? and rule_version=1", r.ruleId()));
        var other = fixture();
        assertThrows(BadRequestException.class, () -> policies.createRule(rule(other, c.contractId(), 1, "1"), f.actor()));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                insert into customer_rate_rule_versions(rule_id,rule_version,priority,customer_id,contract_id,contract_version,currency,
                effective_from,effective_to,rating_method,base_rate,rounding_policy_code,rounding_policy_version,created_by,created_at)
                values (?,4,10,?,?,1,'USD',? ,?,'FLAT',1,'RatingPolicyV1',1,?,now())
                """, r.ruleId(), other.customer(), c.contractId(), DATE, DATE.plusMonths(1), f.actor()));
    }
    @Test void authoringApiRequiresRoleAndRecordsAuthenticatedActor() throws Exception {
        var f = fixture(); var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var body = "{\"customerId\":\"" + f.customer() + "\",\"currency\":\"USD\",\"effectiveFrom\":\"2026-01-01\"}";
        mvc.perform(post("/api/rating/contracts").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rating/contracts").with(user(f.email()).roles("DRIVER")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var response = mvc.perform(post("/api/rating/contracts").with(user(f.email()).roles("ACCOUNTANT"))
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var data = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("data");
        assertEquals(f.actor().toString(), data.get("createdBy").asText());
    }

    @Test void resolverUsesInclusiveDatabaseDatesAndExplicitPriorityWithoutSpecificity() {
        var f = fixture(); var r = policies.createRule(rule(f, null, null, "1"), f.actor());
        var context = new RateMatchContext(f.customer(), null, null, "TEST-LANE", "DRY_VAN", null, null, "USD", DATE);
        assertEquals(r, resolver.resolve(context));
        assertEquals(r, resolver.resolve(new RateMatchContext(f.customer(), null, null, "TEST-LANE", "DRY_VAN", null, null, "USD", DATE.plusMonths(1))));
        assertEquals("RATE_RULE_NOT_FOUND", assertThrows(BadRequestException.class, () -> resolver.resolve(
                new RateMatchContext(f.customer(), null, null, "TEST-LANE", "DRY_VAN", null, null, "USD", DATE.minusDays(1)))).getCode());
        assertEquals("RATE_RULE_NOT_FOUND", assertThrows(BadRequestException.class, () -> resolver.resolve(
                new RateMatchContext(f.customer(), null, null, "TEST-LANE", "DRY_VAN", null, null, "USD", DATE.plusMonths(1).plusDays(1)))).getCode());
        var generic = policies.createRule(new RateRuleRequest(1, f.customer(), null, null, null, null, null, null,
                "USD", DATE, DATE.plusMonths(1), RatingMethod.FLAT, BigDecimal.ONE, null, null, null, null), f.actor());
        assertEquals(generic, resolver.resolve(context));
        assertEquals("RATE_RULE_NOT_FOUND", assertThrows(BadRequestException.class, () -> resolver.resolve(
                new RateMatchContext(UUID.randomUUID(), null, null, "TEST-LANE", "DRY_VAN", null, null, "USD", DATE))).getCode());
    }

    @Test void resolverRejectsWinningAmbiguityAndDoesNotSilentlySelectNewestVersion() {
        var f = fixture(); var r = policies.createRule(rule(f, null, null, "1"), f.actor());
        policies.newRuleVersion(r.ruleId(), 1, rule(f, null, null, "2"), f.actor());
        var c = new RateMatchContext(f.customer(), null, null, "TEST-LANE", "DRY_VAN", null, null, "USD", DATE);
        assertEquals("RATE_RULE_AMBIGUOUS", assertThrows(BadRequestException.class, () -> resolver.resolve(c)).getCode());
        assertEquals(2, jdbc.queryForObject("select count(*) from customer_rate_rule_versions where rule_id=?", Integer.class, r.ruleId()));
    }
}
