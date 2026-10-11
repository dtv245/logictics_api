import json
import os
import sys

def main():
    print("Building Postman Suite for LogisticsX TMS...")

    # 1. Environment
    env_vars = [
        # Base & Auth
        {"key": "baseUrl", "value": "http://localhost:8080", "enabled": True},
        {"key": "accessToken", "value": "", "enabled": True},
        {"key": "tokenExpiresAt", "value": "0", "enabled": True},
        {"key": "tenantId", "value": "", "enabled": True},
        {"key": "employeeId", "value": "", "enabled": True},
        {"key": "roles", "value": "[]", "enabled": True},
        {"key": "returnTo", "value": "/", "enabled": True},
        {"key": "larkAuthorizeUrl", "value": "", "enabled": True},
        {"key": "larkCode", "value": "", "enabled": True},
        {"key": "larkState", "value": "", "enabled": True},
        {"key": "responseTimeoutThreshold", "value": "3000", "enabled": True},
        {"key": "runCleanup", "value": "false", "enabled": True},

        # Runtime IDs & Versions
        {"key": "roleId", "value": "", "enabled": True},
        {"key": "dispatcherEmployeeId", "value": "", "enabled": True},
        {"key": "driverEmployeeId", "value": "", "enabled": True},
        {"key": "driverId", "value": "", "enabled": True},
        {"key": "customerId", "value": "", "enabled": True},
        {"key": "truckId", "value": "", "enabled": True},
        {"key": "truckVersion", "value": "0", "enabled": True},
        {"key": "payPeriodId", "value": "", "enabled": True},
        {"key": "payPeriodCode", "value": "", "enabled": True},
        {"key": "payPolicyId", "value": "", "enabled": True},
        {"key": "ratingContractId", "value": "", "enabled": True},
        {"key": "ratingContractVersion", "value": "1", "enabled": True},
        {"key": "ratingRuleId", "value": "", "enabled": True},
        {"key": "ratingRuleVersion", "value": "1", "enabled": True},
        {"key": "loadId", "value": "", "enabled": True},
        {"key": "loadVersion", "value": "0", "enabled": True},
        {"key": "accessorialChargeId", "value": "", "enabled": True},
        {"key": "ratingSnapshotId", "value": "", "enabled": True},
        {"key": "previewInputHash", "value": "", "enabled": True},
        {"key": "previewResultHash", "value": "", "enabled": True},
        {"key": "ratingPreviewObj", "value": "{}", "enabled": True},
        {"key": "tripId", "value": "", "enabled": True},
        {"key": "tripVersion", "value": "0", "enabled": True},
        {"key": "driverAssignmentId", "value": "", "enabled": True},
        {"key": "pickupStopId", "value": "", "enabled": True},
        {"key": "detentionStopId", "value": "", "enabled": True},
        {"key": "optimizationPolicyId", "value": "", "enabled": True},
        {"key": "fleetPolicyId", "value": "", "enabled": True},
        {"key": "capacityInputId", "value": "", "enabled": True},
        {"key": "qualificationInputId", "value": "", "enabled": True},
        {"key": "forecastInputId", "value": "", "enabled": True},
        {"key": "optimizationRunId", "value": "", "enabled": True},
        {"key": "optimizationCandidateId", "value": "", "enabled": True},
        {"key": "optimizationCandidateFingerprint", "value": "", "enabled": True},
        {"key": "inspectionId", "value": "", "enabled": True},
        {"key": "shipmentCostId", "value": "", "enabled": True},
        {"key": "expenseId", "value": "", "enabled": True},
        {"key": "taxAssessmentId", "value": "", "enabled": True},
        {"key": "billingInvoiceId", "value": "", "enabled": True},
        {"key": "billingInvoiceGeneration", "value": "1", "enabled": True},
        {"key": "billingSupplementalId", "value": "", "enabled": True},
        {"key": "billingCreditId", "value": "", "enabled": True},
        {"key": "originalBillingLineId", "value": "", "enabled": True},
        {"key": "legacyInvoiceId", "value": "", "enabled": True},
        {"key": "paymentId", "value": "", "enabled": True},
        {"key": "settlementId", "value": "", "enabled": True},
        {"key": "payrollRunId", "value": "", "enabled": True},
        {"key": "payrollItemId", "value": "", "enabled": True},
        {"key": "payrollPaymentId", "value": "", "enabled": True},
        {"key": "payslipId", "value": "", "enabled": True},
        {"key": "conversationId", "value": "", "enabled": True},
        {"key": "messageId", "value": "", "enabled": True},
        {"key": "notificationId", "value": "", "enabled": True},
        {"key": "documentId", "value": "", "enabled": True},

        # Enums & Configurations (Gaps & Assumptions defaults)
        {"key": "enum_currency", "value": "USD", "enabled": True},
        {"key": "enum_country", "value": "US", "enabled": True},
        {"key": "enum_state", "value": "CA", "enabled": True},
        {"key": "enum_loadStatus", "value": "DRAFT", "enabled": True},
        {"key": "enum_loadType", "value": "FULL_TRUCKLOAD", "enabled": True},
        {"key": "enum_loadSource", "value": "MANUAL", "enabled": True},
        {"key": "enum_truckStatus", "value": "AVAILABLE", "enabled": True},
        {"key": "enum_truckType", "value": "SEMI", "enabled": True},
        {"key": "enum_employeeStatus", "value": "ACTIVE", "enabled": True},
        {"key": "enum_salaryType", "value": "HOURLY", "enabled": True},
        {"key": "enum_customerStatus", "value": "ACTIVE", "enabled": True},
        {"key": "enum_tripStatus", "value": "PLANNED", "enabled": True},
        {"key": "enum_assignmentType", "value": "PRIMARY", "enabled": True},
        {"key": "enum_accessorialType", "value": "DETENTION", "enabled": True},
        {"key": "enum_shipmentCostCategory", "value": "FUEL", "enabled": True},
        {"key": "enum_shipmentCostBasis", "value": "ESTIMATED", "enabled": True},
        {"key": "enum_shipmentCostSourceType", "value": "MANUAL_ENTRY", "enabled": True},
        {"key": "enum_legacyInvoiceStatus", "value": "DRAFT", "enabled": True},
        {"key": "enum_legacyInvoiceType", "value": "STANDARD", "enabled": True},
        {"key": "enum_paymentStatus", "value": "PENDING", "enabled": True},
        {"key": "enum_taxRequirementReason", "value": "STANDARD_SALES_TAX", "enabled": True},
        {"key": "enum_taxRequirement", "value": "TAXABLE", "enabled": True},
        {"key": "enum_workerClassification", "value": "EMPLOYEE", "enabled": True},
        {"key": "enum_payMethod", "value": "PER_MILE", "enabled": True},
        {"key": "enum_mileageBasis", "value": "CONTRACT_MILES", "enabled": True},
        {"key": "enum_sourceClassification", "value": "AUTHORITATIVE_DB", "enabled": True}
    ]

    environment_data = {
        "id": "logisticsx-env-v1",
        "name": "LogisticsX Environment",
        "values": env_vars,
        "_postman_variable_scope": "environment"
    }

    with open("postman/LogisticsX.postman_environment.json", "w") as f:
        json.dump(environment_data, f, indent=2)
    print("Saved postman/LogisticsX.postman_environment.json")

if __name__ == "__main__":
    main()
