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
    @Autowired com.company.logicstic.service.payroll.DriverPayEngine driverPay;
    @Autowired com.company.logicstic.service.profitability.ProfitabilityService profitability;
    @Autowired com.company.logicstic.service.payroll.PayrollCalculationService payroll;
    @Autowired com.company.logicstic.service.payroll.policy.PayrollConfigurationService payrollConfiguration;
    @Autowired com.company.logicstic.service.payroll.policy.PayrollPolicyResolver payrollPolicyResolver;
    @Autowired com.company.logicstic.service.payroll.PayrollWorkflowService payrollWorkflow;
    @Autowired com.company.logicstic.service.payroll.PayslipService payslips;
    @Autowired com.company.logicstic.service.payroll.payment.PayrollPaymentSchedulingService paymentScheduling;
    @Autowired com.company.logicstic.service.payroll.payment.PayrollPaymentDispatchService paymentDispatch;
    @Autowired com.company.logicstic.service.payroll.payment.PayrollCallbackService paymentCallbacks;
    @Autowired com.company.logicstic.service.payroll.payment.PayrollManualBankReconciliationService bankReconciliation;
    @Autowired com.company.logicstic.service.payroll.PayrollNoPaymentDispositionService noPaymentDisposition;

    private com.company.logicstic.service.payroll.payment.VerifiedPayrollPaymentEvent paymentEvent(
            com.company.logicstic.dto.payroll.PayrollPaymentView payment,String outcome,String amount,String currency) {
        return new com.company.logicstic.service.payroll.payment.VerifiedPayrollPaymentEvent(UUID.randomUUID().toString(),payment.id(),outcome,
                new java.math.BigDecimal(amount),currency,"FIXTURE-"+payment.id(),java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }
    private com.company.logicstic.dto.payroll.PayrollPaymentEventView callback(com.company.logicstic.service.payroll.payment.VerifiedPayrollPaymentEvent event) {
        return paymentCallbacks.receive("TEST_ONLY_ASYNC",tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(event),
                java.util.Map.of("x-fixture-signature","SIGNED_FIXTURE"));
    }
    private com.company.logicstic.dto.payroll.PayrollRunView lockedPayroll(PayrollFixture f,com.company.logicstic.service.payroll.domain.PayrollJurisdiction j) {
        payrollProfile(f.driver(),j);var run=payroll.calculate(payrollRequest(f));
        payrollWorkflow.transition(run.id(),"IN_REVIEW",f.driver());payrollWorkflow.transition(run.id(),"APPROVED",f.driver());
        return payrollWorkflow.transition(run.id(),"LOCKED",f.driver());
    }
    private com.company.logicstic.dto.payroll.PayrollPaymentView scheduledProviderPayment(UUID item,UUID actor) {
        var p=paymentScheduling.schedule(item,new com.company.logicstic.dto.payroll.SchedulePayrollPaymentRequest(
                UUID.randomUUID().toString(),"BANK_TRANSFER","TEST_ONLY_ASYNC","fixture-account"),actor);
        paymentDispatch.dispatch(p.id());return paymentScheduling.list(item).getLast();
    }

    @Test void concurrentVerifiedSuccessPaysOnlyItsItemAndRunWaitsForEveryRequiredItem() throws Exception {
        var f=payrollFixture();var otherSource=fixture();var date=java.time.LocalDate.of(2026,1,1);
        payPolicies.create(payPolicyRequest(UUID.randomUUID().toString(),otherSource.actor(),"1",date,null));addAssignedTrip(otherSource,"2026-01-15T12:00:00Z");
        var s=driverPay.calculate(otherSource.actor(),f.period());finalizeSettlement(s.id(),otherSource.actor());
        var other=new PayrollFixture(otherSource.actor(),f.period(),s.id(),otherSource.email());var j=payrollJurisdiction("VN");
        payrollProfile(f.driver(),j);payrollProfile(other.driver(),j);payrollPolicy(UUID.randomUUID().toString(),j,date,null,"TEST_ONLY_FIXED");
        var template=payrollRequest(f);var request=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(template.idempotencyKey(),f.period(),"USD",
                template.effectiveDate(),java.util.List.of(f.settlement(),other.settlement()),null,null,null);
        var run=payroll.calculate(request);payrollWorkflow.transition(run.id(),"IN_REVIEW",f.driver());payrollWorkflow.transition(run.id(),"APPROVED",f.driver());payrollWorkflow.transition(run.id(),"LOCKED",f.driver());
        var firstItem=run.items().stream().filter(i -> i.driverId().equals(f.driver())).findFirst().orElseThrow();
        var otherItem=run.items().stream().filter(i -> i.driverId().equals(other.driver())).findFirst().orElseThrow();
        var first=scheduledProviderPayment(firstItem.id(),f.driver());var second=scheduledProviderPayment(otherItem.id(),f.driver());
        int costs=jdbc.queryForObject("select count(*) from shipment_costs where driver_id in (?,?)",Integer.class,f.driver(),other.driver());
        var event=paymentEvent(first,"SUCCEEDED","90","USD");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> callback(event));var b=pool.submit(() -> callback(event));
            var one=a.get(20,TimeUnit.SECONDS);var two=b.get(20,TimeUnit.SECONDS);assertEquals(one.eventId(),two.eventId());assertEquals("SUCCEEDED",two.payment().status());
        }
        assertEquals("PAYMENT_SCHEDULED",payroll.get(run.id()).status());assertNull(payroll.get(run.id()).completedAt());
        assertEquals("PAID",jdbc.queryForObject("select status from settlements where id=?",String.class,f.settlement()));
        assertEquals("PAYMENT_SCHEDULED",jdbc.queryForObject("select status from settlements where id=?",String.class,other.settlement()));
        callback(paymentEvent(second,"SUCCEEDED","90","USD"));assertEquals("COMPLETED",payroll.get(run.id()).status());assertNotNull(payroll.get(run.id()).completedAt());
        assertEquals("PAYMENT_PROVIDER",payroll.get(run.id()).completionSource());assertNull(payroll.get(run.id()).completedBy());
        assertTrue(payroll.get(run.id()).items().stream().allMatch(i -> i.status().equals("PAID")));
        assertEquals(costs,jdbc.queryForObject("select count(*) from shipment_costs where driver_id in (?,?)",Integer.class,f.driver(),other.driver()));
        assertEquals(1,jdbc.queryForObject("select count(*) from payroll_payment_events where source_key=?",Integer.class,event.eventId()));
    }

    @Test void zeroNetDispositionIsAuditedAndRunCompletesOnlyAfterEveryOtherItemIsPaid() throws Exception {
        var payable=payrollFixture();var noPay=fixture();var date=java.time.LocalDate.of(2026,1,1);
        var noPayPolicy=payPolicies.create(payPolicyRequest(UUID.randomUUID().toString(),noPay.actor(),"1",date,null));
        var zeroSettlement=zeroLockedSettlement(noPay.actor(),payable.period(),noPayPolicy.getId());
        var jurisdiction=payrollJurisdiction("VN");payrollProfile(payable.driver(),jurisdiction);payrollProfile(noPay.actor(),jurisdiction);
        payrollPolicy(UUID.randomUUID().toString(),jurisdiction,date,null,"TEST_ONLY_FIXED");
        var template=payrollRequest(payable);
        var request=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(template.idempotencyKey(),payable.period(),"USD",
                template.effectiveDate(),java.util.List.of(payable.settlement(),zeroSettlement),null,null,null);
        var calculated=payroll.calculate(request);
        var payableItem=calculated.items().stream().filter(i -> i.driverId().equals(payable.driver())).findFirst().orElseThrow();
        var noPayItem=calculated.items().stream().filter(i -> i.driverId().equals(noPay.actor())).findFirst().orElseThrow();
        assertEquals(java.math.BigDecimal.ZERO.compareTo(noPayItem.netAmount()),0);
        payrollWorkflow.transition(calculated.id(),"IN_REVIEW",payable.driver());
        payrollWorkflow.transition(calculated.id(),"APPROVED",payable.driver());
        payrollWorkflow.transition(calculated.id(),"LOCKED",payable.driver());

        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var body="{\"reasonCode\":\"ZERO_NET_PAY\",\"reason\":\"No net remuneration due for this payroll period\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/items/"+noPayItem.id()+"/no-payment-required")
                        .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/items/"+noPayItem.id()+"/no-payment-required")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(payable.email()).roles("ACCOUNTANT"))
                        .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("LOCKED",tools.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("status").asText());
        var disposition=payroll.get(calculated.id()).items().stream().filter(i -> i.id().equals(noPayItem.id())).findFirst().orElseThrow();
        assertEquals("NO_PAYMENT_REQUIRED",disposition.status());assertEquals("ZERO_NET_PAY",disposition.noPaymentReasonCode());
        assertEquals(payable.driver(),disposition.noPaymentRequiredBy());assertNotNull(disposition.noPaymentRequiredAt());
        assertEquals("LOCKED",jdbc.queryForObject("select status from settlements where id=?",String.class,zeroSettlement));
        assertEquals(0,jdbc.queryForObject("select count(*) from payroll_payments where payroll_run_item_id=?",Integer.class,noPayItem.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("""
                update payroll_runs set status='COMPLETED',completed_at=now(),completed_by=?,completion_source='NO_PAYMENT_DISPOSITION' where id=?
                """,payable.driver(),calculated.id()));
        var replay=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/items/"+noPayItem.id()+"/no-payment-required")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(payable.email()).roles("ACCOUNTANT"))
                        .contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("LOCKED",tools.jackson.databind.json.JsonMapper.builder().build().readTree(replay).get("status").asText());
        var conflict="{\"reasonCode\":\"MANUAL_ADJUSTMENT_ZERO_BALANCE\",\"reason\":\"Different reason\"}";
        assertEquals("PAYROLL_NO_PAYMENT_DISPOSITION_CONFLICT",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> noPaymentDisposition.markNoPaymentRequired(noPayItem.id(),new com.company.logicstic.dto.payroll.NoPaymentRequiredRequest(
                        "MANUAL_ADJUSTMENT_ZERO_BALANCE","Different reason"),payable.driver())).getCode());

        var payment=scheduledProviderPayment(payableItem.id(),payable.driver());
        callback(paymentEvent(payment,"SUCCEEDED","90","USD"));
        var completed=payroll.get(calculated.id());
        assertEquals("COMPLETED",completed.status());assertNotNull(completed.completedAt());
        assertEquals("PAYMENT_PROVIDER",completed.completionSource());assertNull(completed.completedBy());
        assertTrue(completed.items().stream().allMatch(i -> java.util.Set.of("PAID","NO_PAYMENT_REQUIRED").contains(i.status())));
    }

    @Test void verifiedFailureAllowsNewAttemptButLateSuccessCreatesUnresolvedCase() {
        var f=payrollFixture();var j=payrollJurisdiction("US");payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=lockedPayroll(f,j);var item=run.items().getFirst();var first=scheduledProviderPayment(item.id(),f.driver());
        assertEquals("FAILED",callback(paymentEvent(first,"FAILED","90","USD")).payment().status());
        assertEquals("PAYMENT_FAILED",payroll.get(run.id()).items().getFirst().status());
        var retry=scheduledProviderPayment(item.id(),f.driver());assertEquals(2,retry.attemptNumber());
        var late=callback(paymentEvent(first,"SUCCEEDED","90","USD"));assertEquals("RECONCILIATION_REQUIRED",late.status());
        assertEquals("FAILED",late.payment().status());assertEquals("PAYMENT_SCHEDULED",payroll.get(run.id()).status());
        assertEquals("PAYMENT_RECONCILIATION_REQUIRED",assertThrows(com.company.logicstic.exception.BadRequestException.class,() -> paymentDispatch.dispatch(retry.id())).getCode());
        assertEquals("RECONCILIATION_REQUIRED",callback(paymentEvent(retry,"SUCCEEDED","90","USD")).status());
        assertEquals("PAYMENT_PENDING",payroll.get(run.id()).items().getFirst().status());
    }

    @Test void callbackRejectsWrongMoneyUnsignedBodyAndInputDriftAndDatabaseRequiresSuccessEvidence() {
        var f=payrollFixture();var j=payrollJurisdiction("VN");payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=lockedPayroll(f,j);var p=scheduledProviderPayment(run.items().getFirst().id(),f.driver());
        var badAmount=paymentEvent(p,"SUCCEEDED","89","USD");var rejected=callback(badAmount);
        assertEquals("REJECTED",rejected.status());assertEquals("PAYMENT_AMOUNT_MISMATCH",rejected.reason());assertEquals("PROCESSING",rejected.payment().status());
        assertEquals("PAYMENT_CURRENCY_MISMATCH",callback(paymentEvent(p,"SUCCEEDED","90","EUR")).reason());
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var good=paymentEvent(p,"SUCCEEDED","90","USD");
        assertThrows(com.company.logicstic.exception.ForbiddenException.class,() -> paymentCallbacks.receive("TEST_ONLY_ASYNC",mapper.writeValueAsString(good),java.util.Map.of()));
        assertThrows(com.company.logicstic.exception.ForbiddenException.class,() -> paymentCallbacks.receive("NO_VERIFIER",mapper.writeValueAsString(good),java.util.Map.of()));
        var drift=new com.company.logicstic.service.payroll.payment.VerifiedPayrollPaymentEvent(badAmount.eventId(),p.id(),"SUCCEEDED",new java.math.BigDecimal("90"),"USD",badAmount.providerReference(),badAmount.occurredAt());
        assertEquals("PAYMENT_CALLBACK_IDEMPOTENCY_CONFLICT",assertThrows(com.company.logicstic.exception.BadRequestException.class,() -> callback(drift)).getCode());
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_payments set status='SUCCEEDED' where id=?",p.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_run_items set status='PAID' where id=?",run.items().getFirst().id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update settlements set status='PAID' where id=?",f.settlement()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_payment_events set amount=90 where id=?",rejected.eventId()));
        callback(good);assertEquals("COMPLETED",payroll.get(run.id()).status());
    }

    @Test void manualBankEvidenceResolvesLateSuccessCaseIdempotentlyAndKeepsAudit() throws Exception {
        var f=payrollFixture();var j=payrollJurisdiction("VN");
        payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=lockedPayroll(f,j);var payment=scheduledProviderPayment(run.items().getFirst().id(),f.driver());
        assertEquals("FAILED",callback(paymentEvent(payment,"FAILED","90","USD")).payment().status());
        var openCase=callback(paymentEvent(payment,"SUCCEEDED","90","USD"));
        assertEquals("RECONCILIATION_REQUIRED",openCase.status());
        assertTrue(bankReconciliation.openCases(0,100).getContent().stream().anyMatch(c -> c.caseEventId().equals(openCase.eventId())));

        var request=new com.company.logicstic.dto.payroll.ManualPayrollBankReconciliationRequest(
                "manual-bank-idem-"+UUID.randomUUID(),openCase.eventId(),"bank-main","bank-tx-"+UUID.randomUUID(),
                new java.math.BigDecimal("90.00"),"usd","SUCCEEDED","fixture-account",
                "statement:2026-01-15:row-17","Bank statement confirms this payment",java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        com.company.logicstic.dto.payroll.PayrollPaymentEventView reconciled;
        com.company.logicstic.dto.payroll.PayrollPaymentEventView replay;
        try {
            var first=pool.submit(() -> bankReconciliation.reconcile(payment.id(),request,f.driver()));
            var second=pool.submit(() -> bankReconciliation.reconcile(payment.id(),request,f.driver()));
            reconciled=first.get();replay=second.get();
        } finally { pool.shutdownNow(); }

        assertEquals("APPLIED",reconciled.status());assertEquals(reconciled.eventId(),replay.eventId());
        assertEquals("SUCCEEDED",reconciled.payment().status());
        assertEquals("COMPLETED",payroll.get(run.id()).status());
        assertEquals("PAID",jdbc.queryForObject("select status from settlements where id=?",String.class,f.settlement()));
        assertEquals(1,jdbc.queryForObject("select count(*) from payroll_payment_events where source_type='BANK' and actor_id=? and resolves_event_id=? and provider_reference=?",Integer.class,
                f.driver(),openCase.eventId(),request.transactionReference()));
        assertEquals("fixture-account",jdbc.queryForObject("select verification_json->>'counterpartyReference' from payroll_payment_events where id=?",String.class,reconciled.eventId()));
        assertEquals("statement:2026-01-15:row-17",jdbc.queryForObject("select verification_json->>'evidenceReference' from payroll_payment_events where id=?",String.class,reconciled.eventId()));
        assertFalse(bankReconciliation.openCases(0,100).getContent().stream().anyMatch(c -> c.caseEventId().equals(openCase.eventId())));
        assertEquals("BANK_TRANSACTION_ALREADY_CLAIMED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> bankReconciliation.reconcile(payment.id(),new com.company.logicstic.dto.payroll.ManualPayrollBankReconciliationRequest(
                        "another-key",openCase.eventId(),request.bankSource(),request.transactionReference(),request.amount(),request.currency(),request.outcome(),
                        request.counterpartyReference(),request.evidenceReference(),request.reason(),request.occurredAt()),f.driver())).getCode());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update payroll_payments set status='FAILED' where id=?",payment.id()));

        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payroll/reconciliation-cases"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payroll/reconciliation-cases")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("ACCOUNTANT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test void callbackEndpointIsPublicOnlyThroughConfiguredVerifier() throws Exception {
        var f=payrollFixture();var j=payrollJurisdiction("US");payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=lockedPayroll(f,j);var p=scheduledProviderPayment(run.items().getFirst().id(),f.driver());
        var body=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(paymentEvent(p,"SUCCEEDED","90","USD"));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/provider-callbacks/TEST_ONLY_ASYNC").contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/provider-callbacks/TEST_ONLY_ASYNC")
                        .header("x-fixture-signature","SIGNED_FIXTURE").contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals("COMPLETED",payroll.get(run.id()).status());
    }

    @Test void concurrentPaymentSchedulingAndDispatchPersistOneAttemptWithoutPayingPayroll() throws Exception {
        var f=payrollFixture();var j=payrollJurisdiction("VN");payrollProfile(f.driver(),j);
        payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=payroll.calculate(payrollRequest(f));payrollWorkflow.transition(run.id(),"IN_REVIEW",f.driver());
        payrollWorkflow.transition(run.id(),"APPROVED",f.driver());payrollWorkflow.transition(run.id(),"LOCKED",f.driver());
        var item=run.items().getFirst();
        var request=new com.company.logicstic.dto.payroll.SchedulePayrollPaymentRequest(UUID.randomUUID().toString(),"BANK_TRANSFER","TEST_ONLY_ASYNC","fixture-account");
        com.company.logicstic.dto.payroll.PayrollPaymentView scheduled;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> paymentScheduling.schedule(item.id(),request,f.driver()));
            var b=pool.submit(() -> paymentScheduling.schedule(item.id(),request,f.driver()));
            scheduled=a.get(20,TimeUnit.SECONDS);assertEquals(scheduled.id(),b.get(20,TimeUnit.SECONDS).id());
        }
        assertEquals("SCHEDULED",scheduled.status());assertEquals(1,scheduled.attemptNumber());
        assertEquals(0,new java.math.BigDecimal("90").compareTo(scheduled.amount()));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> paymentDispatch.dispatch(scheduled.id()));var b=pool.submit(() -> paymentDispatch.dispatch(scheduled.id()));
            assertEquals("PROCESSING",a.get(20,TimeUnit.SECONDS).status());assertEquals("PROCESSING",b.get(20,TimeUnit.SECONDS).status());
        }
        assertEquals("PAYMENT_SCHEDULED",payroll.get(run.id()).status());assertNull(payroll.get(run.id()).completedAt());
        assertEquals("PAYMENT_PENDING",payroll.get(run.id()).items().getFirst().status());
        assertEquals("PAYMENT_SCHEDULED",jdbc.queryForObject("select status from settlements where id=?",String.class,f.settlement()));
        assertEquals(1,jdbc.queryForObject("select count(*) from payroll_payments where payroll_run_item_id=?",Integer.class,item.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_payments set amount=amount+1 where id=?",scheduled.id()));
        assertEquals("PAYMENT_IDEMPOTENCY_CONFLICT",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> paymentScheduling.schedule(item.id(),new com.company.logicstic.dto.payroll.SchedulePayrollPaymentRequest(request.idempotencyKey(),"BANK_TRANSFER","TEST_ONLY_ASYNC","changed-account"),f.driver())).getCode());
    }

    @Test void missingProviderAndUnknownSubmissionNeverBecomeFailedOrPaid() {
        var f=payrollFixture();var j=payrollJurisdiction("US");payrollProfile(f.driver(),j);
        payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=payroll.calculate(payrollRequest(f));payrollWorkflow.transition(run.id(),"IN_REVIEW",f.driver());
        payrollWorkflow.transition(run.id(),"APPROVED",f.driver());payrollWorkflow.transition(run.id(),"LOCKED",f.driver());
        var scheduled=paymentScheduling.schedule(run.items().getFirst().id(),
                new com.company.logicstic.dto.payroll.SchedulePayrollPaymentRequest(UUID.randomUUID().toString(),"BANK_TRANSFER","MISSING_PROVIDER","fixture-account"),f.driver());
        assertEquals("PAYMENT_PROVIDER_NOT_CONFIGURED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> paymentDispatch.dispatch(scheduled.id())).getCode());
        assertEquals("SCHEDULED",paymentScheduling.list(run.items().getFirst().id()).getFirst().status());
        var other=payrollFixture();payrollProfile(other.driver(),j);var second=payroll.calculate(payrollRequest(other));
        payrollWorkflow.transition(second.id(),"IN_REVIEW",other.driver());payrollWorkflow.transition(second.id(),"APPROVED",other.driver());payrollWorkflow.transition(second.id(),"LOCKED",other.driver());
        var unknown=paymentScheduling.schedule(second.items().getFirst().id(),
                new com.company.logicstic.dto.payroll.SchedulePayrollPaymentRequest(UUID.randomUUID().toString(),"BANK_TRANSFER","TEST_ONLY_ASYNC","UNKNOWN_OUTCOME"),other.driver());
        assertEquals("PAYMENT_SUBMISSION_OUTCOME_UNKNOWN",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> paymentDispatch.dispatch(unknown.id())).getCode());
        assertEquals("PROCESSING",paymentDispatch.dispatch(unknown.id()).status());
        assertEquals("PAYMENT_ATTEMPT_ACTIVE",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> paymentScheduling.schedule(second.items().getFirst().id(),
                        new com.company.logicstic.dto.payroll.SchedulePayrollPaymentRequest(UUID.randomUUID().toString(),"BANK_TRANSFER","TEST_ONLY_ASYNC","fixture-account"),other.driver())).getCode());
    }

    @Test void payrollLockIssuesImmutablePdfAndDriverCanOnlyReadOwnPayslip() throws Exception {
        var f=payrollFixture();var j=payrollJurisdiction("VN");payrollProfile(f.driver(),j);
        payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=payroll.calculate(payrollRequest(f));
        assertThrows(com.company.logicstic.exception.BadRequestException.class,() -> payslips.issue(run.id(),f.driver()));
        payrollWorkflow.transition(run.id(),"IN_REVIEW",f.driver());payrollWorkflow.transition(run.id(),"APPROVED",f.driver());
        jdbc.update("update employees set first_name='Nguyễn',last_name='Văn An' where id=?",f.driver());
        payrollWorkflow.transition(run.id(),"LOCKED",f.driver());
        var slip=payslips.mine(f.driver()).getFirst();var pdf=payslips.pdf(slip.id(),f.driver(),false);
        try(var parsed=org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
            assertTrue(text.contains("Nguyễn Văn An"),text);assertTrue(text.contains("90.00"),text);
        }
        assertEquals(slip.pdfSha256(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pdf)));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> payslips.issue(run.id(),f.driver()));var b=pool.submit(() -> payslips.issue(run.id(),f.driver()));
            assertEquals(slip.id(),a.get(20,TimeUnit.SECONDS).getFirst().id());assertEquals(slip.id(),b.get(20,TimeUnit.SECONDS).getFirst().id());
        }
        jdbc.update("update employees set first_name='Changed after issuance' where id=?",f.driver());
        assertArrayEquals(pdf,payslips.pdf(slip.id(),f.driver(),false));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payslips set snapshot_json='{}' where id=?",slip.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payslips set pdf_content=''::bytea where id=?",slip.id()));
        var other=fixture();var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/driver/me/payslips")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payslips/"+slip.id()+"/pdf")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payslips/"+slip.id())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(other.email()).roles("DRIVER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payslips/"+slip.id()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is4xxClientError());
        assertEquals(1,jdbc.queryForObject("select count(*) from payslips where payroll_run_item_id=?",Integer.class,slip.payrollItemId()));
        assertEquals("LOCKED",payroll.get(run.id()).status());assertEquals("LOCKED",jdbc.queryForObject("select status from settlements where id=?",String.class,f.settlement()));
    }

    @Test void concurrentPayrollLockFreezesHeaderItemsClaimsAndSnapshotsWithoutMarkingPaid() throws Exception {
        var f=payrollFixture();var j=payrollJurisdiction("VN");payrollProfile(f.driver(),j);
        payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var run=payroll.calculate(payrollRequest(f));
        assertThrows(com.company.logicstic.exception.BadRequestException.class,() -> payrollWorkflow.transition(run.id(),"LOCKED",f.driver()));
        payrollWorkflow.transition(run.id(),"IN_REVIEW",f.driver());payrollWorkflow.transition(run.id(),"APPROVED",f.driver());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> payrollWorkflow.transition(run.id(),"LOCKED",f.driver()));
            var b=pool.submit(() -> payrollWorkflow.transition(run.id(),"LOCKED",f.driver()));
            var first=a.get(20,TimeUnit.SECONDS);var second=b.get(20,TimeUnit.SECONDS);
            assertEquals(first.lockedAt().toInstant(),second.lockedAt().toInstant());assertEquals("LOCKED",first.status());assertNull(first.completedAt());
        }
        assertEquals("CALCULATED",payroll.get(run.id()).items().getFirst().status());
        assertEquals("LOCKED",jdbc.queryForObject("select status from settlements where id=?",String.class,f.settlement()));
        assertThrows(com.company.logicstic.exception.BadRequestException.class,() -> payroll.recalculate(run.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_runs set status='CALCULATED' where id=?",run.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_runs set calculation_snapshot_json='{}' where id=?",run.id()));
        var item=run.items().getFirst();
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_run_items set gross_amount=gross_amount+1 where id=?",item.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("delete from payroll_run_item_settlements where payroll_run_item_id=?",item.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update calculation_snapshots set result_json='{}' where entity_type='PAYROLL_ITEM' and entity_id=?",item.id()));
        jdbc.update("update payroll_runs set status='PAYMENT_SCHEDULED' where id=?",run.id());
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_runs set status='COMPLETED' where id=?",run.id()));
    }

    @Test void payrollWorkflowApiBlocksUnavailableTaxAndUsesAuthenticatedActor() throws Exception {
        var f=payrollFixture();payrollProfile(f.driver(),null);var unavailable=payroll.calculate(payrollRequest(f));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/runs/"+unavailable.id()+"/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("PAYROLL")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var other=payrollFixture();var j=payrollJurisdiction("US");payrollProfile(other.driver(),j);
        payrollPolicy(UUID.randomUUID().toString(),j,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var valid=payroll.calculate(payrollRequest(other));
        for(var step:java.util.List.of("submit-review","approve","lock"))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/runs/"+valid.id()+"/"+step)
                            .param("actorId",f.driver().toString())
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(other.email()).roles("PAYROLL")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(other.driver(),jdbc.queryForObject("select approved_by from payroll_runs where id=?",UUID.class,valid.id()));
        assertEquals(other.driver(),jdbc.queryForObject("select locked_by from payroll_runs where id=?",UUID.class,valid.id()));
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class PayrollFixtureConfiguration {
        @org.springframework.context.annotation.Bean
        com.company.logicstic.service.payroll.payment.PayrollProviderCallbackVerifier testOnlyCallbackVerifier(tools.jackson.databind.ObjectMapper mapper) {
            return new com.company.logicstic.service.payroll.payment.PayrollProviderCallbackVerifier() {
                public String key(){return "TEST_ONLY_ASYNC";}
                public Verified verify(String body,java.util.Map<String,String> headers) {
                    if(!"SIGNED_FIXTURE".equals(headers.get("x-fixture-signature"))) throw new com.company.logicstic.exception.ForbiddenException("Fixture signature required");
                    return new Verified(mapper.readValue(body,com.company.logicstic.service.payroll.payment.VerifiedPayrollPaymentEvent.class),
                            java.util.Map.of("verifier","FIXTURE_ONLY","signatureChecked",true));
                }
            };
        }
        @org.springframework.context.annotation.Bean
        com.company.logicstic.service.payroll.payment.PayrollPaymentProvider testOnlyPaymentProvider() {
            return new com.company.logicstic.service.payroll.payment.PayrollPaymentProvider() {
                private final java.util.concurrent.ConcurrentHashMap<String,String> requests=new java.util.concurrent.ConcurrentHashMap<>();
                public String key() {return "TEST_ONLY_ASYNC";}
                public boolean supports(String method) {return "BANK_TRANSFER".equals(method);}
                public Submission submit(com.company.logicstic.service.payroll.payment.PayrollPaymentInstruction input) {
                    var reference=requests.computeIfAbsent(input.idempotencyKey(),key -> "FIXTURE-"+input.paymentId());
                    if("UNKNOWN_OUTCOME".equals(input.destinationReference())) return new Submission("UNAVAILABLE","FIXTURE_UNKNOWN",null);
                    return new Submission("AVAILABLE",null,reference);
                }
            };
        }
        @org.springframework.context.annotation.Bean
        com.company.logicstic.service.payroll.tax.PayrollTaxAdapter testOnlyTaxAdapter() {
            return new com.company.logicstic.service.payroll.tax.PayrollTaxAdapter() {
                public String key() { return "TEST_ONLY_FIXED"; }
                public com.company.logicstic.service.payroll.tax.PayrollTaxResult calculate(
                        com.company.logicstic.service.payroll.tax.PayrollTaxContext context,
                        com.company.logicstic.service.payroll.domain.PayrollPolicy policy) {
                    // Fixed arithmetic fixtures, never regional/statutory rates.
                    var zero=context.grossAmount().signum()==0;
                    return new com.company.logicstic.service.payroll.tax.PayrollTaxResult("AVAILABLE",null,context.currency(),
                            new java.math.BigDecimal(zero?"0":"7"),new java.math.BigDecimal(zero?"0":"3"),
                            key(),"FIXTURE-1",java.util.Map.of("explicitInputs",context.additionalInputs()),java.util.Map.of("fixture",true));
                }
            };
        }
    }

    private record PayrollFixture(UUID driver,UUID period,UUID settlement,String email) {}
    private PayrollFixture payrollFixture() {
        var f=fixture();var date=java.time.LocalDate.of(2026,1,1);var code=UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,f.actor(),"1",date,null));var period=payPeriods.create(code,date,date.plusDays(30),null);
        addAssignedTrip(f,"2026-01-15T12:00:00Z");var s=driverPay.calculate(f.actor(),period.getId());finalizeSettlement(s.id(),f.actor());
        return new PayrollFixture(f.actor(),period.getId(),s.id(),f.email());
    }
    private com.company.logicstic.dto.payroll.CalculatePayrollRequest payrollRequest(PayrollFixture f) {
        return new com.company.logicstic.dto.payroll.CalculatePayrollRequest(UUID.randomUUID().toString(),f.period(),"USD",
                java.time.LocalDate.of(2026,1,31),java.util.List.of(f.settlement()),java.util.Map.of(),java.util.Map.of(),java.util.List.of());
    }
    private UUID zeroLockedSettlement(UUID driver, UUID period, UUID payPolicy) {
        UUID settlement=UUID.randomUUID(),snapshot=UUID.randomUUID();
        jdbc.update("""
                insert into calculation_snapshots(id,entity_type,entity_id,calculation_type,engine_name,engine_version,input_json,result_json)
                values (?,'SETTLEMENT',?,'DRIVER_PAY','ZeroNetFixture','1','{}','{}')
                """,snapshot,settlement);
        jdbc.update("""
                insert into settlements(id,settlement_number,driver_id,pay_period_id,settlement_type,sequence_number,
                    pay_policy_id,pay_policy_version,status,currency,calculation_snapshot_id,settlement_net,gross_earnings)
                values (?, ?, ?, ?, 'ADJUSTMENT', 1, ?, 1, 'LOCKED', 'USD', ?, 0, 0)
                """,settlement,"ZERO-"+settlement,driver,period,payPolicy,snapshot);
        return settlement;
    }
    private com.company.logicstic.service.payroll.domain.PayrollJurisdiction payrollJurisdiction(String country) {
        return new com.company.logicstic.service.payroll.domain.PayrollJurisdiction(country,UUID.randomUUID().toString(),null);
    }
    private void payrollProfile(UUID driver,com.company.logicstic.service.payroll.domain.PayrollJurisdiction jurisdiction) {
        payrollConfiguration.appendProfile(driver,new com.company.logicstic.dto.payroll.PayrollConfigurationRequests.Profile(
                jurisdiction,com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR,
                java.time.LocalDate.of(2026,1,1),null,true));
    }
    private com.company.logicstic.service.payroll.domain.PayrollPolicy payrollPolicy(
            String code,com.company.logicstic.service.payroll.domain.PayrollJurisdiction jurisdiction,java.time.LocalDate from,java.time.LocalDate to,String adapter) {
        return payrollConfiguration.appendPolicy(new com.company.logicstic.dto.payroll.PayrollConfigurationRequests.Policy(code,jurisdiction,
                com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR,from,to,true,"USD",adapter,
                "fixture://explicit-test-policy","{}"));
    }

    @Test void payrollMissingJurisdictionIsUnavailableAndConcurrentRetryReservesOnce() throws Exception {
        var f=payrollFixture();payrollProfile(f.driver(),null);var request=payrollRequest(f);
        com.company.logicstic.dto.payroll.PayrollRunView result;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> payroll.calculate(request));var b=pool.submit(() -> payroll.calculate(request));
            result=a.get(20,TimeUnit.SECONDS);assertEquals(result.id(),b.get(20,TimeUnit.SECONDS).id());
        }
        assertEquals("VALIDATION_REQUIRED",result.status());var item=result.items().getFirst();
        assertEquals("UNAVAILABLE",item.taxAvailability());assertEquals("PAYROLL_JURISDICTION_NOT_CONFIGURED",item.validationReason());
        assertNull(item.incomeTaxAmount());assertNull(item.insuranceAmount());assertNull(item.netAmount());
        assertEquals(0,new java.math.BigDecimal("100").compareTo(item.grossAmount()));
        for(var status:java.util.List.of("APPROVED","LOCKED","PAYMENT_SCHEDULED"))
            assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_runs set status=? where id=?",status,result.id()));
        assertEquals(1,jdbc.queryForObject("select count(*) from payroll_run_item_settlements where settlement_id=?",Integer.class,f.settlement()));
        assertEquals("PAYROLL_SETTLEMENT_ALREADY_RESERVED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> payroll.calculate(payrollRequest(f))).getCode());
        var changed=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(request.idempotencyKey(),request.payPeriodId(),"USD",
                request.effectiveDate().minusDays(1),request.settlementIds(),null,null,null);
        assertEquals("PAYROLL_IDEMPOTENCY_CONFLICT",assertThrows(com.company.logicstic.exception.BadRequestException.class,() -> payroll.calculate(changed)).getCode());
    }

    @Test void payrollUsesOverrideProfileTenantHierarchyWithSeparateContractorTaxAndSupplements() {
        var f=payrollFixture();var tenant=payrollJurisdiction("US");var profile=payrollJurisdiction("VN");var work=payrollJurisdiction("US");
        payrollConfiguration.setTenantDefault(tenant);
        try {
            payrollProfile(f.driver(),profile);var date=java.time.LocalDate.of(2026,1,1);
            var tenantPolicy=payrollPolicy(UUID.randomUUID().toString(),tenant,date,null,"TEST_ONLY_FIXED");
            var profilePolicy=payrollPolicy(UUID.randomUUID().toString(),profile,date,null,"TEST_ONLY_FIXED");
            var workPolicy=payrollPolicy(UUID.randomUUID().toString(),work,date,null,"TEST_ONLY_FIXED");
            var plain=payrollRequest(f);
            var request=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(plain.idempotencyKey(),plain.payPeriodId(),"USD",plain.effectiveDate(),
                    plain.settlementIds(),java.util.Map.of(f.driver(),work),java.util.Map.of(f.driver(),java.util.Map.of("taxPeriod","EXPLICIT_FIXTURE")),
                    java.util.List.of(new com.company.logicstic.dto.payroll.CalculatePayrollRequest.Supplement(UUID.randomUUID(),f.driver(),"DEDUCTION","Voluntary fixture",new java.math.BigDecimal("2")),
                            new com.company.logicstic.dto.payroll.CalculatePayrollRequest.Supplement(UUID.randomUUID(),f.driver(),"REIMBURSEMENT","Expense fixture",new java.math.BigDecimal("5"))));
            var run=payroll.calculate(request);assertEquals("CALCULATED",run.status());var item=run.items().getFirst();
            assertEquals(work,item.jurisdiction());assertEquals(workPolicy.id(),item.policyId());
            assertEquals(com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR,item.workerClassification());
            assertEquals(0,new java.math.BigDecimal("7").compareTo(item.incomeTaxAmount()));assertEquals(0,new java.math.BigDecimal("93").compareTo(item.netAmount()));
            var snapshot=tools.jackson.databind.json.JsonMapper.builder().build().readTree(item.calculationSnapshotJson());
            assertEquals("WORK_PAYROLL_OVERRIDE",snapshot.get("jurisdictionResolution").get("source").asText());
            assertEquals(workPolicy.id().toString(),snapshot.get("policyId").asText());assertEquals(1,snapshot.get("policyVersion").asInt());
            assertEquals("EXPLICIT_FIXTURE",snapshot.get("taxContext").get("additionalInputs").get("taxPeriod").asText());
            assertEquals("FIXTURE-1",snapshot.get("taxResult").get("calculatorVersion").asText());
            var employeeOnly=payrollFixture();payrollProfile(employeeOnly.driver(),profile);
            assertEquals(profilePolicy.id(),payroll.calculate(payrollRequest(employeeOnly)).items().getFirst().policyId());
            var defaultOnly=payrollFixture();payrollProfile(defaultOnly.driver(),null);
            assertEquals(tenantPolicy.id(),payroll.calculate(payrollRequest(defaultOnly)).items().getFirst().policyId());
            assertEquals("LOCKED",jdbc.queryForObject("select status from settlements where id=?",String.class,f.settlement()));
            assertEquals(0,new java.math.BigDecimal("100").compareTo(jdbc.queryForObject("select settlement_net from settlements where id=?",java.math.BigDecimal.class,f.settlement())));
        } finally {jdbc.update("update tenant_payroll_settings set default_jurisdiction_id=null where id=1");}
    }

    @Test void payrollPoliciesAreAppendOnlyEffectiveDatedAndExpiredNewestCannotResurrectOldVersion() {
        var f=payrollFixture();var jurisdiction=payrollJurisdiction("VN");payrollProfile(f.driver(),jurisdiction);
        var code=UUID.randomUUID().toString();var jan=java.time.LocalDate.of(2026,1,1);
        var first=payrollPolicy(code,jurisdiction,jan,null,"TEST_ONLY_FIXED");
        var run=payroll.calculate(payrollRequest(f));var saved=run.items().getFirst().calculationSnapshotJson();
        var second=payrollPolicy(code,jurisdiction,jan.plusMonths(1),jan.plusMonths(1).plusDays(27),"TEST_ONLY_FIXED");
        assertEquals(2,second.version());assertEquals(first.id(),payrollPolicyResolver.resolve(jurisdiction,
                com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR,jan.plusDays(30)).id());
        assertEquals(second.id(),payrollPolicyResolver.resolve(jurisdiction,
                com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR,jan.plusMonths(1)).id());
        assertEquals("PAYROLL_POLICY_NOT_CONFIGURED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> payrollPolicyResolver.resolve(jurisdiction,com.company.logicstic.service.payroll.domain.WorkerClassification.CONTRACTOR,jan.plusMonths(2))).getCode());
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("update payroll_policy_versions set configuration_json='{}' where id=?",first.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> jdbc.update("delete from employee_payroll_profiles where employee_id=?",f.driver()));
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(mapper.readTree(saved),mapper.readTree(payroll.get(run.id()).items().getFirst().calculationSnapshotJson()));
    }

    @Test void payrollRecalculationPreservesUnavailableSnapshotAndResolvesOnlyExplicitNewPolicy() {
        var f=payrollFixture();var jurisdiction=payrollJurisdiction("US");payrollProfile(f.driver(),jurisdiction);
        var code=UUID.randomUUID().toString();var jan=java.time.LocalDate.of(2026,1,1);
        payrollPolicy(code,jurisdiction,jan,null,"UNCONFIGURED_ADAPTER");var run=payroll.calculate(payrollRequest(f));
        assertEquals("PAYROLL_TAX_CALCULATOR_NOT_CONFIGURED",run.items().getFirst().validationReason());
        var prior=jdbc.queryForObject("select result_json::text from calculation_snapshots where entity_type='PAYROLL_ITEM' and entity_id=?",String.class,run.items().getFirst().id());
        var explicit=payrollPolicy(code,jurisdiction,jan.plusDays(1),null,"TEST_ONLY_FIXED");
        var recalculated=payroll.recalculate(run.id());assertEquals("CALCULATED",recalculated.status());assertEquals(explicit.id(),recalculated.items().getFirst().policyId());
        assertEquals(2,jdbc.queryForObject("select count(*) from calculation_snapshots where entity_type='PAYROLL_ITEM' and entity_id=?",Integer.class,run.items().getFirst().id()));
        assertEquals(1,jdbc.queryForObject("select count(*) from calculation_snapshots where entity_type='PAYROLL_ITEM' and entity_id=? and result_json=?::jsonb",Integer.class,run.items().getFirst().id(),prior));
    }

    @Test void payrollReversalMappingUsesOriginalEconomicGrossAndStandaloneRecoveryRequiresValidation() {
        var f=payrollFixture();var jurisdiction=payrollJurisdiction("VN");payrollProfile(f.driver(),jurisdiction);
        payrollPolicy(UUID.randomUUID().toString(),jurisdiction,java.time.LocalDate.of(2026,1,1),null,"TEST_ONLY_FIXED");
        var reverse=driverPay.reverse(f.settlement(),"Fixture reversal");finalizeSettlement(reverse.id(),f.driver());
        var plain=payrollRequest(f);
        var request=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(plain.idempotencyKey(),plain.payPeriodId(),"USD",plain.effectiveDate(),
                java.util.List.of(f.settlement(),reverse.id()),null,null,null);
        var combined=payroll.calculate(request);assertEquals("CALCULATED",combined.status());assertEquals(0,combined.items().getFirst().grossAmount().signum());
        assertEquals(0,combined.items().getFirst().netAmount().signum());
        var other=payrollFixture();payrollProfile(other.driver(),jurisdiction);var child=driverPay.reverse(other.settlement(),"Unpaired recovery");finalizeSettlement(child.id(),other.driver());
        var template=payrollRequest(other);var recovery=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(template.idempotencyKey(),template.payPeriodId(),"USD",template.effectiveDate(),
                java.util.List.of(child.id()),null,null,null);
        var blocked=payroll.calculate(recovery);assertEquals("VALIDATION_REQUIRED",blocked.status());
        assertEquals("PAYROLL_RECOVERY_POLICY_REQUIRED",blocked.items().getFirst().validationReason());assertNull(blocked.items().getFirst().netAmount());
        assertEquals(0,new java.math.BigDecimal("-100").compareTo(blocked.items().getFirst().grossAmount()));
    }

    @Test void payrollCalculationApiRequiresPayrollRoleAndSerializesAvailability() throws Exception {
        var f=payrollFixture();payrollProfile(f.driver(),null);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var body=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(payrollRequest(f));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/runs/calculate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER"))
                        .contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payroll/runs/calculate")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("PAYROLL"))
                        .contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        assertTrue(response.getResponse().getContentAsString().contains("PAYROLL_JURISDICTION_NOT_CONFIGURED"));
        assertTrue(response.getResponse().getContentAsString().contains("\"netAmount\":null"));
    }

    @Test void payrollCurrencyMismatchRollsBackAllSourceClaims() {
        var f=payrollFixture();var template=payrollRequest(f);
        var mixed=new com.company.logicstic.dto.payroll.CalculatePayrollRequest(template.idempotencyKey(),template.payPeriodId(),"EUR",
                template.effectiveDate(),template.settlementIds(),null,null,null);
        assertThrows(com.company.logicstic.exception.CurrencyMismatchException.class,() -> payroll.calculate(mixed));
        assertEquals(0,jdbc.queryForObject("select count(*) from payroll_runs where request_key=?",Integer.class,template.idempotencyKey()));
        assertEquals(0,jdbc.queryForObject("select count(*) from payroll_run_item_settlements where settlement_id=?",Integer.class,f.settlement()));
    }

    @Test void concurrentPayrollPolicyVersionsPreserveHistoryAndRejectEqualEffectiveDate() throws Exception {
        var j=payrollJurisdiction("US");var code=UUID.randomUUID().toString();var date=java.time.LocalDate.of(2026,1,1);
        var first=payrollPolicy(code,j,date,null,"TEST_ONLY_FIXED");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> payrollPolicy(code,j,date.plusDays(1),null,"TEST_ONLY_FIXED"));
            var b=pool.submit(() -> payrollPolicy(code,j,date.plusDays(1),null,"TEST_ONLY_FIXED"));
            int succeeded=0,rejected=0;
            for(var task:java.util.List.of(a,b)) try {assertEquals(2,task.get(20,TimeUnit.SECONDS).version());succeeded++;}
                catch(java.util.concurrent.ExecutionException e) {assertInstanceOf(com.company.logicstic.exception.BadRequestException.class,e.getCause());rejected++;}
            assertEquals(1,succeeded);assertEquals(1,rejected);
        }
        assertEquals(2,jdbc.queryForObject("select count(*) from payroll_policy_versions where policy_code=?",Integer.class,code));
        assertEquals(1,jdbc.queryForObject("select policy_version from payroll_policy_versions where id=?",Integer.class,first.id()));
    }

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
                insert into settlements(id,settlement_number,driver_id,pay_period_id,pay_policy_id,pay_policy_version,status,currency,calculation_snapshot_id,settlement_net,gross_earnings,bonus_amount)
                values (?, ?, ?, ?, ?, 1, 'LOCKED', 'USD', ?, 10,10,10)
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
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("OPEN"));
        String policyJson = "{\"policyCode\":\""+UUID.randomUUID()+"\",\"name\":\"Contract\",\"driverId\":\""+fixture.actor()+"\",\"payMethod\":\"PERCENT_REVENUE\",\"revenuePercentage\":0.25,\"revenueBasis\":\"INVOICE_SUBTOTAL\",\"currency\":\"USD\",\"effectiveFrom\":\"2026-01-01\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/driver-pay-policies").contentType("application/json").content(policyJson)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("PAYROLL_MANAGER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.revenuePercentage").value(0.25));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/driver-pay-policies").contentType("application/json").content(policyJson.replace("0.25","25"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("PAYROLL_MANAGER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    private com.company.logicstic.dto.payroll.DriverPayPolicyRequest payPolicyRequest(String code, UUID driver, String rate,
            java.time.LocalDate from, java.time.LocalDate to) {
        return new com.company.logicstic.dto.payroll.DriverPayPolicyRequest(code,"Mileage",driver,"PER_MILE",new java.math.BigDecimal(rate),
                null,null,null,null,null,"ACTUAL_ALL_MILES",null,null,null,null,null,null,"USD",from,to);
    }

    @Test void mileageSettlementPersistsSelectedBasisInputsAndMissingActualCreatesNoFinancialRows() {
        var fixture = fixture(); var start = java.time.LocalDate.of(2026,1,1); String code = UUID.randomUUID().toString();
        var policy = payPolicies.create(payPolicyRequest(code,fixture.actor(),"0.625",start,null));
        var period = payPeriods.create(code,start,start.plusDays(30),null);
        UUID trip = UUID.randomUUID(), assignment = UUID.randomUUID();
        jdbc.update("insert into trips(id,name,total_distance,status,completed_at) values (?,'Mileage',99999,'COMPLETED','2026-01-15T12:00:00Z')",trip);
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,effective_from,effective_to,actual_miles,planned_miles)
                values (?,?,?,'2026-01-14T12:00:00Z','2026-01-15T12:00:00Z',123.456,200)
                """,assignment,trip,fixture.actor());
        var result = driverPay.calculate(fixture.actor(),period.getId());
        assertEquals(new java.math.BigDecimal("77.16"),result.grossEarnings()); assertEquals(1,result.lines().size());
        String input = jdbc.queryForObject("select s.input_json::text from calculation_snapshots s join settlements ds on ds.calculation_snapshot_id=s.id where ds.id=?",String.class,result.id());
        var calculation = tools.jackson.databind.json.JsonMapper.builder().build().readTree(input).get("mileageCalculations").get(0);
        assertEquals("ACTUAL_ALL_MILES",calculation.get("mileageBasis").asText());
        assertEquals(assignment.toString(),calculation.get("assignmentId").asText()); assertEquals(policy.getId().toString(),calculation.get("policyId").asText());
        assertEquals(1,calculation.get("policyVersion").intValue());
        assertEquals(result.id(),driverPay.calculate(fixture.actor(),period.getId()).id());
        var missing = fixture(); var missingCode = UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(missingCode,missing.actor(),"1",start,null));
        var missingPeriod = payPeriods.create(missingCode,start,start.plusDays(30),null);
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,effective_from,effective_to,planned_miles)
                values (?,?,?,'2026-01-14T12:00:00Z','2026-01-15T12:00:00Z',999)
                """,UUID.randomUUID(),trip,missing.actor());
        int snapshots = jdbc.queryForObject("select count(*) from calculation_snapshots",Integer.class);
        assertEquals("MILEAGE_VALIDATION_REQUIRED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> driverPay.calculate(missing.actor(),missingPeriod.getId())).getCode());
        assertEquals(snapshots,jdbc.queryForObject("select count(*) from calculation_snapshots",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from settlements where driver_id=?",Integer.class,missing.actor()));
    }

    @Test void perLoadHourlyDailyAndFlatSettlementInputsAreExplicitAndDeduplicated() {
        var start = java.time.LocalDate.of(2026,1,1);
        for (String method : new String[]{"PER_LOAD","HOURLY","DAILY","FLAT_RATE"}) {
            var fixture = fixture(); String code = UUID.randomUUID().toString();
            var rate = new java.math.BigDecimal(method.equals("HOURLY")?"20.25":method.equals("PER_LOAD")?"125":method.equals("DAILY")?"150":"1000");
            payPolicies.create(new com.company.logicstic.dto.payroll.DriverPayPolicyRequest(code,"Work pay",fixture.actor(),method,null,
                    method.equals("PER_LOAD")?rate:null,method.equals("HOURLY")?rate:null,method.equals("DAILY")?rate:null,
                    method.equals("FLAT_RATE")?rate:null,null,null,null,null,null,null,null,null,"USD",start,null));
            var period = payPeriods.create(code,start,start.plusDays(30),null);
            if (method.equals("PER_LOAD")) {
                for (int i = 0; i < 2; i++) addAssignedTrip(fixture,"2026-01-15T12:00:00Z");
            } else if (!method.equals("FLAT_RATE")) {
                for (String hours : new String[]{"7.25","1.00"}) {
                    jdbc.update("""
                            insert into time_entries(id,employee_id,date,start_time,end_time,total_hours,type)
                            values (?,?,'2026-01-15T00:00:00Z',interval '8 hours',interval '16 hours',?,'WORK')
                            """,UUID.randomUUID(),fixture.actor(),new java.math.BigDecimal(hours));
                }
            }
            var result = driverPay.calculate(fixture.actor(),period.getId());
            java.math.BigDecimal expected = new java.math.BigDecimal(method.equals("HOURLY")?"167.06":method.equals("PER_LOAD")?"125.00":method.equals("DAILY")?"150.00":"1000.00");
            assertEquals(expected,result.grossEarnings(),method); assertEquals(expected,result.settlementNet(),method);
            assertEquals(method.equals("HOURLY")?2:1,result.lines().size(),method);
            String input = jdbc.queryForObject("select cs.input_json::text from calculation_snapshots cs join settlements s on s.calculation_snapshot_id=cs.id where s.id=?",String.class,result.id());
            var records = tools.jackson.databind.json.JsonMapper.builder().build().readTree(input).get("workCalculations");
            assertEquals(result.lines().size(),records.size()); assertEquals(method,records.get(0).get("method").asText());
        }
    }

    private UUID addAssignedTrip(Fixture fixture, String completedAt) {
        UUID trip = UUID.randomUUID();
        jdbc.update("insert into trips(id,name,total_distance,status,completed_at) values (?,'Work pay',99999,'COMPLETED',?::timestamptz)",trip,completedAt);
        jdbc.update("""
                insert into trip_stops(id,type,trip_id,"order",load_id,address_city,address_country,address_line1,
                    address_state,address_zip_code,location_latitude,location_longitude)
                values (?,'DELIVERY',?,1,?,'Test','US','Test','TX','00000',0,0)
                """,UUID.randomUUID(),trip,fixture.load());
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,effective_from,effective_to,actual_miles,planned_miles)
                values (?,?,?,'2026-01-14T12:00:00Z',?::timestamptz,100,200)
                """,UUID.randomUUID(),trip,fixture.actor(),completedAt);
        return trip;
    }

    @Test void percentageSettlementUsesReconciledSubtotalOnceAndRejectsMissingInvoice() {
        var start = java.time.LocalDate.of(2026,1,1); var fixture = fixture(); String code = UUID.randomUUID().toString();
        payPolicies.create(percentagePolicy(code,fixture.actor(),start)); var period = payPeriods.create(code,start,start.plusDays(30),null);
        UUID invoice = addInvoice(fixture.load()); jdbc.update("update invoices set tax_total_amount=10,total_amount=110 where id=?",invoice);
        addAssignedTrip(fixture,"2026-01-15T12:00:00Z"); addAssignedTrip(fixture,"2026-01-15T13:00:00Z");
        var result = driverPay.calculate(fixture.actor(),period.getId()); assertEquals(new java.math.BigDecimal("25.00"),result.grossEarnings()); assertEquals(1,result.lines().size());
        String input = jdbc.queryForObject("select cs.input_json::text from calculation_snapshots cs join settlements s on s.calculation_snapshot_id=cs.id where s.id=?",String.class,result.id());
        var data = tools.jackson.databind.json.JsonMapper.builder().build().readTree(input).get("percentageCalculations").get(0);
        assertEquals("INVOICE_SUBTOTAL",data.get("revenueBasis").asText()); assertEquals(invoice.toString(),data.get("invoiceId").asText());
        assertEquals(0,new java.math.BigDecimal("100").compareTo(data.get("eligibleRevenue").decimalValue()));
        var missing = fixture(); String missingCode = UUID.randomUUID().toString(); payPolicies.create(percentagePolicy(missingCode,missing.actor(),start));
        var missingPeriod = payPeriods.create(missingCode,start,start.plusDays(30),null); addAssignedTrip(missing,"2026-01-15T12:00:00Z");
        assertEquals("REVENUE_PAY_VALIDATION_REQUIRED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> driverPay.calculate(missing.actor(),missingPeriod.getId())).getCode());
        assertEquals(0,jdbc.queryForObject("select count(*) from settlements where driver_id=?",Integer.class,missing.actor()));
    }

    private com.company.logicstic.dto.payroll.DriverPayPolicyRequest percentagePolicy(String code, UUID driver, java.time.LocalDate date) {
        return new com.company.logicstic.dto.payroll.DriverPayPolicyRequest(code,"Revenue",driver,"PERCENT_REVENUE",null,null,null,null,null,
                new java.math.BigDecimal("0.25"),null,"INVOICE_SUBTOTAL",null,null,null,null,null,"USD",date,null);
    }

    @Test void accessorialSettlementPaysApprovedDriverAmountOnceAndBlocksAmbiguousRecipient() {
        var fixture = fixture(); var start = java.time.LocalDate.of(2026,1,1); String code = UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"0",start,null)); var period = payPeriods.create(code,start,start.plusDays(30),null);
        UUID trip = addAssignedTrip(fixture,"2026-01-15T12:00:00Z");
        var request = new com.company.logicstic.dto.accessorial.CreateAccessorialChargeRequest(trip,null,"OTHER",null,null,null,null,
                new java.math.BigDecimal("999"),java.math.BigDecimal.ZERO,new java.math.BigDecimal("25"),"USD",java.time.OffsetDateTime.parse("2026-01-14T15:00:00Z"),null,null);
        var charge = accessorials.createAccessorial(fixture.load(),request); accessorials.approveAccessorial(charge.id(),fixture.email());
        accessorials.createAccessorial(fixture.load(),request); // draft driver pay must remain ineligible
        var result = driverPay.calculate(fixture.actor(),period.getId()); assertEquals(new java.math.BigDecimal("25.00"),result.grossEarnings());
        assertEquals(1,result.lines().stream().filter(l -> l.lineType().equals("ACCESSORIAL")).count());
        assertEquals(charge.id(),jdbc.queryForObject("select accessorial_charge_id from settlement_lines where settlement_id=? and line_type='ACCESSORIAL'",UUID.class,result.id()));
        var other = fixture(); String otherCode = UUID.randomUUID().toString(); payPolicies.create(payPolicyRequest(otherCode,other.actor(),"0",start,null));
        var otherPeriod = payPeriods.create(otherCode,start,start.plusDays(30),null);
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,effective_from,effective_to,actual_miles)
                values (?,?,?,'2026-01-14T12:00:00Z','2026-01-15T12:00:00Z',100)
                """,UUID.randomUUID(),trip,other.actor());
        assertEquals("ACCESSORIAL_PAY_VALIDATION_REQUIRED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> driverPay.calculate(other.actor(),otherPeriod.getId())).getCode());
    }

    @Test void concurrentOriginalAndLockReconcileLinesAndProjectGrossCostOnce() throws Exception {
        var fixture = fixture(); var start = java.time.LocalDate.of(2026,1,1); String code = UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",start,null)); var period = payPeriods.create(code,start,start.plusDays(30),null);
        addAssignedTrip(fixture,"2026-01-15T12:00:00Z");
        for (String[] item : new String[][]{{"REIMBURSEMENT","5"},{"DEDUCTION","10"}})
            jdbc.update("""
                    insert into expenses(id,type,status,expense_date,amount_amount,amount_currency,category,employee_id,load_id)
                    values (?,'EMPLOYEE','APPROVED','2026-01-15T12:00:00Z',?,'USD',?,?,?)
                    """,UUID.randomUUID(),new java.math.BigDecimal(item[1]),item[0],fixture.actor(),fixture.load());
        com.company.logicstic.dto.payroll.DriverSettlementView original;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> driverPay.calculate(fixture.actor(),period.getId()));
            var b=pool.submit(() -> driverPay.calculate(fixture.actor(),period.getId()));
            original=a.get(20,TimeUnit.SECONDS); assertEquals(original.id(),b.get(20,TimeUnit.SECONDS).id());
        }
        assertEquals(new java.math.BigDecimal("100.00"),original.grossEarnings()); assertEquals(new java.math.BigDecimal("95.00"),original.settlementNet());
        assertThrows(com.company.logicstic.exception.BadRequestException.class, () -> driverPay.transition(original.id(),"LOCKED",fixture.actor()));
        driverPay.transition(original.id(),"IN_REVIEW",fixture.actor()); driverPay.transition(original.id(),"APPROVED",fixture.actor());
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> driverPay.transition(original.id(),"LOCKED",fixture.actor()));
            var b=pool.submit(() -> driverPay.transition(original.id(),"LOCKED",fixture.actor()));
            assertEquals(a.get(20,TimeUnit.SECONDS).lockedAt().toInstant(),b.get(20,TimeUnit.SECONDS).lockedAt().toInstant());
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from shipment_costs sc join settlement_lines l on l.id=sc.source_id where l.settlement_id=?",Integer.class,original.id()));
        assertEquals(0,new java.math.BigDecimal("100").compareTo(jdbc.queryForObject("select sum(sc.amount) from shipment_costs sc join settlement_lines l on l.id=sc.source_id where l.settlement_id=?",java.math.BigDecimal.class,original.id())));
        assertEquals("APPROVED",jdbc.queryForObject("select sc.status from shipment_costs sc join settlement_lines l on l.id=sc.source_id where l.settlement_id=?",String.class,original.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update settlements set gross_earnings=gross_earnings+1,settlement_net=settlement_net+1 where id=?",original.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update settlement_lines set amount=amount+1 where settlement_id=?",original.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update settlements set status='CALCULATED' where id=?",original.id()));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update driver_pay_policies set per_mile_rate=2 where policy_code=?",code));
        assertEquals(original.id(),driverPay.calculate(fixture.actor(),period.getId()).id());

        var request = new com.company.logicstic.dto.payroll.SettlementAdjustmentRequest("adjust-1","Correction",java.util.List.of(
                adjustmentLine("EARNING","BONUS","10",fixture.load()),adjustmentLine("DEDUCTION","ADVANCE","2",fixture.load()),
                adjustmentLine("REIMBURSEMENT","REIMBURSEMENT","5",fixture.load())));
        var adjustment=driverPay.createAdjustment(original.id(),request); assertEquals(new java.math.BigDecimal("13.00"),adjustment.settlementNet());
        assertEquals(adjustment.id(),driverPay.createAdjustment(original.id(),request).id()); finalizeSettlement(adjustment.id(),fixture.actor());
        assertEquals(1,jdbc.queryForObject("select count(*) from shipment_costs sc join settlement_lines l on l.id=sc.source_id where l.settlement_id=?",Integer.class,adjustment.id()));
        var changed = new com.company.logicstic.dto.payroll.SettlementAdjustmentRequest("adjust-1","Correction",java.util.List.of(adjustmentLine("EARNING","BONUS","11",fixture.load())));
        assertEquals("ADJUSTMENT_IDEMPOTENCY_CONFLICT",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> driverPay.createAdjustment(original.id(),changed)).getCode());
        var reverse=driverPay.reverse(original.id(),"R".repeat(300)); assertEquals(new java.math.BigDecimal("-95.00"),reverse.settlementNet());
        assertEquals(reverse.id(),driverPay.reverse(original.id(),"Retry reversal").id()); finalizeSettlement(reverse.id(),fixture.actor());
        assertEquals(0,new java.math.BigDecimal("-100").compareTo(jdbc.queryForObject("select sum(sc.amount) from shipment_costs sc join settlement_lines l on l.id=sc.source_id where l.settlement_id=?",java.math.BigDecimal.class,reverse.id())));
        assertEquals("LOCKED",driverPay.get(original.id()).status()); assertEquals(0,new java.math.BigDecimal("95.00").compareTo(driverPay.get(original.id()).settlementNet()));
        assertEquals(3,jdbc.queryForObject("select count(*) from settlements where driver_id=? and pay_period_id=?",Integer.class,fixture.actor(),period.getId()));
        assertEquals(0,new java.math.BigDecimal("10").compareTo(jdbc.queryForObject("select sum(amount) from shipment_costs where load_id=?",java.math.BigDecimal.class,fixture.load())));
    }

    @Test void assignmentClosingDateIsNotClampedAndOriginalSourcesCannotRepeatAcrossPeriods() {
        var fixture=fixture(); var jan=java.time.LocalDate.of(2026,1,1); String code=UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",jan,null));
        var january=payPeriods.create(code,jan,jan.plusDays(30),null); addAssignedTrip(fixture,"2026-02-01T12:00:00Z");
        assertEquals(new java.math.BigDecimal("0.00"),driverPay.calculate(fixture.actor(),january.getId()).grossEarnings());
        var february=payPeriods.create(code+"-FEB",jan.plusMonths(1),jan.plusMonths(1).plusDays(27),null);
        assertEquals(new java.math.BigDecimal("100.00"),driverPay.calculate(fixture.actor(),february.getId()).grossEarnings());
        var overlapping=payPeriods.create(code+"-OVERLAP",jan.plusMonths(1),jan.plusMonths(1).plusDays(10),null);
        assertEquals("SETTLEMENT_SOURCE_ALREADY_USED",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> driverPay.calculate(fixture.actor(),overlapping.getId())).getCode());
        assertEquals(2,jdbc.queryForObject("select count(*) from settlements where driver_id=?",Integer.class,fixture.actor()));
    }

    @Test void tripOnlyLinesProjectOneTripCostAndLoadsRequireAllocationForProfit() {
        var fixture=fixture(); var another=fixture(); var start=java.time.LocalDate.of(2026,1,1); String code=UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",start,null)); var period=payPeriods.create(code,start,start.plusDays(30),null);
        UUID trip=addAssignedTrip(fixture,"2026-01-15T12:00:00Z");
        jdbc.update("""
                insert into trip_stops(id,type,trip_id,"order",load_id,address_city,address_country,address_line1,address_state,address_zip_code,location_latitude,location_longitude)
                values (?,'DELIVERY',?,2,?,'Test','US','Test','TX','00000',0,0)
                """,UUID.randomUUID(),trip,another.load());
        addInvoice(fixture.load()); addInvoice(another.load());
        var settlement=driverPay.calculate(fixture.actor(),period.getId()); finalizeSettlement(settlement.id(),fixture.actor());
        assertEquals(1,jdbc.queryForObject("select count(*) from shipment_costs where trip_id=? and load_id is null",Integer.class,trip));
        for (UUID load : java.util.List.of(fixture.load(),another.load())) {
            var summary=profitability.getLoadFinancialSummary(load); assertNull(summary.allocatedProfit());
            assertEquals("TRIP_COST_ALLOCATION_REQUIRED",summary.allocatedProfitMetric().reason());
            assertEquals(1,summary.costClassification().unallocatedTripCosts().size());
        }
    }

    private com.company.logicstic.dto.payroll.SettlementAdjustmentRequest.Line adjustmentLine(String kind,String type,String amount,UUID load) {
        return new com.company.logicstic.dto.payroll.SettlementAdjustmentRequest.Line(kind,type,"Correction",new java.math.BigDecimal(amount),load,null);
    }
    private void finalizeSettlement(UUID settlement,UUID actor) {
        driverPay.transition(settlement,"IN_REVIEW",actor); driverPay.transition(settlement,"APPROVED",actor); driverPay.transition(settlement,"LOCKED",actor);
    }

    @Test void concurrentCorrectionsKeepOneIdempotentChildAndMonotonicSequence() throws Exception {
        var fixture=fixture(); var start=java.time.LocalDate.of(2026,1,1); String code=UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",start,null)); var period=payPeriods.create(code,start,start.plusDays(30),null);
        addAssignedTrip(fixture,"2026-01-15T12:00:00Z"); var parent=driverPay.calculate(fixture.actor(),period.getId()); finalizeSettlement(parent.id(),fixture.actor());
        var request=new com.company.logicstic.dto.payroll.SettlementAdjustmentRequest("same-key","Correction",java.util.List.of(adjustmentLine("EARNING","BONUS","10",fixture.load())));
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> driverPay.createAdjustment(parent.id(),request)); var b=pool.submit(() -> driverPay.createAdjustment(parent.id(),request));
            var first=a.get(20,TimeUnit.SECONDS); assertEquals(first.id(),b.get(20,TimeUnit.SECONDS).id()); assertEquals(1,first.sequenceNumber());
        }
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> driverPay.reverse(parent.id(),"First retry")); var b=pool.submit(() -> driverPay.reverse(parent.id(),"Second retry"));
            var first=a.get(20,TimeUnit.SECONDS); assertEquals(first.id(),b.get(20,TimeUnit.SECONDS).id()); assertEquals(2,first.sequenceNumber());
        }
        assertEquals(2,jdbc.queryForObject("select count(*) from settlements where parent_settlement_id=?",Integer.class,parent.id()));
        assertEquals(3,jdbc.queryForObject("select count(*) from calculation_snapshots where entity_type='DRIVER_SETTLEMENT' and entity_id in (select id from settlements where driver_id=?)",Integer.class,fixture.actor()));
    }

    @Test void projectionConflictRollsBackLockAndNeverOverwritesExistingLedgerCost() {
        var fixture=fixture(); var start=java.time.LocalDate.of(2026,1,1); String code=UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",start,null)); var period=payPeriods.create(code,start,start.plusDays(30),null);
        addAssignedTrip(fixture,"2026-01-15T12:00:00Z"); var original=driverPay.calculate(fixture.actor(),period.getId());
        driverPay.transition(original.id(),"IN_REVIEW",fixture.actor()); driverPay.transition(original.id(),"APPROVED",fixture.actor());
        UUID line=original.lines().getFirst().id();
        jdbc.update("""
                insert into shipment_costs(id,load_id,category,cost_basis,status,source_type,source_id,amount,currency,driver_id)
                values (?,?,'DRIVER','ACTUAL','APPROVED','DRIVER_SETTLEMENT',?,80,'USD',?)
                """,UUID.randomUUID(),fixture.load(),line,fixture.actor());
        assertEquals("SETTLEMENT_COST_SOURCE_CONFLICT",assertThrows(com.company.logicstic.exception.BadRequestException.class,
                () -> driverPay.transition(original.id(),"LOCKED",fixture.actor())).getCode());
        assertEquals("APPROVED",driverPay.get(original.id()).status()); assertNull(driverPay.get(original.id()).lockedAt());
        assertEquals(0,new java.math.BigDecimal("80").compareTo(jdbc.queryForObject("select amount from shipment_costs where source_id=?",java.math.BigDecimal.class,line)));
    }

    @Test void settlementValidationApiBlocksApprovalAndLockedHistoryCannotReopen() throws Exception {
        var fixture=fixture(); var start=java.time.LocalDate.of(2026,1,1); String code=UUID.randomUUID().toString();
        payPolicies.create(payPolicyRequest(code,fixture.actor(),"1",start,null)); var period=payPeriods.create(code,start,start.plusDays(30),null);
        addAssignedTrip(fixture,"2026-01-15T12:00:00Z"); var original=driverPay.calculate(fixture.actor(),period.getId());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var path="/api/driver-settlements/"+original.id();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/require-validation").contentType("application/json").content("{\"reason\":\"Receipt required\"}")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("ACCOUNTANT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("VALIDATION_REQUIRED"));
        assertThrows(com.company.logicstic.exception.BadRequestException.class, () -> driverPay.transition(original.id(),"APPROVED",fixture.actor()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/resolve-validation")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver").roles("DRIVER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/resolve-validation")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(fixture.email()).roles("ACCOUNTANT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("CALCULATED"));
        finalizeSettlement(original.id(),fixture.actor());
        assertThrows(com.company.logicstic.exception.BadRequestException.class, () -> driverPay.requireValidation(original.id(),"Reopen",fixture.actor()));
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
