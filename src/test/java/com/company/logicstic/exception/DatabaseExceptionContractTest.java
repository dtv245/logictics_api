package com.company.logicstic.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.logicstic.config.RequestIds;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

/** Keeps backend defects distinct from data conflicts and rolled-back transient failures. */
class DatabaseExceptionContractTest {
    @ParameterizedTest @CsvSource({"23505,409", "23503,409", "23502,500", "23514,500", "40001,503", "40P01,503", "XX000,500"})
    void sqlStatesKeepCorrectStatus(String state,int status) {
        var request=new MockHttpServletRequest("POST","/api/trucks");
        var response=new GlobalExceptionHandler().handleUnexpectedException(new DataIntegrityViolationException("private-row",new SQLException("private-detail",state)),request);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().message()).doesNotContain("private-");
    }
    @Test void knownConstraintMapsToDtoFieldAndNeverLeaksInternalName() {
        var cause=new org.hibernate.exception.ConstraintViolationException("private-row",new SQLException("private-row","23505"),"private-sql","ix_trucks_number");
        var request=new MockHttpServletRequest("POST","/api/trucks");
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(new DataIntegrityViolationException("private-row",cause),request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().getFirst().field()).isEqualTo("number");
        assertThat(response.getBody().errors().getFirst().code()).isEqualTo("UNIQUE");
        assertThat(response.getBody().toString()).doesNotContain("ix_trucks_number","private-");
    }

    @ParameterizedTest
    @CsvSource({
            "pay_periods_period_code_key,/api/pay-periods,periodCode",
            "uq_driver_pay_policies_version,/api/driver-pay-policies,policyCode",
            "ix_invoices_load_id,/api/invoices,loadId",
            "ux_legacy_invoice_load,/api/invoices,loadId",
            "uq_payroll_jurisdiction,/api/payroll/configuration/tenant-default,jurisdiction",
            "uq_payroll_jurisdiction,/api/payroll/configuration/policy-versions,jurisdiction",
            "uq_payroll_jurisdiction,/api/payroll/configuration/employees/employee-id/profiles,jurisdiction",
            "pay_periods_period_code_key,/api/pay-periods/,periodCode",
            "ux_legacy_invoice_load,/api/invoices/invoice-id,loadId"
    })
    void verifiedUniqueConstraintsMapToSubmittedFields(String constraint,String path,String field) {
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(
                violation("23505",constraint),new MockHttpServletRequest("POST",path));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_VALUE");
        assertThat(response.getBody().errors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo(field);
            assertThat(error.code()).isEqualTo("UNIQUE");
            assertThat(error.message()).isNotBlank();
        });
        assertThat(response.getBody().toString()).doesNotContain(constraint,"private-");
    }

    @ParameterizedTest
    @CsvSource({
            "ix_trucks_number,/api/loads",
            "ix_trucks_number,/api/trucks-extra",
            "ix_trucks_number,/api/trucksExtra",
            "ix_trucks_number,/api/loads/load-id/trucks",
            "ix_trucks_number,/other/api/trucks",
            "uq_payroll_jurisdiction,/api/payroll/configuration-extra/policy-versions",
            "uq_payroll_jurisdiction,/api/payroll/runs/calculate",
            "uq_payroll_jurisdiction,/api/payroll/other/configuration",
            "uq_driver_pay_policies_version,/api/driver-pay-policies/policy-id/new-version",
            "uq_driver_pay_policies_version,/api/driverpaypolicies"
    })
    void unrelatedResourcesAndGeneratedPolicyVersionsDoNotMarkFields(String constraint,String path) {
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(
                violation("23505",constraint),new MockHttpServletRequest("POST",path));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_VALUE");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void explicitServletContextKeepsNestedResourceMatching() {
        var request=new MockHttpServletRequest("POST","/logistics/api/payroll/configuration/policy-versions");
        request.setContextPath("/logistics");
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(violation("23505","uq_payroll_jurisdiction"),request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_VALUE");
        assertThat(response.getBody().errors()).singleElement().satisfies(error -> assertThat(error.field()).isEqualTo("jurisdiction"));
    }

    @ParameterizedTest
    @CsvSource({
            "23505,pay_periods_period_code_key,/api/pay-periods/period-id,DUPLICATE_VALUE",
            "23505,uq_driver_pay_policies_version,/api/driver-pay-policies,DUPLICATE_VALUE",
            "23505,ix_invoices_load_id,/api/invoices/invoice-id,DUPLICATE_VALUE",
            "23505,ux_legacy_invoice_load,/api/invoices/invoice-id,DUPLICATE_VALUE",
            "23505,uq_payroll_jurisdiction,/api/payroll/configuration/policy-versions,DUPLICATE_VALUE",
            "23503,fk_trucks_employees_main_driver_id,/api/trucks/truck-id,DATA_INTEGRITY_VIOLATION",
            "23503,fk_trucks_employees_main_driver_id,/api/employees/employee-id,DATA_INTEGRITY_VIOLATION"
    })
    void deleteNeverReportsAFormField(String state,String constraint,String path,String code) {
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(
                violation(state,constraint),new MockHttpServletRequest("DELETE",path));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().errors()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/trucks,mainDriverId",
            "PUT,/api/trucks/truck-id,mainDriverId",
            "POST,/api/employees,''"
    })
    void foreignKeyFieldsBelongOnlyToTheReferencingForm(String method,String path,String field) {
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(
                violation("23503","fk_trucks_employees_main_driver_id"),new MockHttpServletRequest(method,path));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        if(field.isEmpty())assertThat(response.getBody().errors()).isEmpty();
        else assertThat(response.getBody().errors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo(field);
            assertThat(error.code()).isEqualTo("REFERENCE_CONFLICT");
        });
    }

    @Test
    void unknownUniqueConstraintWarnsWithRequestIdWithoutLeakingMetadata() {
        Logger logger=(Logger)LoggerFactory.getLogger(DatabaseFailures.class);
        var events=new ListAppender<ILoggingEvent>();
        events.start();logger.addAppender(events);
        try {
            var request=new MockHttpServletRequest("POST","/api/trucks");
            request.addHeader(RequestIds.HEADER,"session-a-unmapped");
            var response=new GlobalExceptionHandler().handleDataIntegrityViolation(violation("23505","private_unknown_unique"),request);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().code()).isEqualTo("DUPLICATE_VALUE");
            assertThat(response.getBody().errors()).isEmpty();
            assertThat(response.getBody().params()).isEmpty();
            assertThat(response.getBody().toString()).doesNotContain("private_unknown_unique","private-table","private-column","private-detail");
            assertThat(events.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).isEqualTo(
                        "unmapped_unique_constraint constraint=private_unknown_unique path=/api/trucks requestId=session-a-unmapped");
            });
        } finally {
            logger.detachAppender(events);events.stop();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "uq_payments_idempotency_key,/api/payments,PAYMENT_IDEMPOTENCY_CONFLICT",
            "uq_payroll_run_request,/api/payroll/runs/calculate,DUPLICATE_VALUE",
            "payroll_runs_run_number_key,/api/payroll/runs/calculate,DUPLICATE_VALUE",
            "settlements_settlement_number_key,/api/driver-settlements/calculate,DUPLICATE_VALUE",
            "uq_settlements_original_driver_period,/api/driver-settlements/calculate,DUPLICATE_VALUE",
            "uq_settlements_parent_request,/api/driver-settlements/settlement-id/adjustments,DUPLICATE_VALUE",
            "uq_settlements_parent_sequence,/api/driver-settlements/settlement-id/adjustments,DUPLICATE_VALUE",
            "uq_settlements_one_reversal,/api/driver-settlements/settlement-id/reversal,DUPLICATE_VALUE",
            "optimization_policy_versions_policy_code_policy_version_key,/api/optimization/policies,DUPLICATE_VALUE",
            "fleet_policy_versions_policy_code_policy_version_key,/api/fleet/policies,DUPLICATE_VALUE",
            "payroll_policy_versions_policy_code_policy_version_key,/api/payroll/configuration/policy-versions,DUPLICATE_VALUE",
            "employee_payroll_profiles_employee_id_profile_version_key,/api/payroll/configuration/employees/employee-id/profiles,DUPLICATE_VALUE",
            "ux_rated_primary_business_key,/api/invoices/billing/generate,DUPLICATE_VALUE",
            "ux_invoice_one_rebill,/api/invoices/billing/invoice-id/rebill,DUPLICATE_VALUE"
    })
    void commandIdentityHistoryAndGeneratedValuesKeepToastErrors(String constraint,String path,String code) {
        var response=new GlobalExceptionHandler().handleDataIntegrityViolation(
                violation("23505",constraint),new MockHttpServletRequest("POST",path));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().errors()).isEmpty();
        assertThat(response.getBody().message()).isNotBlank();
        assertThat(response.getBody().toString()).doesNotContain(constraint,"private-");
    }

    private static DataIntegrityViolationException violation(String state,String constraint) {
        var metadata=new ServerErrorMessage("SERROR\0C"+state+"\0n"+constraint
                +"\0tprivate-table\0cprivate-column\0Mprivate-detail\0\0");
        return new DataIntegrityViolationException("private-detail",new PSQLException(metadata));
    }
}
