package com.company.logicstic.integration;

import com.company.logicstic.dto.invoice.*;
import com.company.logicstic.dto.load.*;
import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.billing.*;
import com.company.logicstic.service.billing.domain.BillingInvoice;
import com.company.logicstic.service.rating.*;
import com.company.logicstic.service.rating.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
@org.springframework.context.annotation.Import(CostLedgerPostgresTest.PayrollFixtureConfiguration.class)
class BillingPrimaryPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;@Autowired BillingService billing;@Autowired TaxAssessmentService taxes;
    @Autowired RatePolicyService policies;@Autowired RatingPreviewService previews;@Autowired RatingSnapshotService snapshots;
    @Autowired LoadPickupBusinessDateService dates;
    @Autowired com.company.logicstic.service.payroll.DriverPayEngine driverPay;
    @Autowired com.company.logicstic.service.payroll.DriverPayPolicyService driverPolicies;
    @Autowired com.company.logicstic.service.payroll.PayPeriodService periods;
    @Autowired com.company.logicstic.service.payroll.SettlementRevenueService settlementRevenue;
    @Autowired com.company.logicstic.service.payroll.PayrollCalculationService payroll;
    @Autowired com.company.logicstic.service.payroll.PayrollWorkflowService payrollWorkflow;
    @Autowired com.company.logicstic.service.payroll.policy.PayrollConfigurationService payrollConfiguration;
    @Autowired com.company.logicstic.service.calculation.RevenueCalculator revenues;
    @Autowired com.company.logicstic.service.profitability.ProfitabilityService profitability;
    @Autowired org.springframework.web.context.WebApplicationContext web;@Autowired tools.jackson.databind.ObjectMapper json;
    private static final LocalDate DATE=LocalDate.of(2026,1,12);
    record Fixture(UUID load,UUID customer,UUID actor,String email,AcceptedRatingSnapshot snapshot,RatingPreviewRequest preview) { }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    Fixture fixture(String currency,String amount) {
        UUID load=UUID.randomUUID(),customer=UUID.randomUUID(),actor=UUID.randomUUID();String email=actor+"@billing.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Billing','ACTIVE',false)",customer);
        jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Billing','Audit','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        jdbc.update("""
            insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
            destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
            destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
            origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude)
            values (?,'Billing','FTL','DELIVERED',0,false,?,'MANUAL',false,0,?,
            'City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0)
            """,load,customer,currency);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"test",List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        dates.remediate(load,new SetPickupBusinessDateRequest(DATE,new PickupBusinessDateProvenance("CUSTOMER_PICKUP","Customer promise","signed contract"),null));
        var contract=policies.createContract(new RatingContractRequest(customer,currency,DATE,DATE.plusMonths(1)),actor);
        policies.createRule(new RateRuleRequest(10,customer,contract.contractId(),1,null,null,null,null,currency,DATE,DATE.plusMonths(1),
                RatingMethod.FLAT,new BigDecimal(amount),null,null,null,null),actor);
        var request=new RatingPreviewRequest(contract.contractId(),1,null,null,null,null,currency,"signed contract",null,null,List.of());
        var p=previews.preview(load,request,"billing fixture");
        var snapshot=snapshots.accept(load,new RatingAcceptRequest("billing-rating-"+UUID.randomUUID(),request,p.inputHash(),p.resultHash(),null,null,null),actor,"billing fixture");
        return new Fixture(load,customer,actor,email,snapshot,request);
    }
    GenerateInvoiceRequest noTax(Fixture f,String key) {
        return new GenerateInvoiceRequest(key,f.snapshot().snapshotId(),new GenerateInvoiceRequest.TaxDecision("NOT_REQUIRED","ACCOUNTING_TAX_NOT_REQUIRED",
                "Accounting verified no tax required","signed accounting decision",null),List.of());
    }
    BillingInvoice generate(Fixture f) { return billing.generatePrimary(noTax(f,"primary-"+UUID.randomUUID()),f.actor(),"test"); }
    BillingInvoice issue(Fixture f,BillingInvoice invoice) { return billing.issue(invoice.invoiceId(),"issue-"+UUID.randomUUID(),f.actor()); }
    AcceptedRatingSnapshot correction(Fixture f,List<UUID> charges) {
        var old=f.preview();var request=new RatingPreviewRequest(old.contractId(),old.contractVersion(),old.lane(),old.equipment(),old.service(),old.tier(),
                old.currency(),old.contextSource(),old.linehaulMileageEvidenceId(),old.fscMileageEvidenceId(),charges);
        var p=previews.preview(f.load(),request,"correction");
        return snapshots.accept(f.load(),new RatingAcceptRequest("new-basis-"+UUID.randomUUID(),request,p.inputHash(),p.resultHash(),f.snapshot().snapshotId(),"APPROVED_CORRECTION","Accounting approved new basis"),f.actor(),"correction");
    }
    BillingCorrectionRequests.Credit credit(Fixture f,BillingInvoice parent,String amount) {
        return new BillingCorrectionRequests.Credit("credit-"+UUID.randomUUID(),List.of(new BillingCorrectionRequests.CreditLine(parent.lines().getFirst().lineId(),new BigDecimal(amount),BigDecimal.ZERO,null)),
                noTax(f,"unused").taxDecision(),"APPROVED_CREDIT","Accounting approved explicit line credit");
    }
    GenerateInvoiceRequest generation(Fixture f,UUID snapshot) { return new GenerateInvoiceRequest("command-"+UUID.randomUUID(),snapshot,noTax(f,"unused").taxDecision(),List.of()); }
    com.company.logicstic.dto.payroll.DriverSettlementView settlement(Fixture f,String basis) {
        LocalDate date=LocalDate.of(2026,1,1);String code=UUID.randomUUID().toString();
        driverPolicies.create(new com.company.logicstic.dto.payroll.DriverPayPolicyRequest(code,"Revenue",f.actor(),"PERCENT_REVENUE",null,null,null,null,null,new BigDecimal("0.25"),null,basis,null,null,null,null,null,"USD",date,null));
        var period=periods.create(code,date,date.plusDays(30),null);UUID trip=UUID.randomUUID();
        jdbc.update("insert into trips(id,name,total_distance,status,completed_at) values (?,'Revenue',0,'COMPLETED','2026-01-15T12:00:00Z')",trip);
        jdbc.update("insert into trip_driver_assignments(id,trip_id,driver_id,effective_from,effective_to) values (?,?,?,'2026-01-14T12:00:00Z','2026-01-15T12:00:00Z')",UUID.randomUUID(),trip,f.actor());
        jdbc.update("insert into trip_stops(id,trip_id,load_id,\"order\",type,address_city,address_country,address_line1,address_state,address_zip_code,location_latitude,location_longitude) values (?,?,?,0,'DELIVERY','City','US','Address','TX','00000',0,0)",UUID.randomUUID(),trip,f.load());
        return driverPay.calculate(f.actor(),period.getId());
    }
    void finalizeSettlement(Fixture f,UUID id) {driverPay.transition(id,"IN_REVIEW",f.actor());driverPay.transition(id,"APPROVED",f.actor());driverPay.transition(id,"LOCKED",f.actor());}
    com.company.logicstic.dto.payroll.PayrollRunView lockedPayroll(Fixture f,com.company.logicstic.dto.payroll.DriverSettlementView settlement) {
        var date=LocalDate.of(2026,1,1);var jurisdiction=new com.company.logicstic.service.payroll.domain.PayrollJurisdiction("US",UUID.randomUUID().toString(),null);
        var classification=com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR;
        payrollConfiguration.appendProfile(f.actor(),new com.company.logicstic.dto.payroll.PayrollConfigurationRequests.Profile(jurisdiction,classification,date,null,true));
        payrollConfiguration.appendPolicy(new com.company.logicstic.dto.payroll.PayrollConfigurationRequests.Policy(UUID.randomUUID().toString(),jurisdiction,classification,date,null,true,"USD","TEST_ONLY_FIXED","fixture://explicit-test-policy","{}"));
        var run=payroll.calculate(new com.company.logicstic.dto.payroll.CalculatePayrollRequest("payroll-"+UUID.randomUUID(),settlement.payPeriodId(),"USD",date.plusDays(30),List.of(settlement.id()),Map.of(),Map.of(),List.of()));
        payrollWorkflow.transition(run.id(),"IN_REVIEW",f.actor());payrollWorkflow.transition(run.id(),"APPROVED",f.actor());return payrollWorkflow.transition(run.id(),"LOCKED",f.actor());
    }
    @Test void primaryUsesAcceptedBasisEvenAfterLoadAndRuleChangeAndRetryPreservesOutcome() {
        var f=fixture("USD","100.005");var request=noTax(f,"retry-"+UUID.randomUUID());
        var invoice=billing.generatePrimary(request,f.actor(),"test");assertEquals(new BigDecimal("100.01"),invoice.subtotal());assertEquals(f.snapshot().snapshotId(),invoice.snapshotId());
        var date=f.snapshot().calculation().inputs().pricingDate();
        dates.remediate(f.load(),new SetPickupBusinessDateRequest(DATE.plusDays(1),new PickupBusinessDateProvenance("PROMISE_CHANGED","New promise","customer"),date.sourceChangeId()));
        var rule=f.snapshot().calculation().inputs().rule();
        policies.createRule(new RateRuleRequest(5,f.customer(),rule.contractId(),rule.contractVersion(),null,null,null,null,"USD",DATE,DATE.plusMonths(1),RatingMethod.FLAT,new BigDecimal("500"),null,null,null,null),f.actor());
        assertEquals(invoice,billing.generatePrimary(request,f.actor(),"replay"));
        assertEquals("INVOICE_PRIMARY_EXISTS",assertThrows(ApiException.class,()->generate(f)).getCode());
        var different=new GenerateInvoiceRequest(request.idempotencyKey(),request.snapshotId(),new GenerateInvoiceRequest.TaxDecision("NOT_REQUIRED","OTHER_REASON","Different reason","source",null),List.of());
        assertEquals("INVOICE_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->billing.generatePrimary(different,f.actor(),"drift")).getCode());
    }
    @Test void mandatoryTaxNeedsRealAssessmentAndExplicitLineAllocation() {
        var f=fixture("USD","100");var decision=new GenerateInvoiceRequest.TaxDecision("REQUIRED","ACCOUNTING_REQUIRED","Tax required","accounting decision",null);
        var missing=new GenerateInvoiceRequest("missing-"+UUID.randomUUID(),f.snapshot().snapshotId(),decision,List.of());
        assertEquals("INVOICE_TAX_ASSESSMENT_REQUIRED",assertThrows(ApiException.class,()->billing.generatePrimary(missing,f.actor(),"test")).getCode());
        assertEquals(0,jdbc.queryForObject("select count(*) from invoices where load_id=?",Integer.class,f.load()));
        var a=taxes.capture(new TaxAssessmentRequest(UUID.randomUUID(),f.load(),f.customer(),"ACCOUNTING","assessment reference","jurisdiction",new BigDecimal("100"),new BigDecimal("12.34"),"USD",null,OffsetDateTime.parse("2026-01-12T00:00:00Z"),"original accountant"),f.actor());
        var supplied=new GenerateInvoiceRequest("tax-"+UUID.randomUUID(),f.snapshot().snapshotId(),new GenerateInvoiceRequest.TaxDecision("REQUIRED",decision.reasonCode(),decision.reason(),decision.sourceReference(),a.assessment().assessmentId()),
                List.of(new GenerateInvoiceRequest.LineTax("LINEHAUL",f.snapshot().calculation().inputs().rule().ruleId(),new BigDecimal("12.34"))));
        var invoice=billing.generatePrimary(supplied,f.actor(),"test");assertEquals(new BigDecimal("112.34"),invoice.total());
        assertEquals(new BigDecimal("12.34"),invoice.lines().getFirst().taxAmount());
        assertNull(jdbc.queryForObject("select tax_rate_percent from invoice_line_items where invoice_id=?",BigDecimal.class,invoice.invoiceId()));
    }
    @Test void missingDecisionAndWrongAssessmentCurrencyCannotBecomeDefaultZero() {
        var f=fixture("USD","100");
        assertEquals("INVOICE_TAX_DECISION_REQUIRED",assertThrows(ApiException.class,()->billing.generatePrimary(new GenerateInvoiceRequest("missing-"+UUID.randomUUID(),f.snapshot().snapshotId(),null,List.of()),f.actor(),"test")).getCode());
        var a=taxes.capture(new TaxAssessmentRequest(UUID.randomUUID(),f.load(),f.customer(),"ACCOUNTING","reference","jurisdiction",new BigDecimal("100"),new BigDecimal("10"),"EUR",null,OffsetDateTime.parse("2026-01-12T00:00:00Z"),"accountant"),f.actor());
        var r=new GenerateInvoiceRequest("mismatch-"+UUID.randomUUID(),f.snapshot().snapshotId(),new GenerateInvoiceRequest.TaxDecision("REQUIRED","REQUIRED","Required","accounting",a.assessment().assessmentId()),List.of());
        assertEquals("CURRENCY_MISMATCH",assertThrows(ApiException.class,()->billing.generatePrimary(r,f.actor(),"test")).getCode());
    }
    @Test void threeDecimalCurrencyIsNotNarrowedByInvoiceStorage() {
        var f=fixture("KWD","100.1234");var invoice=generate(f);
        assertEquals(new BigDecimal("100.123"),invoice.subtotal());
        assertEquals(new BigDecimal("100.123"),jdbc.queryForObject("select subtotal_amount from invoices where id=?",BigDecimal.class,invoice.invoiceId()));
        assertEquals(new BigDecimal("100.123"),jdbc.queryForObject("select amount_amount from invoice_line_items where invoice_id=?",BigDecimal.class,invoice.invoiceId()));
    }
    @Test void issueAndDraftRelinkAreIdempotentAndIssuedHistoryCannotMutate() {
        var f=fixture("USD","100");var invoice=generate(f);var p=previews.preview(f.load(),f.preview(),"correction");
        var next=snapshots.accept(f.load(),new RatingAcceptRequest("correction-"+UUID.randomUUID(),f.preview(),p.inputHash(),p.resultHash(),f.snapshot().snapshotId(),"ACCOUNTING_RERATE","Explicit acceptance correction"),f.actor(),"correction");
        var r=new GenerateInvoiceRequest("regenerate-"+UUID.randomUUID(),next.snapshotId(),noTax(f,"unused").taxDecision(),List.of());
        var regenerated=billing.regenerateDraft(invoice.invoiceId(),invoice.snapshotId(),r,f.actor());
        assertEquals(next.snapshotId(),regenerated.snapshotId());assertEquals(regenerated,billing.regenerateDraft(invoice.invoiceId(),invoice.snapshotId(),r,f.actor()));
        String key="issue-"+UUID.randomUUID();var issued=billing.issue(invoice.invoiceId(),key,f.actor());assertEquals("ISSUED",issued.status());
        assertEquals(issued,billing.issue(invoice.invoiceId(),key,f.actor()));
        assertEquals(regenerated,billing.regenerateDraft(invoice.invoiceId(),invoice.snapshotId(),r,f.actor()));
        // A different command cannot update issued history; the original successful regenerate command still replays safely.
        var changed=new GenerateInvoiceRequest("after-issue-"+UUID.randomUUID(),next.snapshotId(),r.taxDecision(),List.of());
        assertThrows(ApiException.class,()->billing.regenerateDraft(invoice.invoiceId(),next.snapshotId(),changed,f.actor()));
        assertThrows(DataAccessException.class,()->jdbc.update("update invoices set subtotal_amount=1 where id=?",invoice.invoiceId()));
        assertThrows(DataAccessException.class,()->jdbc.update("update invoice_line_items set amount_amount=1 where invoice_id=?",invoice.invoiceId()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from invoices where id=?",invoice.invoiceId()));
    }
    @Test void concurrentSameKeyReturnsOneOutcomeAndDifferentKeysCannotDuplicatePrimary() throws Exception {
        var f=fixture("USD","100");var r=noTax(f,"concurrent-"+UUID.randomUUID());var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<BillingInvoice> call=()->{start.await();return billing.generatePrimary(r,f.actor(),"concurrent");};
            var one=pool.submit(call);var two=pool.submit(call);start.countDown();assertEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("select count(*) from invoices where load_id=?",Integer.class,f.load()));
        } finally { pool.shutdownNow(); }
        assertEquals("INVOICE_PRIMARY_EXISTS",assertThrows(ApiException.class,()->generate(f)).getCode());
    }
    @Test void billingApiDerivesActorAndRejectsCrossTenantIdsAndUnauthorizedRoles() throws Exception {
        var f=fixture("USD","100");var request=noTax(f,"api-"+UUID.randomUUID());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/invoices/billing/primary")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER"))
            .contentType("application/json").content(json.writeValueAsString(request)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/invoices/billing/primary")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("ACCOUNTANT"))
            .contentType("application/json").content(json.writeValueAsString(request)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.actor").value(f.actor().toString()));
        assertThrows(ApiException.class,()->billing.get(UUID.randomUUID()));
        assertThrows(ApiException.class,()->billing.generatePrimary(new GenerateInvoiceRequest("foreign-"+UUID.randomUUID(),UUID.randomUUID(),request.taxDecision(),List.of()),f.actor(),"test"));
    }
    @Test void multiplePartialCreditsProvideExplicitFullReversalEvidenceForRebill() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var r=credit(f,original,"40");
        var first=billing.credit(original.invoiceId(),r,f.actor());assertEquals(first,billing.credit(original.invoiceId(),r,f.actor()));
        assertEquals(-1,first.economicSign());assertEquals(new BigDecimal("40"),first.subtotal());first=issue(f,first);
        var second=issue(f,billing.credit(original.invoiceId(),credit(f,original,"60"),f.actor()));
        var next=correction(f,List.of());var command=new BillingCorrectionRequests.Rebill(generation(f,next.snapshotId()),List.of(second.invoiceId(),first.invoiceId()),"FULL_REPLACEMENT","Full economic reversal via two credits");
        var replacement=billing.rebill(original.invoiceId(),command,f.actor());assertEquals("REBILL",replacement.purpose());assertEquals(original.invoiceId(),replacement.parentInvoiceId());
        assertEquals(original.billingChainId(),replacement.billingChainId());assertEquals(next.snapshotId(),replacement.snapshotId());
        assertEquals(replacement,billing.rebill(original.invoiceId(),command,f.actor()));
        assertEquals(2,jdbc.queryForObject("select count(*) from invoice_rebill_credit_evidence where rebill_invoice_id=?",Integer.class,replacement.invoiceId()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from invoice_rebill_credit_evidence where rebill_invoice_id=?",replacement.invoiceId()));
        issue(f,replacement);
        assertEquals(0,new BigDecimal("100").compareTo(jdbc.queryForObject("select sum(subtotal_amount*economic_sign) from invoices where billing_chain_id=? and status in ('ISSUED','SENT','PARTIALLY_PAID','PAID')",BigDecimal.class,original.invoiceId())));
        assertEquals(new BigDecimal("100.00"),billing.get(original.invoiceId()).subtotal());
        assertEquals(new BigDecimal("100.00"),revenues.calculateLoadRevenue(f.load(),"USD").subtotalRevenue());
        assertEquals(new BigDecimal("100.00"),revenues.calculateCustomerBalance(f.customer(),"USD").totalInvoiced());
        assertEquals(new BigDecimal("100.00"),profitability.getLoadFinancialSummary(f.load()).actualInvoicedRevenue());
    }
    @Test void overCreditAndIncompleteOrDraftRebillEvidenceAreRejected() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var partial=issue(f,billing.credit(original.invoiceId(),credit(f,original,"40"),f.actor()));
        assertEquals("INVOICE_OVER_CREDIT",assertThrows(ApiException.class,()->billing.credit(original.invoiceId(),credit(f,original,"61"),f.actor())).getCode());
        var next=correction(f,List.of());
        assertEquals("INVOICE_FULL_CREDIT_REQUIRED",assertThrows(ApiException.class,()->billing.rebill(original.invoiceId(),new BillingCorrectionRequests.Rebill(generation(f,next.snapshotId()),List.of(partial.invoiceId()),"REBILL","Incomplete reversal"),f.actor())).getCode());
        var remaining=billing.credit(original.invoiceId(),credit(f,original,"60"),f.actor());
        assertEquals("INVOICE_FULL_CREDIT_REQUIRED",assertThrows(ApiException.class,()->billing.rebill(original.invoiceId(),new BillingCorrectionRequests.Rebill(generation(f,next.snapshotId()),List.of(partial.invoiceId(),remaining.invoiceId()),"REBILL","Draft credit is not economic reversal"),f.actor())).getCode());
        assertThrows(DataAccessException.class,()->jdbc.update("update invoice_line_items set amount_amount=61 where invoice_id=?",remaining.invoiceId()));
    }
    @Test void supplementalBillsOnlyNewApprovedPositiveAccessorialEvent() {
        var f=fixture("USD","100");var original=issue(f,generate(f));UUID charge=UUID.randomUUID();
        jdbc.update("insert into accessorial_charges(id,load_id,type,status,customer_amount,currency,approved_by,approved_at) values (?,?,'LUMPER','APPROVED',20,'USD',?,now())",charge,f.load(),f.actor());
        var next=correction(f,List.of(charge));var command=new BillingCorrectionRequests.Supplemental(generation(f,next.snapshotId()),List.of(charge),"LATE_LUMPER","Approved incremental event");
        var extra=billing.supplemental(original.invoiceId(),command,f.actor());assertEquals(new BigDecimal("20.00"),extra.subtotal());
        assertEquals(1,extra.lines().size());assertEquals("ACCESSORIAL",extra.lines().getFirst().componentType());
        assertEquals(extra,billing.supplemental(original.invoiceId(),command,f.actor()));
        assertEquals("INVOICE_CHARGE_ALREADY_BILLED",assertThrows(ApiException.class,()->billing.supplemental(original.invoiceId(),new BillingCorrectionRequests.Supplemental(generation(f,next.snapshotId()),List.of(charge),"OTHER","Cannot duplicate event"),f.actor())).getCode());
        assertEquals("INVOICE_INCREMENTAL_CHARGE_REQUIRED",assertThrows(ApiException.class,()->billing.supplemental(original.invoiceId(),new BillingCorrectionRequests.Supplemental(generation(f,next.snapshotId()),List.of(f.snapshot().calculation().inputs().rule().ruleId()),"OTHER","Cannot duplicate linehaul"),f.actor())).getCode());
        issue(f,extra);assertEquals(0,new BigDecimal("120").compareTo(jdbc.queryForObject("select sum(subtotal_amount*economic_sign) from invoices where billing_chain_id=?",BigDecimal.class,original.invoiceId())));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from invoice_charge_claims where charge_id=?",charge));
    }
    @Test void concurrentDifferentKeysCannotOverCreditOriginal() throws Exception {
        var f=fixture("USD","100");var original=issue(f,generate(f));var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Object> call=()->{start.await();try{return billing.credit(original.invoiceId(),credit(f,original,"60"),f.actor());}catch(ApiException e){return e.getCode();}};
            var a=pool.submit(call);var b=pool.submit(call);start.countDown();var results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(BillingInvoice.class::isInstance).count());assertTrue(results.contains("INVOICE_OVER_CREDIT"));
            assertEquals(0,new BigDecimal("60").compareTo(jdbc.queryForObject("select sum(subtotal_amount) from invoices where parent_invoice_id=?",BigDecimal.class,original.invoiceId())));
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDifferentKeysCannotCreateSecondPrimaryBusinessIdentity() throws Exception {
        var f=fixture("USD","100");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Object> call=()->{start.await();try{return generate(f);}catch(ApiException e){return e.getCode();}};
            var a=pool.submit(call);var b=pool.submit(call);start.countDown();var results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(BillingInvoice.class::isInstance).count());assertTrue(results.contains("INVOICE_PRIMARY_EXISTS"));
        } finally {pool.shutdownNow();}
    }
    @Test void issuedLineCannotBeMovedToAnotherDraftAndForeignCreditLineIsRejected() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var other=fixture("USD","50");var draft=generate(other);
        assertThrows(DataAccessException.class,()->jdbc.update("update invoice_line_items set invoice_id=? where id=?",draft.invoiceId(),original.lines().getFirst().lineId()));
        var r=new BillingCorrectionRequests.Credit("foreign-line-"+UUID.randomUUID(),List.of(new BillingCorrectionRequests.CreditLine(UUID.randomUUID(),BigDecimal.ONE,BigDecimal.ZERO,null)),noTax(f,"unused").taxDecision(),"CREDIT","Unknown tenant line");
        assertEquals("INVOICE_CREDIT_LINE_REQUIRED",assertThrows(ApiException.class,()->billing.credit(original.invoiceId(),r,f.actor())).getCode());
        assertThrows(ApiException.class,()->billing.credit(UUID.randomUUID(),credit(f,original,"1"),f.actor()));
    }
    @Test void staleRevenueRejectsApproveAndLockUntilExplicitRecalculationAndReapproval() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"NET_ELIGIBLE_REVENUE");
        UUID oldSnapshot=jdbc.queryForObject("select calculation_snapshot_id from settlements where id=?",UUID.class,settlement.id());
        driverPay.transition(settlement.id(),"IN_REVIEW",f.actor());issue(f,billing.credit(original.invoiceId(),credit(f,original,"20"),f.actor()));
        assertEquals("SETTLEMENT_REVENUE_BASIS_STALE",assertThrows(ApiException.class,()->driverPay.transition(settlement.id(),"APPROVED",f.actor())).getCode());
        var command=new com.company.logicstic.service.payroll.SettlementRevenueService.Recalculate("recalc-"+UUID.randomUUID(),oldSnapshot,"BILLING_DRIFT","Accounting recalculates before approval");
        var updated=settlementRevenue.recalculate(settlement.id(),command,f.actor());assertEquals(new BigDecimal("20.00"),updated.grossEarnings());assertEquals("CALCULATED",updated.status());
        assertEquals(updated,settlementRevenue.recalculate(settlement.id(),command,f.actor()));
        assertEquals(1,jdbc.queryForObject("select count(*) from calculation_snapshots where id=?",Integer.class,oldSnapshot));
        driverPay.transition(settlement.id(),"IN_REVIEW",f.actor());driverPay.transition(settlement.id(),"APPROVED",f.actor());
        issue(f,billing.credit(original.invoiceId(),credit(f,original,"20"),f.actor()));
        assertEquals("SETTLEMENT_REVENUE_BASIS_STALE",assertThrows(ApiException.class,()->driverPay.transition(settlement.id(),"LOCKED",f.actor())).getCode());
        UUID next=jdbc.queryForObject("select calculation_snapshot_id from settlements where id=?",UUID.class,settlement.id());
        settlementRevenue.recalculate(settlement.id(),new com.company.logicstic.service.payroll.SettlementRevenueService.Recalculate("recalc-"+UUID.randomUUID(),next,"BILLING_DRIFT","Repeat approval after correction"),f.actor());
        finalizeSettlement(f,settlement.id());assertEquals(new BigDecimal("15.00"),driverPay.get(settlement.id()).settlementNet());
        assertThrows(ApiException.class,()->settlementRevenue.recalculate(settlement.id(),new com.company.logicstic.service.payroll.SettlementRevenueService.Recalculate("locked-"+UUID.randomUUID(),next,"OTHER","Never reopen locked history"),f.actor()));
    }
    @Test void issuingCreditAfterLockCreatesAuditedAdjustmentWithoutChangingParent() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"NET_ELIGIBLE_REVENUE");finalizeSettlement(f,settlement.id());
        var run=lockedPayroll(f,settlement);String payslip=jdbc.queryForObject("select row_to_json(p)::text from payslips p where payroll_run_item_id=?",String.class,run.items().getFirst().id());
        UUID snapshot=jdbc.queryForObject("select calculation_snapshot_id from settlements where id=?",UUID.class,settlement.id());
        var credit=issue(f,billing.credit(original.invoiceId(),credit(f,original,"20"),f.actor()));
        var impact=settlementRevenue.adjust(settlement.id(),new com.company.logicstic.service.payroll.SettlementRevenueService.Adjustment("explicit-retry-"+UUID.randomUUID(),credit.invoiceId(),"ACCOUNTING_RETRY","Retry existing economic effect"),f.actor());
        assertEquals(0,new BigDecimal("-20").compareTo(impact.economicDelta()));assertEquals(0,new BigDecimal("-5").compareTo(impact.payDelta()));
        assertNotNull(impact.adjustmentSettlementId());var child=driverPay.get(impact.adjustmentSettlementId());assertEquals("ADJUSTMENT",child.settlementType());assertEquals("CALCULATED",child.status());
        assertEquals(new BigDecimal("-5.00"),child.settlementNet());assertEquals(settlement.id(),child.parentSettlementId());
        assertEquals("LOCKED",driverPay.get(settlement.id()).status());assertEquals(new BigDecimal("25.00"),driverPay.get(settlement.id()).settlementNet());
        assertEquals(snapshot,jdbc.queryForObject("select calculation_snapshot_id from settlements where id=?",UUID.class,settlement.id()));
        assertEquals(1,jdbc.queryForObject("select count(*) from settlement_billing_adjustments where original_settlement_id=?",Integer.class,settlement.id()));
        assertEquals(LocalDate.of(2026,1,15),jdbc.queryForObject("select earning_date from settlement_billing_adjustments where original_settlement_id=?",LocalDate.class,settlement.id()));
        assertThrows(DataAccessException.class,()->jdbc.update("update settlement_billing_adjustments set pay_delta=1 where original_settlement_id=?",settlement.id()));
        finalizeSettlement(f,child.id());assertEquals(new BigDecimal("25.00"),driverPay.get(settlement.id()).settlementNet());
        assertEquals(run,payroll.get(run.id()));assertEquals(payslip,jdbc.queryForObject("select row_to_json(p)::text from payslips p where payroll_run_item_id=?",String.class,run.items().getFirst().id()));
        assertThrows(DataAccessException.class,()->jdbc.update("update calculation_snapshots set result_json='{}' where id=?",snapshot));
    }
    @Test void primaryOnlyPayBasisDoesNotIncludeLaterCreditAndIssueRetryDoesNotDuplicateImpact() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"PRIMARY_INVOICE_REVENUE");finalizeSettlement(f,settlement.id());
        var credit=billing.credit(original.invoiceId(),credit(f,original,"20"),f.actor());String key="issue-"+UUID.randomUUID();
        var issued=billing.issue(credit.invoiceId(),key,f.actor());assertEquals(issued,billing.issue(credit.invoiceId(),key,f.actor()));
        assertEquals(new BigDecimal("25.00"),driverPay.get(settlement.id()).settlementNet());
        assertEquals(0,jdbc.queryForObject("select count(*) from settlements where parent_settlement_id=?",Integer.class,settlement.id()));
        assertEquals(1,jdbc.queryForObject("select count(*) from settlement_billing_adjustments where original_settlement_id=? and pay_delta=0 and adjustment_settlement_id is null",Integer.class,settlement.id()));
    }
    @Test void concurrentIssueVersusLockEitherRejectsStaleOrCreatesOneAdjustment() throws Exception {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"NET_ELIGIBLE_REVENUE");
        driverPay.transition(settlement.id(),"IN_REVIEW",f.actor());driverPay.transition(settlement.id(),"APPROVED",f.actor());
        var credit=billing.credit(original.invoiceId(),credit(f,original,"20"),f.actor());var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var lock=pool.submit(()->{start.await();try{return driverPay.transition(settlement.id(),"LOCKED",f.actor()).status();}catch(ApiException e){return e.getCode();}});
            var issue=pool.submit(()->{start.await();return billing.issue(credit.invoiceId(),"race-"+UUID.randomUUID(),f.actor());});start.countDown();
            assertEquals("ISSUED",issue.get(20,TimeUnit.SECONDS).status());String outcome=lock.get(20,TimeUnit.SECONDS);
            if("LOCKED".equals(outcome))assertEquals(1,jdbc.queryForObject("select count(*) from settlements where parent_settlement_id=?",Integer.class,settlement.id()));
            else {assertEquals("SETTLEMENT_REVENUE_BASIS_STALE",outcome);assertEquals("APPROVED",driverPay.get(settlement.id()).status());assertEquals(0,jdbc.queryForObject("select count(*) from settlements where parent_settlement_id=?",Integer.class,settlement.id()));}
        }finally{pool.shutdownNow();}
    }
    @Test void postLockCorrectionKeepsEarningDatePolicyInsteadOfLaterInvoiceDateVersion() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"NET_ELIGIBLE_REVENUE");finalizeSettlement(f,settlement.id());
        String code=jdbc.queryForObject("select policy_code from driver_pay_policies where id=?",String.class,settlement.policyId());
        driverPolicies.newVersion(settlement.policyId(),new com.company.logicstic.dto.payroll.DriverPayPolicyRequest(code,"New version",f.actor(),"PERCENT_REVENUE",null,null,null,null,null,new BigDecimal("0.75"),null,"NET_ELIGIBLE_REVENUE",null,null,null,null,null,"USD",LocalDate.of(2026,2,1),null));
        var doc=issue(f,billing.credit(original.invoiceId(),credit(f,original,"20"),f.actor()));
        assertEquals(1,jdbc.queryForObject("select policy_version from settlement_billing_adjustments where affected_document_id=?",Integer.class,doc.invoiceId()));
        assertEquals(0,new BigDecimal("-5").compareTo(jdbc.queryForObject("select pay_delta from settlement_billing_adjustments where affected_document_id=?",BigDecimal.class,doc.invoiceId())));
    }
    @Test void fullCreditWithAuditedTaxReversesBothSubtotalAndTaxBeforeRebill() {
        var f=fixture("USD","100");var assessment=taxes.capture(new TaxAssessmentRequest(UUID.randomUUID(),f.load(),f.customer(),"ACCOUNTING","original-tax","jurisdiction",new BigDecimal("100"),new BigDecimal("10"),"USD",null,OffsetDateTime.parse("2026-01-12T00:00:00Z"),"accountant"),f.actor());
        var decision=new GenerateInvoiceRequest.TaxDecision("REQUIRED","ASSESSMENT","Explicit assessment","Accounting",assessment.assessment().assessmentId());
        var original=issue(f,billing.generatePrimary(new GenerateInvoiceRequest("tax-primary-"+UUID.randomUUID(),f.snapshot().snapshotId(),decision,List.of(new GenerateInvoiceRequest.LineTax("LINEHAUL",f.snapshot().calculation().inputs().rule().ruleId(),new BigDecimal("10")))),f.actor(),"test"));
        var credit=billing.credit(original.invoiceId(),new BillingCorrectionRequests.Credit("full-tax-credit-"+UUID.randomUUID(),List.of(new BillingCorrectionRequests.CreditLine(original.lines().getFirst().lineId(),new BigDecimal("100"),new BigDecimal("10"),BigDecimal.ONE)),decision,"FULL_CREDIT","Accounting reverses full line and assessed tax"),f.actor());issue(f,credit);
        var next=correction(f,List.of());var generation=new GenerateInvoiceRequest("rebill-tax-"+UUID.randomUUID(),next.snapshotId(),decision,List.of(new GenerateInvoiceRequest.LineTax("LINEHAUL",next.calculation().inputs().rule().ruleId(),new BigDecimal("10"))));
        var replacement=issue(f,billing.rebill(original.invoiceId(),new BillingCorrectionRequests.Rebill(generation,List.of(credit.invoiceId()),"REBILL","Full replacement after tax and subtotal reversal"),f.actor()));
        assertEquals(new BigDecimal("110.00"),replacement.total());assertEquals(0,new BigDecimal("110").compareTo(jdbc.queryForObject("select sum(total_amount*economic_sign) from invoices where billing_chain_id=?",BigDecimal.class,original.invoiceId())));
    }
    @Test void deferredRatingMethodsReturnDomainErrorAndRevenueCommandsRemainProtected() throws Exception {
        var f=fixture("USD","100");var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        // Deserialization must preserve the locked domain error, not turn deferred methods into a generic parse error.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/rating/rules")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("ACCOUNTANT"))
                .contentType("application/json").content("{\"method\":\"PER_WEIGHT\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("UNSUPPORTED_RATE_METHOD"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/driver-settlements/"+UUID.randomUUID()+"/recalculate-revenue")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER"))
                .contentType("application/json").content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void aggregateRevenueRoundingPreservesSmallEconomicDeltasWithoutFakeZeroChild() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"NET_ELIGIBLE_REVENUE");finalizeSettlement(f,settlement.id());
        for(int i=0;i<3;i++)issue(f,billing.credit(original.invoiceId(),credit(f,original,"0.01"),f.actor()));
        assertEquals(3,jdbc.queryForObject("select count(*) from settlement_billing_adjustments where original_settlement_id=?",Integer.class,settlement.id()));
        assertEquals(2,jdbc.queryForObject("select count(*) from settlement_billing_adjustments where original_settlement_id=? and pay_delta=0 and adjustment_settlement_id is null",Integer.class,settlement.id()));
        assertEquals(0,new BigDecimal("-0.01").compareTo(jdbc.queryForObject("select sum(pay_delta) from settlement_billing_adjustments where original_settlement_id=?",BigDecimal.class,settlement.id())));
        assertEquals(1,jdbc.queryForObject("select count(*) from settlements where parent_settlement_id=?",Integer.class,settlement.id()));
        assertEquals(0,jdbc.queryForObject("select count(*) from payroll_payments where payroll_run_item_id in (select id from payroll_run_items where driver_id=?)",Integer.class,f.actor()));
    }
    @Test void manualAdjustmentRetryKeyBindsInputEvenWhenAutomaticIssueAlreadyAppliedSource() {
        var f=fixture("USD","100");var original=issue(f,generate(f));var settlement=settlement(f,"NET_ELIGIBLE_REVENUE");finalizeSettlement(f,settlement.id());
        var doc=issue(f,billing.credit(original.invoiceId(),credit(f,original,"10"),f.actor()));String key="manual-"+UUID.randomUUID();
        var command=new com.company.logicstic.service.payroll.SettlementRevenueService.Adjustment(key,doc.invoiceId(),"RETRY","Explicit retry");
        var outcome=settlementRevenue.adjust(settlement.id(),command,f.actor());assertEquals(outcome,settlementRevenue.adjust(settlement.id(),command,f.actor()));
        assertEquals("SETTLEMENT_REVENUE_IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->settlementRevenue.adjust(settlement.id(),new com.company.logicstic.service.payroll.SettlementRevenueService.Adjustment(key,doc.invoiceId(),"OTHER","Different audit request"),f.actor())).getCode());
        assertEquals(1,jdbc.queryForObject("select count(*) from settlements where parent_settlement_id=?",Integer.class,settlement.id()));
    }
    @Test void physicalPostgresTenantCannotReadOrAttachNeighborFinancialFacts() {
        var f=fixture("USD","100");var invoice=generate(f);
        var a=taxes.capture(new TaxAssessmentRequest(UUID.randomUUID(),f.load(),f.customer(),"ACCOUNTING","tenant-a-source","jurisdiction",new BigDecimal("100"),BigDecimal.ZERO,"USD",null,OffsetDateTime.parse("2026-01-12T00:00:00Z"),"accountant"),f.actor());
        // DATABASE_PER_TENANT is the locked architecture. Use another real PostgreSQL DB,
        // not H2 or an unknown random UUID in the same database, to prove repository isolation.
        String database="codex_billing_isolation_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create database "+database);
        var ds=new org.postgresql.ds.PGSimpleDataSource();ds.setURL(System.getenv("TASK_DB_URL"));ds.setDatabaseName(database);
        ds.setUser(System.getenv("TASK_DB_USER"));ds.setPassword(System.getenv("TASK_DB_PASSWORD"));
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration/tenant").load();flyway.migrate();assertTrue(flyway.validateWithResult().validationSuccessful);
        var other=new JdbcTemplate(ds);
        assertTrue(new com.company.logicstic.repository.RatingSnapshotRepository(other,json).find(f.snapshot().snapshotId()).isEmpty());
        assertTrue(new com.company.logicstic.repository.TaxAssessmentRepository(other).find(a.assessment().assessmentId()).isEmpty());
        assertThrows(ApiException.class,()->new com.company.logicstic.repository.BillingRepository(other,json).current(invoice.invoiceId(),false));
        assertEquals(0,other.queryForObject("select count(*) from invoice_billing_commands",Integer.class));
        assertEquals(invoice,billing.get(invoice.invoiceId()));
        // Retain this explicitly named disposable DB with the regression diagnostics; do not drop user databases.
    }
}
