package com.company.logicstic.integration;

import com.company.logicstic.service.cost.ExpenseApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only on an explicitly supplied disposable PostgreSQL database. */
@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/tenant", "app.tenancy.enabled=false",
        "app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL", matches="jdbc:postgresql:.*codex_.*")
class CostLedgerPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TASK_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("TASK_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TASK_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ExpenseApprovalService approvals;
    @Autowired com.company.logicstic.service.cost.CostAllocator allocator;
    @Autowired com.company.logicstic.service.accessorial.AccessorialService accessorials;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @Autowired com.company.logicstic.service.payroll.DriverPayPolicyService payPolicies;
    @Autowired com.company.logicstic.service.payroll.DriverPayPolicyResolver payPolicyResolver;
    @Autowired com.company.logicstic.service.payroll.PayPeriodService payPeriods;

    @Test void contextLoadsWithEveryEntityAndController() {
        assertTrue(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class) >= 11);
    }

    @Test void concurrentRetriesProduceOneCostAndPreserveApprovalAudit() throws Exception {
        var fixture = fixture();
        UUID expense = fixture.expense(), actor = fixture.actor(); String email = fixture.email();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> approvals.approve(expense, email));
            var second = pool.submit(() -> approvals.approve(expense, email));
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.shipmentCostId(), b.shipmentCostId()); assertNotNull(a.shipmentCostId());
            assertEquals(a.approvedAt().toInstant(), b.approvedAt().toInstant()); assertEquals(actor.toString(), a.approvedBy());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from shipment_costs where source_type='EXPENSE' and source_id=?", Integer.class, expense));
        assertEquals("ACTUAL", jdbc.queryForObject("select cost_basis from shipment_costs where source_id=?", String.class, expense));
        assertEquals("VERIFIED", jdbc.queryForObject("select status from shipment_costs where source_id=?", String.class, expense));
    }

    @Test void ledgerConflictRollsBackExpenseApproval() {
        var fixture = fixture();
        jdbc.update("""
                insert into shipment_costs(id,load_id,category,cost_basis,status,source_type,source_id,amount,currency)
                values (?,?,'FUEL','ACTUAL','POSTED','EXPENSE',?,99,'USD')
                """, UUID.randomUUID(), fixture.load(), fixture.expense());
        assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> approvals.approve(fixture.expense(), fixture.email()));
        assertEquals("PENDING_APPROVAL", jdbc.queryForObject("select status from expenses where id=?", String.class, fixture.expense()));
        assertNull(jdbc.queryForObject("select approved_at from expenses where id=?", java.sql.Timestamp.class, fixture.expense()));
    }

    @Test void allocationPersistsSnapshotAndRetryCreatesNeitherNewCostNorSnapshot() {
        var fixture = fixture(); UUID truck = UUID.randomUUID(), trip = UUID.randomUUID();
        jdbc.update("""
                insert into trucks(id,number,type,vehicle_capacity,status,is_hazmat_placarded,
                    adr_equipment_allowed_classes,adr_equipment_is_adr_certified)
                values (?,?,'SEMI',1,'ACTIVE',false,'',false)
                """, truck, truck.toString());
        jdbc.update("""
                insert into trips(id,name,total_distance,status,truck_id,actual_distance_miles)
                values (?,'Test Trip',99999,'COMPLETED',?,12.5)
                """, trip, truck);
        jdbc.update("""
                insert into trip_stops(id,type,trip_id,"order",load_id,address_city,address_country,address_line1,
                    address_state,address_zip_code,location_latitude,location_longitude)
                values (?,'DELIVERY',?,1,?,'Test','US','Test','TX','00000',0,0)
                """, UUID.randomUUID(), trip, fixture.load());
        var first = allocator.allocateMaintenanceCost(fixture.load(), new java.math.BigDecimal("2.5"), "USD");
        var second = allocator.allocateMaintenanceCost(fixture.load(), new java.math.BigDecimal("2.500000"), "usd");
        assertEquals(first.id(), second.id()); assertEquals("ESTIMATE", first.costBasis());
        assertEquals(new java.math.BigDecimal("31.25"), first.amount()); assertEquals(truck, first.truckId());
        assertEquals(1, jdbc.queryForObject("select count(*) from calculation_snapshots where entity_id=?", Integer.class, first.id()));
        jdbc.update("update shipment_costs set status='APPROVED' where id=?", first.id());
        assertEquals(first.id(), allocator.allocateMaintenanceCost(fixture.load(), new java.math.BigDecimal("2.5"), "USD").id());
        assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> allocator.allocateMaintenanceCost(fixture.load(), java.math.BigDecimal.TEN, "USD"));
    }

    @Test void actualProfitabilityRoutesSerializeVersionedClassificationWithoutWrites() throws Exception {
        var fixture = fixture(); UUID invoice = addInvoice(fixture.load());
        jdbc.update("update loads set delivery_cost_amount=99999 where id=?", fixture.load());
        jdbc.update("""
                insert into shipment_costs(id,load_id,category,cost_basis,status,source_type,source_id,amount,currency)
                values (?,?,'FUEL','ACTUAL','POSTED','MANUAL',?,20,'USD')
                """, UUID.randomUUID(), fixture.load(), UUID.randomUUID());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        int snapshotCount = jdbc.queryForObject("select count(*) from calculation_snapshots", Integer.class);
        int costCount = jdbc.queryForObject("select count(*) from shipment_costs", Integer.class);
        String[] paths = {"/api/loads/" + fixture.load() + "/financial-summary",
                "/api/reports/profitability/by-load?loadId=" + fixture.load(),
                "/api/reports/profitability/by-lane", "/api/reports/profitability/by-truck"};
        for (String path : paths) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver").roles("DRIVER")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("ACCOUNTANT")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
            var data = json.readTree(response.getResponse().getContentAsString()).get("data");
            assertNotNull(data);
            if (path.contains("financial-summary")) {
                assertEquals(100, data.get("actualRevenue").decimalValue().intValueExact());
                assertEquals(20, data.get("actualCost").decimalValue().intValueExact());
                assertEquals(80, data.get("contributionMargin").decimalValue().intValueExact());
                assertEquals("AVAILABLE",data.get("contributionMarginMetric").get("availability").asText());
                assertEquals(80, data.get("allocatedProfit").decimalValue().intValueExact());
                assertEquals("LOGISTICSX_COST_CLASSIFICATION", data.get("costClassification").get("policyName").asText());
                assertEquals("1", data.get("costClassification").get("policyVersion").asText());
                assertEquals("VARIABLE", data.get("costClassification").get("costs").get(0).get("behavior").asText());
            } else assertTrue(data.isArray());
        }
        assertEquals("ISSUED",jdbc.queryForObject("select status from invoices where id=?", String.class,invoice));
        assertEquals(snapshotCount,jdbc.queryForObject("select count(*) from calculation_snapshots", Integer.class));
        assertEquals(costCount,jdbc.queryForObject("select count(*) from shipment_costs", Integer.class));
    }

    @Test void profitabilityReturnsFourHundredForSourceCurrencyMismatch() throws Exception {
        var fixture = fixture(); UUID invoice = addInvoice(fixture.load());
        jdbc.update("update invoice_line_items set amount_currency='VND' where invoice_id=?", invoice);
        try {
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/loads/" + fixture.load() + "/financial-summary")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("ACCOUNTANT")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("CURRENCY_MISMATCH"));
        } finally {
            jdbc.update("update invoice_line_items set amount_currency='USD' where invoice_id=?", invoice);
        }
    }

    @Test void profitabilityReconcilesDirectAndAllocatedMaintenanceAndSuppressesUnknownActualCosts() throws Exception {
        var fixture = fixture(); addInvoice(fixture.load());
        String[][] inputs = {{"FUEL","MANUAL",null,"20"}, {"MAINTENANCE","MANUAL","DIRECT","10"},
                {"MAINTENANCE","ALLOCATION","CPM_MILEAGE","5"}, {"INSURANCE","MANUAL",null,"3"},
                {"DRIVER","DRIVER_SETTLEMENT_REVERSAL",null,"-2"}};
        for (String[] row : inputs) {
            jdbc.update("""
                    insert into shipment_costs(id,load_id,category,cost_basis,status,source_type,source_id,allocation_method,amount,currency)
                    values (?, ?, ?, 'ACTUAL', 'POSTED', ?, ?, ?, ?, 'USD')
                    """, UUID.randomUUID(),fixture.load(),row[0],row[1],UUID.randomUUID(),row[2],new java.math.BigDecimal(row[3]));
        }
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/loads/" + fixture.load() + "/financial-summary")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("ACCOUNTANT"));
        mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.actualCost").value(36))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.contributionMargin").value(72))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.allocatedProfit").value(64))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.costClassification.variableCost").value(28))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.costClassification.allocatedFixedCost").value(8))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.costClassification.contributionMarginPercent.value").value(0.72))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.marginPercent.value").value(64));
        jdbc.update("""
                insert into shipment_costs(id,load_id,category,cost_basis,status,source_type,amount,currency)
                values (?,?,'MAINTENANCE','ACTUAL','APPROVED','EXPENSE',0,'USD')
                """,UUID.randomUUID(),fixture.load());
        mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.allocatedProfit").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.contributionMarginMetric.availability").value("UNAVAILABLE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.allocatedProfitMetric.reason").value("COST_CLASSIFICATION_INCOMPLETE"));
    }

    private UUID addInvoice(UUID loadId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into invoices(id,type,status,subtotal_amount,subtotal_currency,tax_total_amount,tax_total_currency,
                    total_amount,total_currency,load_id)
                values (?,'LOAD','ISSUED',100,'USD',0,'USD',100,'USD',?)
                """, id,loadId);
        jdbc.update("""
                insert into invoice_line_items(id,invoice_id,description,type,quantity,"order",tax_rate_percent,tax_amount,amount_amount,amount_currency)
                values (?,?,'Test freight','FREIGHT',1,1,0,0,100,'USD')
                """, UUID.randomUUID(),id);
        return id;
    }

    @Test void policyVersionConcurrencyPreservesLockedSettlementHistoricalPolicy() throws Exception {
        var fixture = fixture(); var start = java.time.LocalDate.of(2026,1,1);
        String code = UUID.randomUUID().toString();
        var old = payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",start,null));
        var period = payPeriods.create(code,start,start.plusDays(30),null);
        UUID settlement = UUID.randomUUID(), snapshot = UUID.randomUUID();
        jdbc.update("""
                insert into calculation_snapshots(id,entity_type,entity_id,calculation_type,engine_name,engine_version,input_json,result_json)
                values (?,'SETTLEMENT',?,'DRIVER_PAY','Test','1','{}','{}')
                """,snapshot,settlement);
        jdbc.update("""
                insert into settlements(id,settlement_number,driver_id,pay_period_id,pay_policy_id,pay_policy_version,status,currency,calculation_snapshot_id,settlement_net)
                values (?, ?, ?, ?, ?, 1, 'LOCKED', 'USD', ?, 10)
                """,settlement,settlement.toString(),fixture.actor(),period.getId(),old.getId(),snapshot);
        var request = payPolicyRequest(code,fixture.actor(),"2",start.plusMonths(1),start.plusMonths(2));
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> create = () -> {
                gate.await();
                try { return payPolicies.newVersion(old.getId(),request); }
                catch (com.company.logicstic.exception.BadRequestException e) { return e.getCode(); }
            };
            var first = pool.submit(create); var second = pool.submit(create); gate.countDown();
            var results = java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(com.company.logicstic.entity.DriverPayPolicy.class::isInstance).count());
            assertEquals(1,results.stream().filter("POLICY_VERSION_STALE"::equals).count());
        }
        assertEquals(2,jdbc.queryForObject("select count(*) from driver_pay_policies where policy_code=?",Integer.class,code));
        assertEquals(0,new java.math.BigDecimal("1").compareTo(jdbc.queryForObject("select per_mile_rate from driver_pay_policies where id=?",java.math.BigDecimal.class,old.getId())));
        assertNull(jdbc.queryForObject("select effective_to from driver_pay_policies where id=?",java.sql.Date.class,old.getId()));
        assertEquals(old.getId(),jdbc.queryForObject("select pay_policy_id from settlements where id=?",UUID.class,settlement));
        assertEquals(1,jdbc.queryForObject("select pay_policy_version from settlements where id=?",Integer.class,settlement));
        assertEquals(old.getId(),payPolicyResolver.resolve(fixture.actor(),start.plusDays(15)).getId());
        assertEquals(2,payPolicyResolver.resolve(fixture.actor(),start.plusMonths(1)).getPolicyVersion());
        assertThrows(com.company.logicstic.exception.BadRequestException.class, () -> payPolicyResolver.resolve(fixture.actor(),start.plusMonths(2).plusDays(1)));
    }

    @Test void payPolicyAndPeriodEndpointsAreProtectedAndValidateActualContracts() throws Exception {
        var fixture = fixture(); var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        for (String path : new String[]{"/api/driver-pay-policies","/api/pay-periods"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver").roles("DRIVER")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
        String periodJson = "{\"periodCode\":\"" + UUID.randomUUID() + "\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/pay-periods").contentType("application/json").content(periodJson)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("PAYROLL_MANAGER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("OPEN"));
        String policyJson = "{\"policyCode\":\""+UUID.randomUUID()+"\",\"name\":\"Contract\",\"driverId\":\""+fixture.actor()+"\",\"payMethod\":\"PERCENT_REVENUE\",\"revenuePercentage\":0.25,\"revenueBasis\":\"INVOICE_SUBTOTAL\",\"currency\":\"USD\",\"effectiveFrom\":\"2026-01-01\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/driver-pay-policies").contentType("application/json").content(policyJson)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("PAYROLL_MANAGER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.revenuePercentage").value(0.25));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/driver-pay-policies").contentType("application/json").content(policyJson.replace("0.25","25"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("PAYROLL_MANAGER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    private com.company.logicstic.dto.payroll.DriverPayPolicyRequest payPolicyRequest(String code, UUID driver, String rate,
            java.time.LocalDate from, java.time.LocalDate to) {
        return new com.company.logicstic.dto.payroll.DriverPayPolicyRequest(code,"Mileage",driver,"PER_MILE",new java.math.BigDecimal(rate),
                null,null,null,null,null,"ACTUAL_ALL_MILES",null,null,null,null,null,null,"USD",from,to);
    }

    private record Fixture(UUID actor, UUID load, UUID expense, String email) {}

    @Test void concurrentAccessorialApprovalsProjectOnlyCompanyCost() throws Exception {
        var fixture = fixture();
        var request = new com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest(null,null,"DETENTION",
                null,null,null,null,new java.math.BigDecimal("100"),new java.math.BigDecimal("20"),
                new java.math.BigDecimal("10"),"USD",null,null,"test");
        var charge = accessorials.createAccessorial(fixture.load(), request);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> accessorials.approveAccessorial(charge.id(), fixture.email()));
            var second = pool.submit(() -> accessorials.approveAccessorial(charge.id(), fixture.email()));
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.approvedAt().toInstant(), b.approvedAt().toInstant()); assertEquals(fixture.actor(), b.approvedBy());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from shipment_costs where source_type='ACCESSORIAL' and source_id=?", Integer.class, charge.id()));
        assertEquals(0, new java.math.BigDecimal("20").compareTo(jdbc.queryForObject("select amount from shipment_costs where source_id=?", java.math.BigDecimal.class, charge.id())));
        jdbc.update("update accessorial_charges set status='VOIDED' where id=?", charge.id());
        assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> accessorials.approveAccessorial(charge.id(), fixture.email()));
    }

    @Test void approvalEndpointCannotSpoofActorWithQueryParameter() throws Exception {
        var fixture = fixture();
        var request = new com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest(null,null,"OTHER",
                null,null,null,null,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,"USD",null,null,null);
        var charge = accessorials.createAccessorial(fixture.load(), request);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/accessorial-charges/" + charge.id() + "/approve")
                .param("approvedBy", UUID.randomUUID().toString())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("ACCOUNTANT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(fixture.actor(), jdbc.queryForObject("select approved_by from accessorial_charges where id=?", UUID.class, charge.id()));
    }

    private Fixture fixture() {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), load = UUID.randomUUID(), expense = UUID.randomUUID();
        String email = actor + "@example.test";
        jdbc.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                values (?,?,'Test','Accountant','HOURLY','ACTIVE',now(),0,'USD')
                """, actor, email);
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Test Customer','ACTIVE',false)", customer);
        jdbc.update("""
                insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,
                  delivery_cost_amount,delivery_cost_currency,destination_address_city,destination_address_country,
                  destination_address_line1,destination_address_state,destination_address_zip_code,destination_location_latitude,
                  destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                  origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude)
                values (?,'Test Load','FTL','DELIVERED',0,false,?,'MANUAL',false,0,'USD',
                  'Test','US','Test','TX','00000',0,0,'Test','US','Test','TX','00000',0,0)
                """, load, customer);
        jdbc.update("""
                insert into expenses(id,type,status,expense_date,amount_amount,amount_currency,category,load_id)
                values (?,'TRUCK','PENDING_APPROVAL',now(),25,'USD','FUEL',?)
                """, expense, load);
        return new Fixture(actor, load, expense, email);
    }
}
