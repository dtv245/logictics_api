# Danh mục API cho frontend

Sinh từ [OpenAPI backend remediation](openapi-backend-remediation.json), bản JAR đã kiểm chứng tại [provenance](openapi-provenance.json). 140 paths / 176 operations. Không lấy contract từ container cũ `:8080`.

Bảng ghi response **thành công** trên wire. Lỗi bảo mật/domain dùng envelope như tài liệu [context](../frontend-backend-integration-context.md). `RAW JSON` cần adapter rõ theo endpoint; frontend `apiClient` mặc định đòi ENVELOPE. Các ràng buộc domain, khả năng thao tác và roles phải đọc context/source; sự tồn tại của route không bảo đảm thao tác được phép.

Schema là tên trong `components.schemas`; mở JSON để xem toàn bộ fields, nested references, required, formats và constraints. Path parameter phải thay bằng ID và URL-encode; query/body phải đúng vị trí. `*/*` trong response OpenAPI không có nghĩa bỏ qua schema.

## accessorial-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| PUT | `/api/accessorial-charges/{id}/approve` | — | path: id (required) | 200 | ENVELOPE | ApiResponseAccessorialChargeView |
| GET | `/api/loads/{loadId}/accessorials` | — | path: loadId (required) | 200 | ENVELOPE | ApiResponseListAccessorialChargeView |
| POST | `/api/loads/{loadId}/accessorials` | application/json: CreateAccessorialChargeRequest | path: loadId (required) | 200 | ENVELOPE | ApiResponseAccessorialChargeView |
| POST | `/api/trip-stops/{stopId}/calculate-detention` | application/json: DetentionCalculationRequest | path: stopId (required) | 200 | ENVELOPE | ApiResponseDetentionCalculationResult |

## billing-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/invoices/billing/primary` | application/json: GenerateInvoiceRequest | — | 200 | ENVELOPE | ApiResponseBillingInvoice |
| GET | `/api/invoices/billing/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseBillingInvoice |
| POST | `/api/invoices/billing/{id}/credit` | application/json: Credit | path: id (required) | 200 | ENVELOPE | ApiResponseBillingInvoice |
| POST | `/api/invoices/billing/{id}/issue` | application/json: Issue | path: id (required) | 200 | ENVELOPE | ApiResponseBillingInvoice |
| POST | `/api/invoices/billing/{id}/rebill` | application/json: Rebill | path: id (required) | 200 | ENVELOPE | ApiResponseBillingInvoice |
| POST | `/api/invoices/billing/{id}/regenerate` | application/json: Regenerate | path: id (required) | 200 | ENVELOPE | ApiResponseBillingInvoice |
| POST | `/api/invoices/billing/{id}/supplemental` | application/json: Supplemental | path: id (required) | 200 | ENVELOPE | ApiResponseBillingInvoice |

## current-user-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/me` | — | — | 200 | ENVELOPE | ApiResponseCurrentUserResponse |

## customer-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/customers` | — | query: search; query: status; query: page = 1; query: pageSize = 20; query: orderBy = name; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseCustomerView |
| POST | `/api/customers` | application/json: CreateCustomerRequest | — | 200 | ENVELOPE | ApiResponseCustomerView |
| DELETE | `/api/customers/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/customers/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseCustomerView |
| PUT | `/api/customers/{id}` | application/json: CreateCustomerRequest | path: id (required) | 200 | ENVELOPE | ApiResponseCustomerView |

## document-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/documents` | — | query: type; query: status; query: loadId; query: truckId; query: employeeId; query: page = 1; query: pageSize = 20; query: orderBy = filename; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseDocumentView |
| DELETE | `/api/documents/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/documents/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDocumentView |

## driver-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/drivers` | — | query: search; query: status; query: page = 1; query: pageSize = 20; query: orderBy = lastname; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseEmployeeView |
| GET | `/api/drivers/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseEmployeeView |

## driver-pay-policy-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/driver-pay-policies` | — | — | 200 | ENVELOPE | ApiResponseListDriverPayPolicyView |
| POST | `/api/driver-pay-policies` | application/json: DriverPayPolicyRequest | — | 200 | ENVELOPE | ApiResponseDriverPayPolicyView |
| GET | `/api/driver-pay-policies/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDriverPayPolicyView |
| POST | `/api/driver-pay-policies/{id}/new-version` | application/json: DriverPayPolicyRequest | path: id (required) | 200 | ENVELOPE | ApiResponseDriverPayPolicyView |

## driver-settlement-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/driver-settlements` | — | query: payPeriodId; query: driverId; query: status; query: settlementType | 200 | ENVELOPE | ApiResponseListDriverSettlementView |
| POST | `/api/driver-settlements/calculate` | application/json: CalculateSettlementRequest | — | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| GET | `/api/driver-settlements/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/adjustments` | application/json: SettlementAdjustmentRequest | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/approve` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/billing-adjustments` | application/json: Adjustment | path: id (required) | 200 | ENVELOPE | ApiResponseImpact |
| POST | `/api/driver-settlements/{id}/lock` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/recalculate-revenue` | application/json: Recalculate | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/require-validation` | application/json: ValidationRequest | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/resolve-validation` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/reversal` | application/json: SettlementReversalRequest | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |
| POST | `/api/driver-settlements/{id}/submit-review` | — | path: id (required) | 200 | ENVELOPE | ApiResponseDriverSettlementView |

## employee-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/employees` | — | query: search; query: status; query: roleId; query: page = 1; query: pageSize = 20; query: orderBy = lastname; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseEmployeeView |
| POST | `/api/employees` | application/json: CreateEmployeeRequest | — | 200 | ENVELOPE | ApiResponseEmployeeView |
| DELETE | `/api/employees/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/employees/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseEmployeeView |
| PUT | `/api/employees/{id}` | application/json: CreateEmployeeRequest | path: id (required) | 200 | ENVELOPE | ApiResponseEmployeeView |

## expense-approval-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/expenses/{id}/approve` | — | path: id (required) | 200 | ENVELOPE | ApiResponseApprovalResult |

## fleet-history-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/fleet/mileage-attributions` | application/json: Attribute | — | 200 | ENVELOPE | ApiResponseMileage |
| POST | `/api/fleet/policies` | application/json: Publish | — | 200 | ENVELOPE | ApiResponsePolicy |
| GET | `/api/fleet/policies/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponsePolicy |
| POST | `/api/fleet/status-events` | application/json: Capture | — | 200 | ENVELOPE | ApiResponseEvent |

## health-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/actuator/health` | — | — | 200 | RAW JSON | object |
| GET | `/api/health` | — | — | 200 | RAW JSON | object |
| GET | `/health` | — | — | 200 | RAW JSON | object |

## inspection-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/inspections` | — | query: loadId; query: type; query: page = 1; query: pageSize = 20; query: orderBy = inspectedat; query: descending = true | 200 | ENVELOPE | ApiResponsePagedResponseInspectionView |
| POST | `/api/inspections` | application/json: CreateInspectionRequest | — | 200 | ENVELOPE | ApiResponseInspectionView |
| DELETE | `/api/inspections/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/inspections/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseInspectionView |
| PUT | `/api/inspections/{id}` | application/json: CreateInspectionRequest | path: id (required) | 200 | ENVELOPE | ApiResponseInspectionView |

## internal-build-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/internal/build` | — | — | 200 | ENVELOPE | ApiResponseIdentity |

## invoice-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/invoices` | — | query: status; query: type; query: customerId; query: employeeId; query: page = 1; query: pageSize = 20; query: orderBy = number; query: descending = true | 200 | ENVELOPE | ApiResponsePagedResponseInvoiceView |
| POST | `/api/invoices` | application/json: CreateInvoiceRequest | — | 200 | ENVELOPE | ApiResponseInvoiceView |
| DELETE | `/api/invoices/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/invoices/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseInvoiceView |
| PUT | `/api/invoices/{id}` | application/json: CreateInvoiceRequest | path: id (required) | 200 | ENVELOPE | ApiResponseInvoiceView |

## lark-auth-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/auth/lark/authorize` | — | query: returnTo | 200 | ENVELOPE | ApiResponseMapStringString |
| POST | `/api/auth/lark/callback` | application/json: LarkCallbackRequest | — | 200 | ENVELOPE | ApiResponseLarkLoginResult |
| GET | `/api/auth/lark/login` | — | query: returnTo | 200 | EMPTY | — |

## lark-base-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/lark/base/loads` | — | query: pageSize = 20; query: pageToken | 200 | ENVELOPE | ApiResponseListLarkBaseRecord |
| POST | `/api/lark/base/sync/driver/{employeeId}` | — | path: employeeId (required) | 200 | ENVELOPE | ApiResponseLarkBaseRecord |
| POST | `/api/lark/base/sync/load/{loadId}` | — | path: loadId (required) | 200 | ENVELOPE | ApiResponseLarkBaseRecord |

## load-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/loads` | — | query: search; query: status; query: customerId; query: truckId; query: dispatcherId; query: page = 1; query: pageSize = 20; query: orderBy = name; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseLoadView |
| POST | `/api/loads` | application/json: CreateLoadRequest | — | 200 | ENVELOPE | ApiResponseLoadView |
| DELETE | `/api/loads/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/loads/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseLoadView |
| PUT | `/api/loads/{id}` | application/json: UpdateLoadRequest | path: id (required) | 200 | ENVELOPE | ApiResponseLoadView |

## load-pickup-business-date-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/loads/{id}/requested-pickup-business-date` | application/json: SetPickupBusinessDateRequest | path: id (required) | 200 | ENVELOPE | ApiResponseLoadView |

## load-timeline-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/loads/{loadId}/timeline` | — | path: loadId (required) | 200 | ENVELOPE | ApiResponseLoadTimelineResponse |

## message-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/messages` | — | query: conversationId (required); query: page = 1; query: pageSize = 50 | 200 | ENVELOPE | ApiResponsePagedResponseMessageView |
| POST | `/api/messages` | application/json: SendMessageRequest | — | 200 | ENVELOPE | ApiResponseMessageView |
| GET | `/api/messages/conversations` | — | query: employeeId (required); query: page = 1; query: pageSize = 20 | 200 | ENVELOPE | ApiResponsePagedResponseConversationView |
| POST | `/api/messages/conversations` | application/json: CreateConversationRequest | — | 200 | ENVELOPE | ApiResponseConversationView |
| GET | `/api/messages/conversations/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseConversationView |
| GET | `/api/messages/unread-count` | — | query: employeeId (required) | 200 | ENVELOPE | ApiResponseLong |

## notification-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/notifications` | — | query: page = 1; query: pageSize = 20 | 200 | ENVELOPE | ApiResponsePagedResponseNotificationView |
| POST | `/api/notifications/mark-all-read` | — | — | 200 | ENVELOPE | ApiResponseInteger |
| GET | `/api/notifications/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseNotificationView |

## optimization-policy-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/optimization/policies` | application/json: PublishRequest | — | 200 | ENVELOPE | ApiResponsePublishedPolicy |
| GET | `/api/optimization/policies/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponsePublishedPolicy |

## optimization-qualified-input-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/optimization/qualified-inputs` | application/json: CaptureRequest | — | 200 | ENVELOPE | ApiResponseCaptured |
| POST | `/api/optimization/qualified-inputs/forecasts` | application/json: CaptureRequest | — | 200 | ENVELOPE | ApiResponseCaptured |
| GET | `/api/optimization/qualified-inputs/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseCaptured |

## optimization-run-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/optimization/runs` | application/json: CreateRequest | — | 200 | ENVELOPE | ApiResponseOutcome |
| GET | `/api/optimization/runs/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseOutcome |
| POST | `/api/optimization/runs/{id}/assignments/{candidateId}/accept` | application/json: AcceptRequest | path: id (required); path: candidateId (required) | 200 | ENVELOPE | ApiResponseAccepted |

## pay-period-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/pay-periods` | — | — | 200 | ENVELOPE | ApiResponseListPayPeriod |
| POST | `/api/pay-periods` | application/json: Request | — | 200 | ENVELOPE | ApiResponsePayPeriod |

## payment-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/payments` | — | query: status; query: invoiceId; query: page = 1; query: pageSize = 20; query: orderBy = recordedat; query: descending = true | 200 | ENVELOPE | ApiResponsePagedResponsePaymentView |
| POST | `/api/payments` | application/json: CreatePaymentRequest | — | 200 | ENVELOPE | ApiResponsePaymentView |
| DELETE deprecated | `/api/payments/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/payments/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponsePaymentView |
| PUT | `/api/payments/{id}` | application/json: UpdatePaymentRequest | path: id (required) | 200 | ENVELOPE | ApiResponsePaymentView |
| POST | `/api/payments/{id}/cancel` | — | path: id (required); query: reason (required) | 200 | ENVELOPE | ApiResponsePaymentView |

## payroll-callback-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/payroll/provider-callbacks/{provider}` | application/json: string | path: provider (required) | 200 | RAW JSON | PayrollPaymentEventView |

## payroll-configuration-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/payroll/configuration/employees/{id}/profiles` | application/json: Profile | path: id (required) | 200 | RAW JSON | ProfileView |
| POST | `/api/payroll/configuration/policy-versions` | application/json: Policy | — | 200 | RAW JSON | PayrollPolicy |
| PUT | `/api/payroll/configuration/tenant-default` | application/json: TenantDefault | — | 200 | RAW JSON | PayrollJurisdiction |

## payroll-payment-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/payroll/items/{id}/no-payment-required` | application/json: NoPaymentRequiredRequest | path: id (required) | 200 | RAW JSON | PayrollRunView |
| GET | `/api/payroll/items/{id}/payments` | — | path: id (required) | 200 | RAW JSON | List<PayrollPaymentView> |
| POST | `/api/payroll/items/{id}/payments` | application/json: SchedulePayrollPaymentRequest | path: id (required) | 200 | RAW JSON | PayrollPaymentView |
| POST | `/api/payroll/payments/{id}/dispatch` | — | path: id (required) | 200 | RAW JSON | PayrollPaymentView |
| POST | `/api/payroll/payments/{id}/reconcile-bank` | application/json: ManualPayrollBankReconciliationRequest | path: id (required) | 200 | RAW JSON | PayrollPaymentEventView |
| GET | `/api/payroll/reconciliation-cases` | — | query: page = 0; query: size = 25 | 200 | RAW JSON | PagePayrollReconciliationCaseView |

## payroll-run-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/payroll/runs/calculate` | application/json: CalculatePayrollRequest | — | 200 | RAW JSON | PayrollRunView |
| GET | `/api/payroll/runs/{id}` | — | path: id (required) | 200 | RAW JSON | PayrollRunView |
| POST | `/api/payroll/runs/{id}/approve` | — | path: id (required) | 200 | RAW JSON | PayrollRunView |
| POST | `/api/payroll/runs/{id}/lock` | — | path: id (required) | 200 | RAW JSON | PayrollRunView |
| POST | `/api/payroll/runs/{id}/recalculate` | — | path: id (required) | 200 | RAW JSON | PayrollRunView |
| POST | `/api/payroll/runs/{id}/submit-review` | — | path: id (required) | 200 | RAW JSON | PayrollRunView |

## payslip-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/driver/me/payslips` | — | — | 200 | RAW JSON | List<PayslipView> |
| POST | `/api/payroll/runs/{id}/payslips` | — | path: id (required) | 200 | RAW JSON | List<PayslipView> |
| GET | `/api/payslips/{id}` | — | path: id (required) | 200 | RAW JSON | PayslipView |
| GET | `/api/payslips/{id}/pdf` | — | path: id (required) | 200 | BINARY | string |

## rate-policy-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/rating/contracts` | application/json: RatingContractRequest | — | 200 | ENVELOPE | ApiResponseRatingContract |
| POST | `/api/rating/contracts/{id}/versions` | application/json: RatingContractRequest | path: id (required); query: expectedVersion (required) | 200 | ENVELOPE | ApiResponseRatingContract |
| GET | `/api/rating/contracts/{id}/versions/{version}` | — | path: id (required); path: version (required) | 200 | ENVELOPE | ApiResponseRatingContract |
| POST | `/api/rating/rules` | application/json: RateRuleRequest | — | 200 | ENVELOPE | ApiResponseRateRule |
| POST | `/api/rating/rules/{id}/versions` | application/json: RateRuleRequest | path: id (required); query: expectedVersion (required) | 200 | ENVELOPE | ApiResponseRateRule |
| GET | `/api/rating/rules/{id}/versions/{version}` | — | path: id (required); path: version (required) | 200 | ENVELOPE | ApiResponseRateRule |

## rating-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/loads/{id}/rating/accept` | application/json: RatingAcceptRequest | path: id (required) | 200 | ENVELOPE | ApiResponseAcceptedRatingSnapshot |
| POST | `/api/loads/{id}/rating/preview` | application/json: RatingPreviewRequest | path: id (required) | 200 | ENVELOPE | ApiResponseRatingPreview |
| GET | `/api/rating/snapshots/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseAcceptedRatingSnapshot |

## rating-mileage-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/loads/{id}/rating/contract-mileage` | application/json: ContractMileageRequest | path: id (required) | 200 | ENVELOPE | ApiResponseContractMileageEvidence |

## report-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/customers/{customerId}/balance` | — | path: customerId (required); query: currency | 200 | ENVELOPE | ApiResponseCustomerBalanceReport |
| GET | `/api/loads/{loadId}/financial-summary` | — | path: loadId (required); query: currency | 200 | ENVELOPE | ApiResponseLoadProfitabilityReport |
| GET | `/api/reports/costs/known-operating-cpm` | — | query: from; query: to; query: currency | 200 | ENVELOPE | ApiResponseKnownOperatingCpmReport |
| GET | `/api/reports/expenses` | — | query: from; query: to; query: currency | 200 | ENVELOPE | ApiResponseExpenseSummaryReport |
| GET | `/api/reports/financials/monthly` | — | query: year (required); query: month (required); query: currency | 200 | ENVELOPE | ApiResponseMonthlyFinancialSummary |
| GET | `/api/reports/fleet/fuel` | — | query: from; query: to; query: currency | 200 | ENVELOPE | ApiResponseFuelReport |
| GET | `/api/reports/fleet/health` | — | query: policyId (required); query: truckIds (required); query: from; query: to; query: firstDate; query: exclusiveLastDate; query: businessZoneId | 200 | ENVELOPE | ApiResponseReport |
| GET | `/api/reports/fleet/maintenance` | — | query: from; query: to; query: truckId; query: currency | 200 | ENVELOPE | ApiResponseMaintenanceSummaryReport |
| GET | `/api/reports/fleet/utilization-history` | — | query: policyId (required); query: truckIds (required); query: from; query: to; query: firstDate; query: exclusiveLastDate; query: businessZoneId | 200 | ENVELOPE | ApiResponseReport |
| GET | `/api/reports/operations/delays` | — | query: from; query: to | 200 | ENVELOPE | ApiResponseDeliveryDelayReport |
| GET | `/api/reports/operations/exceptions-summary` | — | query: from; query: to | 200 | ENVELOPE | ApiResponseExceptionSummaryReport |
| GET | `/api/reports/operations/on-time-delivery` | — | query: from; query: to | 200 | ENVELOPE | ApiResponseOtdReport |
| GET | `/api/reports/operations/transit-time` | — | query: from; query: to | 200 | ENVELOPE | ApiResponseTransitTimeReport |
| GET | `/api/reports/profitability/by-lane` | — | — | 200 | ENVELOPE | ApiResponseListLaneProfitabilityReport |
| GET | `/api/reports/profitability/by-load` | — | query: loadId | 200 | ENVELOPE | ApiResponseListLoadFinancialSummary |
| GET | `/api/reports/profitability/by-truck` | — | — | 200 | ENVELOPE | ApiResponseListTruckProfitabilityReport |
| GET | `/api/reports/revenue` | — | query: loadId (required); query: currency | 200 | ENVELOPE | ApiResponseLoadRevenueSummary |

## role-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/roles` | — | query: page = 1; query: pageSize = 20 | 200 | ENVELOPE | ApiResponsePagedResponseRoleView |
| POST | `/api/roles` | application/json: CreateRoleRequest | — | 200 | ENVELOPE | ApiResponseRoleView |
| DELETE | `/api/roles/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/roles/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseRoleView |
| PUT | `/api/roles/{id}` | application/json: CreateRoleRequest | path: id (required) | 200 | ENVELOPE | ApiResponseRoleView |

## shipment-cost-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/loads/{loadId}/costs` | — | path: loadId (required) | 200 | ENVELOPE | ApiResponseListShipmentCostView |
| POST | `/api/loads/{loadId}/costs` | application/json: CreateShipmentCostRequest | path: loadId (required) | 200 | ENVELOPE | ApiResponseShipmentCostView |
| POST | `/api/loads/{loadId}/costs/allocate-maintenance` | — | path: loadId (required); query: truckMaintenanceCpm (required); query: currency | 200 | ENVELOPE | ApiResponseShipmentCostView |
| POST | `/api/loads/{loadId}/costs/sync-expenses` | — | path: loadId (required) | 200 | ENVELOPE | ApiResponseInteger |

## tax-assessment-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/invoices/tax-assessments` | application/json: TaxAssessmentRequest | — | 200 | ENVELOPE | ApiResponseTaxAssessment |
| GET | `/api/invoices/tax-assessments/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTaxAssessment |

## trip-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/trips` | — | query: search; query: status; query: truckId; query: page = 1; query: pageSize = 20; query: orderBy = name; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseTripView |
| POST | `/api/trips` | application/json: CreateTripRequest | — | 200 | ENVELOPE | ApiResponseTripView |
| DELETE | `/api/trips/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/trips/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTripView |
| PUT | `/api/trips/{id}` | application/json: UpdateTripRequest | path: id (required) | 200 | ENVELOPE | ApiResponseTripView |

## trip-execution-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| POST | `/api/trip-stops/{id}/arrive` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTripStopView |
| POST | `/api/trip-stops/{id}/complete-service` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTripStopView |
| POST | `/api/trip-stops/{id}/depart` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTripStopView |
| POST | `/api/trip-stops/{id}/start-service` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTripStopView |
| GET | `/api/trips/{tripId}/drivers` | — | path: tripId (required) | 200 | ENVELOPE | ApiResponseListTripDriverAssignmentView |
| POST | `/api/trips/{tripId}/drivers` | application/json: AssignDriverRequest | path: tripId (required) | 200 | ENVELOPE | ApiResponseTripDriverAssignmentView |
| POST | `/api/trips/{tripId}/drivers/{assignmentId}/unassign` | — | path: tripId (required); path: assignmentId (required) | 200 | ENVELOPE | ApiResponseTripDriverAssignmentView |
| GET | `/api/trips/{tripId}/stops` | — | path: tripId (required) | 200 | ENVELOPE | ApiResponseListTripStopView |

## truck-controller

| Method | URL | Body (content type / schema) | Parameters | HTTP success theo OpenAPI | Response mode | Response schema |
|---|---|---|---|---|---|---|
| GET | `/api/trucks` | — | query: search; query: status; query: type; query: page = 1; query: pageSize = 20; query: orderBy = number; query: descending = false | 200 | ENVELOPE | ApiResponsePagedResponseTruckView |
| POST | `/api/trucks` | application/json: CreateTruckRequest | — | 200 | ENVELOPE | ApiResponseTruckView |
| DELETE | `/api/trucks/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseVoid |
| GET | `/api/trucks/{id}` | — | path: id (required) | 200 | ENVELOPE | ApiResponseTruckView |
| PUT | `/api/trucks/{id}` | application/json: UpdateTruckRequest | path: id (required) | 200 | ENVELOPE | ApiResponseTruckView |

## Giới hạn của OpenAPI

- `POST /api/payments` trả 201 cả replay cùng ID; OpenAPI không mô tả mọi lỗi/domain guard.
- `GET /api/auth/lark/login` có response 200 theo generated OpenAPI, nhưng controller thực tế trả 302 Location redirect. Dùng public `/authorize` cho JSON SPA flow.
- `DELETE /api/payments/{id}` vẫn hiện response 200 từ chữ ký controller, nhưng service luôn từ chối actor hợp lệ bằng 409 `PAYMENT_DELETE_FORBIDDEN`. Frontend phải tắt physical delete.
- Required/type/constraints không thay thế conditional validation (Lark error callback), membership, invoice eligibility, lock/version hoặc source provenance.
- Generic CORE status/type còn là string; enum từ client cũ không trở thành enum backend chỉ vì đã có TypeScript union.
- Không có collection GET `/api/payroll/runs` hay API refresh/password-login/tenant-switch chung trong snapshot. Không tự suy endpoint từ resource name.
