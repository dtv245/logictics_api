# Logistics App — Backend & Executive Dashboard Implementation Plan

> **Purpose:** Production-readiness implementation plan for backend gaps, Executive Overview reporting, Operations Dashboard restoration, and dependency/security gates.

---

## 1. Objectives

The implementation has four primary objectives:

1. Remove production-blocking backend/security issues.
2. Establish trustworthy backend reporting APIs for Executive Overview.
3. Restore the real-data Operations Dashboard as a separate operational screen.
4. Prevent the frontend from fabricating, extrapolating, or deriving unsafe executive metrics.

The core principle is:

> **A visibly unavailable metric is safer than a fabricated executive number.**

The frontend must fail closed whenever the backend cannot provide a trustworthy source.

---

# 2. Implementation Rules

These rules apply to every task in this plan.

## 2.1 Backend owns aggregation

Company-wide metrics must be aggregated server-side.

Never calculate company totals from paginated CRUD endpoints.

`MAX_PAGE_SIZE = 100` must never cause the frontend to silently treat the first 100 records as the complete population.

---

## 2.2 No fabricated data

Do not:

* invent API endpoints without defining their backend contract;
* fabricate Executive Dashboard values;
* extrapolate missing metrics;
* substitute unrelated metrics;
* calculate HHI from top-N customer rows;
* derive maintenance metrics without maintenance data;
* derive DSO without receivables/payment data;
* invent accounting period-close semantics.

When a metric cannot be calculated reliably:

```text
availability = unavailable
```

The UI must render its empty/unavailable state.

---

## 2.3 Security comes from backend identity

Never trust caller-supplied identity for authorization.

Authenticated user identity must originate from the validated JWT principal.

Tenant identity must originate from the backend-approved JWT tenant claim.

Frontend must NOT invent or send `X-Tenant`.

---

## 2.4 Benchmark provenance

Executive Dashboard references must remain distinguishable:

```text
T = Internal Target
I = Industry Benchmark
H = Historical Baseline
```

An Internal Target must never be rendered as an Industry Benchmark.

Industry benchmarks must include:

* source;
* source period;
* applicable fleet type/scope.

If the benchmark does not apply:

```text
industry = null
```

---

# 3. Target Architecture

```text
Application
│
├── Executive Overview
│   │
│   ├── North Star KPIs
│   ├── Executive Insights
│   ├── Financial Performance
│   ├── Operational Efficiency
│   ├── Fleet Health
│   └── Customer Concentration
│
└── Operations Dashboard
    │
    ├── Vehicle counts
    ├── Load counts
    ├── Trip counts
    ├── OperationsChart
    ├── VehicleTrackingMap
    └── Real operational API data
```

These are separate products/screens.

Executive Overview is for:

* CEO;
* COO;
* CFO;
* senior management.

Operations Dashboard is for:

* dispatcher;
* operations staff;
* fleet operations.

Do NOT merge the old operational dashboard into the Executive Overview page.

---

# 4. Implementation Phases

---

# PHASE 0 — Production Blockers

Priority: **CRITICAL**

Complete these tasks before relying on the application for production traffic.

---

## TASK P0-01 — BE-001 CORS

**Status:** BLOCKED

### Goal

Implement explicit Spring CORS configuration for the SPA.

### Approved development origin

```text
http://localhost:5173
```

### Required methods

```text
GET
POST
PUT
DELETE
OPTIONS
```

### Required request headers

```text
Authorization
Content-Type
X-Request-Id
```

### Exposed response headers

```text
Content-Disposition
```

### Requirements

* exact origin whitelist;
* no wildcard origin;
* OPTIONS preflight works;
* Bearer-token authentication remains intact;
* credentials remain disabled.

### Acceptance

Real browser preflight from the approved SPA origin succeeds.

Unknown origins do not receive approved CORS access.

---

## TASK P0-02 — BE-006 Health / Readiness

**Status:** BLOCKED

### Problem

`/api/health` currently reports fake health.

### Goal

Provide truthful application/database readiness.

Use either:

```text
/api/health
```

or, if Actuator is the intended architecture:

```text
/actuator/health
```

Do not maintain two competing health systems.

### Acceptance

Database failure must not produce:

```json
{
  "status": "UP"
}
```

Sensitive infrastructure information must not leak through the endpoint.

---

## TASK P0-03 — BE-007 / BE-008 HTTP Contract

### Goal

Align runtime HTTP behavior with OpenAPI.

### Required semantics

```text
Create success             → 201
Invalid input/type         → 400
Unsupported method         → 405
Unsupported media type     → 415
Unexpected server failure  → 500
```

### Work

Fix:

* create response annotations;
* nullable OpenAPI fields;
* documented error responses;
* invalid UUID handling;
* missing parameter handling;
* unsupported method handling;
* unsupported content type handling.

### Acceptance

Framework-level client errors no longer incorrectly become `500 INTERNAL_ERROR`.

---

## TASK P0-04 — BE-009 / BE-010 / BE-011 Security Contract

### Tenant

Source of truth:

```text
validated JWT tenant claim
```

Frontend must not send:

```text
X-Tenant
```

Remove stale documentation claiming otherwise.

### Logout

Default Spring `/logout` must not masquerade as OIDC logout.

Frontend logout must eventually use Identity Server metadata.

### Roles

JWT role converter must whitelist supported roles.

Do not convert arbitrary strings into application authorities.

### Acceptance

Unknown JWT role strings do not automatically receive authorities.

---

# PHASE 1 — Domain Correctness

---

## TASK P1-01 — BE-012 Invoice Status

Normalize invoice states.

Do not scatter:

```java
equalsIgnoreCase(...)
```

through business logic.

Prefer a canonical enum/domain representation.

Example:

```text
DRAFT
ISSUED
PAID
CANCELLED
```

Exact values must follow the existing domain.

---

## TASK P1-02 — BE-013 Currency

Validate currencies against ISO 4217.

Requirements:

* canonical casing;
* invalid codes rejected;
* standard 400 validation response;
* no silent fallback currency.

Do not invent accounting rounding policy.

If no rounding policy exists, document the gap.

---

# PHASE 2 — Upload Security

---

## TASK P2-01 — BE-005 Document Upload

`documentUpload` must remain:

```text
FEATURE FLAG = OFF
```

until server-side controls exist.

Investigate:

* maximum size;
* MIME allowlist;
* content sniffing;
* filename handling;
* malware/quarantine;
* authorization;
* tenant isolation;
* signed/private URLs;
* idempotency.

Frontend validation does not count as a security control.

Do not invent arbitrary limits without product/security requirements.

---

# PHASE 3 — Identity & Messaging

---

## TASK P3-01 — BE-002 Identity Server

**External dependency**

Required information:

```text
OIDC discovery URL
SPA client ID
redirect URIs
scopes
token renewal mechanism
logout metadata
```

If unavailable:

```text
STATUS = BLOCKED
```

Do not fabricate configuration.

---

## TASK P3-02 — BE-003 Authenticated Employee

Determine whether JWT contains a trustworthy employee mapping.

Preferred:

```text
JWT principal
      ↓
employeeId
```

If no trustworthy mapping exists, implement/design:

```http
GET /api/me
```

Never ask users to select themselves from an employee list.

---

## TASK P3-03 — BE-004 Messaging Authorization

Remove trust in caller-supplied employee identity.

Determine semantics for:

```text
my notifications
mark my notifications read
mark all my notifications read
tenant-wide notifications
admin notifications
```

Per-user operations must derive identity from authenticated principal.

---

# PHASE 4 — Executive Reporting Foundation

Executive Dashboard source:

```text
src/pages/dashboard/DashboardPage.tsx
```

Metric registry:

```text
src/features/executive/executive.metrics.ts
```

Reference registry:

```text
src/features/executive/executive.refs.ts
```

Every metric retains its own availability state.

---

# TASK P4-01 — BE-014 Executive Summary

Proposed endpoint:

```http
GET /api/reports/executive-summary
```

Required fields:

```text
fleetSize
fleetUtilizationPct
loadedMilesPct
onTimeDeliveryPct
difotPct
```

### Requirements

For every percentage define:

```text
numerator
denominator
reporting period
source records
zero denominator behavior
```

Aggregation must be:

```text
server-side
tenant-scoped
complete population
```

### Required test

Dataset with:

```text
> 100 records
```

must return correct totals.

---

# TASK P4-02 — BE-015 Monthly Financials

Proposed endpoint:

```http
GET /api/reports/financials/monthly
```

Required monthly fields:

```text
month
revenue
operatingCost
revenuePerMile
costPerMile
contributionSpread
totalMiles
```

Formula:

```text
Contribution Spread
= Revenue per Mile - Cost per Mile
```

### Critical rule

Do not assume:

```text
invoice.createdAt = recognized revenue date
```

unless the accounting model explicitly establishes that rule.

Accounting period finalization must also not be invented.

---

# TASK P4-03 — BE-016 Cost Structure

Proposed endpoint:

```http
GET /api/reports/costs/by-category
```

Required row:

```text
labelKey
currentPerMile
previousPerMile
shareOfTotal
```

Potential categories:

```text
fuel
driver labor
maintenance
insurance
lease/depreciation
tolls
other
```

Each category must be classified:

```text
AVAILABLE
DERIVABLE
MISSING SOURCE
```

Never create a cost category without real persisted source data.

---

# TASK P4-04 — BE-017 Fleet Health

Proposed endpoint:

```http
GET /api/reports/fleet/health
```

Required metrics:

```text
unplannedDowntimePct
pmCompliancePct
maintenanceCostPerMile
breakdownsPer100kMiles
```

Before implementing, verify existence of:

```text
maintenance events
PM schedules
breakdown events
out-of-service timestamps
vehicle mileage
maintenance cost
```

### Critical rule

No PM schedule means:

```text
pmCompliancePct = unavailable
```

Do not invent PM compliance.

---

# TASK P4-05 — BE-018 Customer Concentration

Proposed endpoint:

```http
GET /api/reports/customers/concentration
```

Per-customer fields:

```text
customerId
name
revenue
shareOfTotal
yoyGrowthPct
grossMarginPct
onTimePct
dsoDays
```

Summary:

```text
top1Share
top3Share
top5Share
hhi
```

### HHI rule

HHI must use **ALL customers in scope**.

Conceptually:

```text
HHI = Σ(customer revenue share²)
```

Never calculate HHI from the displayed Top-N list.

### Required test

Use:

```text
> 100 customers
```

and verify HHI against the complete population.

### Frontend visualization

Keep:

```text
Horizontal Revenue Bars
+
Customer Table
```

Do NOT implement Pareto as the primary Executive visualization.

---

# TASK P4-06 — BE-019 Receivables / DSO

Proposed endpoint:

```http
GET /api/reports/receivables/aging
```

Required:

```text
dsoDays
aging buckets
```

First verify the domain supports:

```text
invoice amount
paid amount
outstanding balance
invoice date
due date
payment date
```

Do not infer receivables from unrelated invoice states.

If payment data is insufficient:

```text
DSO = unavailable
```

---

# PHASE 5 — Executive Frontend Integration

---

## TASK P5-01 — Connect Real Report APIs

Only connect Executive metrics after corresponding backend endpoints are implemented and tested.

Rules:

```text
NO fake fallback
NO CRUD-page aggregation
NO extrapolation
NO substituted KPI
```

On failure:

```text
metric.availability = unavailable
```

The UI renders the existing unavailable frame.

---

## Required mapping

Maintain an explicit mapping:

| Executive Metric       | Backend Endpoint        | Backend Field         | Missing Behavior |
| ---------------------- | ----------------------- | --------------------- | ---------------- |
| Fleet Size             | executive-summary       | fleetSize             | unavailable      |
| Utilization            | executive-summary       | fleetUtilizationPct   | unavailable      |
| Loaded Miles           | executive-summary       | loadedMilesPct        | unavailable      |
| OTD                    | executive-summary       | onTimeDeliveryPct     | unavailable      |
| DIFOT                  | executive-summary       | difotPct              | unavailable      |
| Revenue                | financials/monthly      | revenue               | unavailable      |
| Cost/Mile              | financials/monthly      | costPerMile           | unavailable      |
| Revenue/Mile           | financials/monthly      | revenuePerMile        | unavailable      |
| Cost Structure         | costs/by-category       | rows                  | unavailable      |
| Fleet Health           | fleet/health            | health metrics        | unavailable      |
| Customer Concentration | customers/concentration | concentration metrics | unavailable      |
| DSO                    | receivables/aging       | dsoDays               | unavailable      |

---

# PHASE 6 — Restore Operations Dashboard

---

## TASK P6-01 — Restore Real Operational Dashboard

Do NOT place the old dashboard below Executive Overview.

Restore it as a separate route.

Suggested architecture:

```text
/dashboard
    → Executive Overview

/operations
    → Operations Dashboard
```

Reuse existing modules where valid:

```text
dashboardData.ts
OperationsChart.tsx
VehicleTrackingMap.tsx
```

Restore:

```text
vehicle counts
load counts
trip counts
operations chart
vehicle tracking map
real API integration
```

Do not duplicate existing data-fetching logic unnecessarily.

---

## Operations vs Executive

### Executive Overview asks:

```text
Is the company healthy?
What is off target?
Why?
What requires management attention?
```

### Operations Dashboard asks:

```text
What vehicles are active?
What loads/trips are running?
Where are vehicles?
What is happening operationally now?
```

Do not mix these concerns.

---

# PHASE 7 — Dependency Security

---

## TASK P7-01 — Production npm Audit

Current known residual:

```text
9 advisories
6 moderate
3 high
```

Relevant dependency chain includes:

```text
@ant-design/pro-layout
@refinedev/antd
@refinedev/react-router-v6
@refinedev/simple-rest
decode-uri-component
path-to-regexp
query-string
react-router
react-router-dom
```

Approved stack:

```text
Refine v4
React Router 6
```

Do NOT automatically migrate to Router 7.

Do NOT run:

```bash
npm audit fix --force
```

unless a stack migration is explicitly approved.

---

## Required investigation

For every advisory document:

```text
advisory
dependency path
installed version
patched version
runtime exploitability
current mitigation
upgrade requirement
```

Verify:

* application route constants control destinations;
* untrusted strings are not directly passed to `navigate`;
* untrusted strings are not directly used as link destinations;
* SPA does not rely on vulnerable SSR hydration/deserialization behavior.

---

## Production gate

If advisories remain and policy requires zero vulnerabilities:

```text
PRODUCTION GATE = FAIL
```

Mitigation does not equal a clean audit.

---

# PHASE 8 — Final Production Verification

---

## TASK P8-01 — Re-audit BE-001 → BE-019

Do not trust the historical ticket status.

Verify current:

```text
CODE
+
TESTS
+
RUNTIME CONTRACT
```

Allowed statuses:

```text
RESOLVED
BLOCKED
OPEN
FEATURE-FLAGGED
NOT APPLICABLE
```

A ticket is only `RESOLVED` when:

```text
implementation exists
+
contract is defined
+
relevant tests pass
```

A DTO, TODO, proposed endpoint, interface, or documentation entry alone does NOT count as resolved.

---

# 5. Recommended Execution Order

Execute in this order:

```text
P0-01  CORS
   ↓
P0-02  Health
   ↓
P0-03  HTTP/OpenAPI
   ↓
P0-04  Security Contract
   ↓
P1-01  Invoice Status
   ↓
P1-02  Currency
   ↓
P3-01  Identity Server Investigation
   ↓
P3-02  Authenticated Employee
   ↓
P3-03  Messaging Authorization
   ↓
P4-01  Executive Summary
   ↓
P4-02  Monthly Financials
   ↓
P4-03  Cost Structure
   ↓
P4-04  Fleet Health
   ↓
P4-05  Customer Concentration
   ↓
P4-06  Receivables / DSO
   ↓
P5-01  Executive Frontend Integration
   ↓
P6-01  Operations Dashboard Restoration
   ↓
P2-01  Upload Security
   ↓
P7-01  Dependency Audit
   ↓
P8-01  Final Production Verification
```

---

# 6. Agent Execution Protocol

Because the repository previously experienced context/autocompact pressure, coding agents must work incrementally.

## Before every task

Run only targeted discovery.

Example:

```bash
git status --short

rg -n "RelevantClass|RelevantEndpoint|RelevantSymbol" src
```

Do not start by reading the entire repository.

---

## File reading

Read only required sections.

Prefer:

```bash
sed -n '1,160p' path/to/file
```

over:

```bash
cat path/to/large-file
```

Avoid reading:

```text
node_modules
.git
dist
build
coverage
large generated files
large JSON dumps
lockfiles unless dependency task requires them
```

---

## During implementation

One task = one focused context.

Do not opportunistically fix unrelated tickets.

If another problem is discovered:

```text
document it
do not expand scope
```

unless it blocks the current task.

---

## After every task

Run:

```bash
targeted tests
git diff --stat
git diff -- <changed files>
```

Then record:

```text
Task:
Status:
Root cause:
Files changed:
Tests:
Remaining blockers:
```

Commit/checkpoint before starting another large task.

---

# 7. Executive Reporting Correctness Rules

These rules are non-negotiable.

## Never aggregate truncated pages

Forbidden:

```text
GET /customers?pageSize=100
↓
sum revenue
↓
present as company revenue
```

Required:

```text
database/server aggregate
↓
complete tenant-scoped population
↓
report endpoint
↓
Executive Dashboard
```

---

## Never fabricate unavailable KPI

Forbidden:

```javascript
costPerMile = apiValue ?? 1.70;
```

Required:

```javascript
if (apiValue == null) {
    availability = "unavailable";
}
```

---

## Never fake benchmark provenance

Forbidden:

```text
Internal target → displayed as Industry Benchmark
```

Required:

```text
Internal Target       → T
Industry Benchmark    → I
Historical Baseline   → H
```

---

# 8. Customer Concentration Decision

Final Executive Overview decision:

```text
Horizontal Bar Chart
+
Customer Detail Table
```

Primary metrics:

```text
Top 1 Share
Top 3 Share
Top 5 Share
HHI when trustworthy
```

Top 3 customers should be visually highlighted.

Pareto is not required for Executive Overview.

It may later exist in a dedicated Customer Analytics drill-down.

---

# 9. Production Release Checklist

## Security

* [ ] CORS exact-origin policy implemented
* [ ] OPTIONS works
* [ ] JWT tenant is source of truth
* [ ] Unsupported X-Tenant documentation removed
* [ ] Role whitelist implemented
* [ ] OIDC logout contract confirmed
* [ ] `/api/me` or trusted employee mapping exists
* [ ] Messaging identity bound to principal
* [ ] Upload remains disabled until secured

## API correctness

* [ ] Create endpoints document 201
* [ ] Invalid input returns stable 400
* [ ] Unsupported methods return 405
* [ ] Unsupported media types return 415
* [ ] OpenAPI nullability matches runtime
* [ ] Health endpoint reports real readiness

## Executive reporting

* [ ] Executive Summary aggregate
* [ ] Monthly Financial aggregate
* [ ] Cost Structure aggregate
* [ ] Fleet Health aggregate
* [ ] Customer Concentration aggregate
* [ ] Receivables/DSO aggregate
* [ ] Aggregation tested above 100 records
* [ ] HHI uses complete customer population
* [ ] Missing metrics fail closed

## Frontend

* [ ] Executive Overview uses real reporting APIs
* [ ] No fabricated KPI fallback
* [ ] Benchmark provenance preserved
* [ ] Partial API response handled
* [ ] Failed API handled
* [ ] HHI omitted when unavailable
* [ ] Customer concentration uses horizontal bars + table
* [ ] Operations Dashboard restored separately
* [ ] VehicleTrackingMap uses real data
* [ ] OperationsChart uses real data

## Dependencies

* [ ] Production npm audit rerun
* [ ] Advisories documented
* [ ] No forced Router 7 migration
* [ ] Navigation input reviewed
* [ ] Final residual risk documented

---

# 10. Final Verification Commands

Use project-specific commands where different.

Backend:

```bash
./mvnw test
```

or:

```bash
mvn test
```

Frontend:

```bash
npm test
npm run build
npm audit --omit=dev
```

Also inspect:

```bash
git status --short
git diff --stat
```

No unexpected generated files or placeholder files should remain.

---

# 11. Final Release Report

The final verification task must produce:

| Ticket | Status | Evidence | Remaining Blocker |
| ------ | ------ | -------- | ----------------- |
| BE-001 |        |          |                   |
| BE-002 |        |          |                   |
| BE-003 |        |          |                   |
| BE-004 |        |          |                   |
| BE-005 |        |          |                   |
| BE-006 |        |          |                   |
| BE-007 |        |          |                   |
| BE-008 |        |          |                   |
| BE-009 |        |          |                   |
| BE-010 |        |          |                   |
| BE-011 |        |          |                   |
| BE-012 |        |          |                   |
| BE-013 |        |          |                   |
| BE-014 |        |          |                   |
| BE-015 |        |          |                   |
| BE-016 |        |          |                   |
| BE-017 |        |          |                   |
| BE-018 |        |          |                   |
| BE-019 |        |          |                   |

Final result must explicitly state one of:

```text
PRODUCTION GATE: PASS
```

or:

```text
PRODUCTION GATE: FAIL
```

If the result is FAIL, enumerate the exact unresolved blockers.

---

# 12. Definition of Done

The project is not production-ready merely because the dashboard renders.

Production readiness requires:

```text
Trusted authentication
+
Correct authorization
+
Truthful health checks
+
Stable API contracts
+
Server-side reporting aggregation
+
No fabricated executive metrics
+
Correct benchmark provenance
+
Real operational dashboard preserved
+
Passing tests/build
+
Accepted dependency risk
```

The Executive Overview must answer:

> **What happened → Is it good or bad → Why did it happen → Where should management investigate?**

while every number displayed remains traceable to a trustworthy backend source.
