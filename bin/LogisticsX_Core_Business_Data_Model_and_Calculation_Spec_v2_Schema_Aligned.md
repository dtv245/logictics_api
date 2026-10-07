# LogisticsX Core Business Data Model & Calculation Specification

**Version:** 2.0 — Schema-Aligned Revision  
**Target:** Existing LogisticsX backend/database  
**Stack:** Spring Boot 3 / Spring 6 / JPA-Hibernate / PostgreSQL / Redis  
**Source of truth for this revision:** Current LogisticsX DBML/schema supplied by the project  
**Previous document:** `LogisticsX_Core_Business_Data_Model_and_Calculation_Spec.md`

---

# 0. What changed in Version 2

Version 1 described a clean target architecture for rating, costing, settlement, payroll, reporting, and optimization.

Version 2 changes the design so it fits the **actual LogisticsX schema** instead of assuming a greenfield database.

The main rule for this revision is:

```text
Reuse existing data first.
Alter an existing table when that preserves its real business responsibility.
Create a new table only when the existing schema cannot safely represent the new business concept.
```

Every relevant table is classified as:

```text
KEEP
    Keep the table and use it as an authoritative source.

ALTER
    Keep the table but add fields/constraints/relationships.

NEW
    Introduce a new table because the concept does not currently exist.

DEPRECATE
    Keep existing data for compatibility, but stop using the field for new business logic.
```

The most important corrections from Version 1 are:

```text
1. KEEP `expenses`.
   Do not introduce `expense_receipts` as a parallel primary expense model.

2. Do not introduce `fuel_transactions` immediately.
   Fuel can be represented through `expenses` in V1.
   A dedicated fuel-card table should be introduced only when provider integration requires it.

3. KEEP the existing HOS subsystem:
   hos_logs
   hos_violations
   driver_hos_statuses
   eld_driver_mappings
   eld_vehicle_mappings

4. NEW `trip_driver_assignments` is mandatory.
   A Trip currently points to a Truck but does not preserve driver assignment history.

5. KEEP customer billing in:
   invoices
   invoice_line_items
   payments

6. Stop using invoices as the long-term payroll model.
   Payroll must move to:
   settlements
   payroll_runs
   payslips

7. NEW `shipment_costs` remains necessary.
   Existing expenses and maintenance records are source documents;
   `shipment_costs` becomes the canonical load/trip cost ledger.

8. NEW `calculation_snapshots` remains necessary.
   Financial and optimization calculations must be auditable.

9. Several metrics can be calculated immediately from the current schema.
   They should be implemented before adding every target table.
```

---

# 1. Current LogisticsX schema — business inventory

The current database already contains the following major capabilities.

## 1.1 Customer and tenancy

```text
customers
customer_users

tenant_roles
tenant_role_claims

terminals
```

## 1.2 Load and dispatch

```text
loads
trips
trip_stops

load_board_configurations
load_board_listings
posted_trucks

ai_dispatch_sessions
ai_dispatch_decisions

tracking_links
load_exceptions
load_condition_reports
condition_defects
```

## 1.3 Fleet

```text
trucks

maintenance_schedules
maintenance_records
maintenance_parts

dvir_reports
dvir_defects

accident_reports
accident_third_parties
accident_witnesses
```

## 1.4 Drivers / employees / compliance

```text
employees
driver_licenses

hos_logs
hos_violations
driver_hos_statuses

driver_behavior_events

eld_provider_configurations
eld_driver_mappings
eld_vehicle_mappings

time_entries
```

## 1.5 Finance

```text
expenses

invoices
invoice_line_items

payments
payment_links
```

## 1.6 Documents and communication

```text
documents

conversations
conversation_participants
messages
message_read_receipts

notifications
telegram_chats
```

This means LogisticsX already has enough raw data to implement a useful **Calculation V1** before the complete accounting/payroll model is added.

---

# 2. KEEP / ALTER / NEW matrix

## 2.1 Core operational tables

| Table | Action | Reason |
|---|---|---|
| `loads` | KEEP + small ALTER later | Core shipment/order aggregate already exists |
| `trips` | ALTER | Distance semantics are ambiguous and driver assignment is absent |
| `trip_stops` | ALTER | Missing appointment/service/departure timestamps required for dwell/detention |
| `trucks` | KEEP | Current truck identity/status/location is already represented |
| `employees` | KEEP | Employee/driver identity and basic salary remain useful |
| `expenses` | ALTER | Existing expense model is good; add business links instead of replacing it |
| `maintenance_records` | KEEP | Authoritative maintenance transaction/source |
| `maintenance_schedules` | KEEP | Preventive maintenance planning already exists |
| `invoices` | KEEP for customer billing; DEPRECATE payroll semantics | Customer AR model is useful but payroll must be separated |
| `invoice_line_items` | KEEP | Useful for revenue breakdown and invoice reconciliation |
| `payments` | KEEP for customer payments | Do not use this table as driver payroll payment ledger |
| `time_entries` | KEEP | Useful for hourly employees / hourly driver pay |
| `hos_logs` | KEEP | Historical HOS source |
| `hos_violations` | KEEP | Compliance source |
| `driver_hos_statuses` | KEEP | Excellent dispatch feasibility input |
| `driver_behavior_events` | KEEP | Safety/performance source |
| `load_board_listings` | KEEP | Market-rate / external-load source |
| `load_exceptions` | KEEP | Operational exception source |
| `documents` | KEEP | POD/BOL/receipt/document storage relationship already exists |

---

## 2.2 New tables required for the calculation architecture

### V1 critical

```text
trip_driver_assignments
load_events
shipment_costs
calculation_snapshots
```

### V2 driver compensation

```text
driver_pay_policies
pay_periods
settlements
settlement_lines

payroll_runs
payroll_run_items
payslips
payroll_payments
```

### V2/V3 pricing and accessorials

```text
rate_rules
accessorial_charges
```

### V3/V4 optimization audit

```text
optimization_runs
optimization_assignments
```

### Optional later

```text
vehicle_status_events
```

Only add `vehicle_status_events` when historical fleet utilization is implemented.

---

# 3. Current fields that must be treated carefully

# 3.1 `loads.distance`

Current schema contains:

```text
loads.distance DOUBLE PRECISION NOT NULL
```

The schema does not prove whether this value means:

```text
planned routing distance
quoted distance
actual GPS distance
loaded distance
total trip distance
```

Therefore Version 2 must not silently call this:

```text
actualMiles
```

Recommended temporary semantic:

```text
recordedLoadDistance
```

The API must expose the basis if this field is used in a KPI.

Example:

```json
{
  "revenuePerMile": 2.50,
  "distance": 1300,
  "distanceBasis": "LEGACY_LOAD_DISTANCE"
}
```

---

# 3.2 `trips.total_distance`

The same issue exists for:

```text
trips.total_distance
```

Do not assume it is actual ELD/GPS mileage without confirming how the application populates it.

Recommended ALTER:

```text
planned_distance_miles
actual_distance_miles
loaded_miles
empty_miles
```

Keep `total_distance` temporarily for backward compatibility.

---

# 3.3 `loads.delivery_cost_amount`

Current Load has:

```text
delivery_cost_amount
delivery_cost_currency
```

This field is not sufficiently self-describing to safely decide whether it is:

```text
quoted customer revenue
estimated carrier cost
estimated internal delivery cost
actual delivery cost
a generic delivery fee
```

Therefore:

```text
DO NOT USE delivery_cost_amount IN PROFIT FORMULAS
until the existing Java/domain semantic is audited.
```

If the field is confirmed to mean **estimated load cost**, map it to:

```text
estimatedCost
```

If it means **customer quoted revenue**, it belongs on the revenue side instead.

Do not make this decision from the DBML alone.

---

# 3.4 `invoices`

The current invoice table contains both normal customer billing fields and fields that look payroll-related:

```text
load_id
customer_id

employee_id
period_start
period_end
total_distance_driven
total_hours_worked
```

This is a domain smell.

Target architecture:

```text
CUSTOMER INVOICE
    invoices
    invoice_line_items
    payments

DRIVER COMPENSATION
    settlements
    payroll_runs
    payslips
    payroll_payments
```

Do not drop historical invoice columns immediately.

Instead:

```text
Phase 1:
    stop adding new payroll behavior to invoices

Phase 2:
    migrate historical payroll use if necessary

Phase 3:
    deprecate/remove employee/payroll-specific invoice columns
```

---

# 3.5 `employees.salary_type` and `employees.salary_amount`

Keep these fields.

They remain valid for:

```text
salary employees
hourly employees
simple fixed compensation
legacy behavior
```

But they are not sufficient for trucking driver compensation such as:

```text
PER_MILE
PER_LOAD
PERCENT_REVENUE
DETENTION
LAYOVER
STOP_PAY
BONUS
DEDUCTION
```

Therefore:

```text
employees salary
    = employee HR/base-pay concept

driver_pay_policies
    = operational driver compensation policy
```

---

# 4. Calculation V1 — formulas available immediately

This section deliberately uses only data already available in the current database.

The rule is:

```text
Do not invent missing data.
If a required denominator/source is unavailable,
return the metric as UNAVAILABLE or PARTIAL.
```

---

# 5. Revenue V1

## 5.1 Source

Use:

```text
invoices
invoice_line_items
```

For a Load:

```text
invoices.load_id = load.id
```

Revenue before tax:

```text
LoadRevenue =
    SUM(eligible invoices.subtotal_amount)
```

Do not use tax as operating revenue by default.

Therefore:

```text
revenue = subtotal
```

unless accounting policy explicitly defines otherwise.

---

## 5.2 Currency condition

Only sum records where:

```text
subtotal_currency == reportCurrency
```

unless an explicit FX conversion engine exists.

Do not calculate:

```text
100 USD + 1,000,000 VND
```

as a valid monetary total.

---

## 5.3 Invoice eligibility

The DBML only shows `invoices.status` as text.

It does not list the actual Java enum values.

Therefore:

```text
EligibleInvoiceStatus
```

must be resolved from the current backend enum/domain implementation.

The calculation code should not invent statuses from the DBML.

Recommended interface:

```java
public interface InvoiceRevenuePolicy {

    boolean countsAsRevenue(InvoiceStatus status);
}
```

---

# 6. Invoice reconciliation V1

Current schema contains:

```text
invoices.subtotal_amount

invoice_line_items.amount_amount
invoice_line_items.invoice_id
```

Invariant:

```text
InvoiceSubtotal
==
SUM(InvoiceLineAmounts)
```

after the application's defined line-level rounding rules.

Pseudo-code:

```java
BigDecimal lineSubtotal =
    invoice.getLineItems().stream()
        .map(InvoiceLineItem::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

if (lineSubtotal.compareTo(invoice.getSubtotal()) != 0) {
    throw new InvoiceReconciliationException(...);
}
```

Do not hide the mismatch using an arbitrary epsilon for money.

---

# 7. Customer payment and open balance V1

Current source:

```text
payments.invoice_id
payments.amount_amount
payments.amount_currency
payments.status
```

Paid amount:

```text
PaidAmount =
    SUM(eligible completed payments)
```

Open balance:

```text
OpenBalance =
    InvoiceTotal - PaidAmount
```

Customer open balance:

```text
CustomerBalance =
    SUM(OpenBalance for eligible customer invoices)
```

Report endpoint:

```text
GET /api/customers/{customerId}/balance
```

Future endpoint:

```text
GET /api/reports/receivables/aging
```

---

# 8. Revenue per mile V1

If using the legacy load distance:

```text
RevenuePerRecordedLoadMile =
    LoadRevenue / loads.distance
```

If using the trip distance:

```text
RevenuePerRecordedTripMile =
    TripRevenue / trips.total_distance
```

The response must identify the distance basis.

Do not call either metric `actualRevenuePerMile` until actual mileage semantics are established.

---

# 9. Expense cost V1

Current authoritative source:

```text
expenses
```

Relevant fields already include:

```text
status
type
category
expense_date

amount_amount
amount_currency

truck_id

odometer_reading
quantity
quantity_unit

receipt_blob_path
approved_at
```

For an approved expense population:

```text
ApprovedExpenseCost =
    SUM(expenses.amount_amount)
```

by:

```text
truck
period
category
currency
```

This can support V1 fleet operating cost reporting immediately.

---

# 10. Fuel V1 using `expenses`

Do not create a second primary fuel table in V1.

Normalize the expense category to a business enum:

```java
public enum ExpenseCategory {
    FUEL,
    TOLL,
    PARKING,
    SCALE,
    REPAIR,
    TIRE,
    LUMPER,
    HOTEL,
    PERMIT,
    OTHER
}
```

Fuel cost:

```text
FuelCost =
    SUM(approved expenses where category == FUEL)
```

If:

```text
quantity
quantity_unit
```

represent fuel volume, normalize units before computing consumption.

Example normalized units:

```java
public enum QuantityUnit {
    GALLON,
    LITER,
    UNIT,
    HOUR,
    MILE,
    OTHER
}
```

---

# 11. MPG V1

Only calculate MPG if:

```text
Fuel volume is known
AND
distance basis is valid
```

Formula:

```text
MPG =
    MilesDriven / GallonsConsumed
```

If the input fuel quantity is liters:

```text
convert liters to gallons
```

through a unit-conversion service before calculating the U.S. MPG metric.

Do not infer gallons from `quantity` without `quantity_unit`.

---

# 12. Maintenance cost V1

Current authoritative source:

```text
maintenance_records
```

Relevant fields:

```text
truck_id
service_date
odometer_reading

labor_cost
parts_cost
total_cost
```

Invariant:

```text
MaintenanceTotal =
    LaborCost + PartsCost
```

The current schema also contains:

```text
maintenance_parts
```

Therefore an optional second reconciliation is:

```text
maintenance_records.parts_cost
==
SUM(maintenance_parts.total_cost)
```

where applicable.

---

# 13. Maintenance CPM V1

If truck mileage for the same period is usable:

```text
MaintenanceCPM =
    MaintenanceCost / TruckMiles
```

Return:

```text
numerator = maintenance cost
denominator = mileage
distance basis
```

Example response:

```json
{
  "value": 0.15,
  "unit": "USD_PER_MILE",
  "numerator": 6000,
  "denominator": 40000,
  "distanceBasis": "RECORDED_TRIP_DISTANCE"
}
```

---

# 14. Known operating CPM V1

The current database can calculate a **partial** CPM.

Example numerator:

```text
KnownOperatingCost =
    ApprovedFuelExpenses
  + ApprovedTolls
  + ApprovedParking
  + ApprovedOtherOperatingExpenses
  + MaintenanceCost
```

Then:

```text
KnownOperatingCPM =
    KnownOperatingCost / EligibleMiles
```

Do **not** call this full `TotalCPM` yet.

Why:

```text
driver settlement cost is not fully modeled
fixed cost allocation is not modeled
insurance allocation may be missing
equipment/depreciation allocation may be missing
direct load attribution is incomplete
```

Recommended API field:

```text
knownOperatingCostPerMile
```

with metadata:

```json
{
  "driverCostIncluded": false,
  "fixedCostIncluded": false,
  "allocationComplete": false
}
```

---

# 15. Double-count prevention: expenses vs maintenance

The current schema can represent:

```text
a repair expense in `expenses`

AND

a maintenance transaction in `maintenance_records`
```

without a direct relationship between them.

If both are summed, the report may double-count the same repair.

Therefore V2 should ALTER `expenses`:

```text
maintenance_record_id UUID NULL
```

and apply:

```text
If expense is the financial source for a maintenance record:
    link them

When computing total cost:
    count one canonical financial transaction
    not both representations
```

Until the relationship exists, the report should use an explicit source precedence policy.

Recommended temporary policy:

```text
Maintenance cost category:
    use maintenance_records

Expense operating categories:
    exclude expense rows identified as duplicated maintenance/repair
```

This rule must be tested against current production data before enabling it globally.

---

# 16. OTD V1

Current Load already contains:

```text
requested_delivery_date
delivered_at
```

Therefore basic V1:

```text
OnTime =
    delivered_at <= requested_delivery_date
```

OTD:

```text
OTD% =
    OnTimeEligibleDeliveredLoads
    / EligibleDeliveredLoads
    × 100
```

Return both numerator and denominator.

The current schema does not provide a delivery window start/end on the load, therefore this is a **single-deadline V1 metric**.

---

# 17. Delivery delay V1

For late delivered loads:

```text
DeliveryDelayMinutes =
    max(
        0,
        delivered_at - requested_delivery_date
    )
```

Useful reports:

```text
average delay
median delay
P95 delay
late-load count
```

---

# 18. Transit time V1

Current Load:

```text
picked_up_at
delivered_at
```

Formula:

```text
TransitTime =
    delivered_at - picked_up_at
```

Required validation:

```text
delivered_at >= picked_up_at
```

---

# 19. Dispatch-to-pickup V1

Current Load:

```text
dispatched_at
picked_up_at
```

Formula:

```text
DispatchToPickupTime =
    picked_up_at - dispatched_at
```

This is useful for dispatch execution analytics.

---

# 20. Load exception metrics V1

Current source:

```text
load_exceptions
```

Supports:

```text
exception count
exception count by type
exception resolution time
unresolved exception count
```

Resolution time:

```text
ExceptionResolutionTime =
    resolved_at - occurred_at
```

for resolved exceptions.

---

# 21. Hourly employee pay V1

Current sources:

```text
employees.salary_type
employees.salary_amount
employees.salary_currency

time_entries.total_hours
time_entries.employee_id
```

When the active HR rule means hourly pay:

```text
HourlyGross =
    SUM(eligible time_entries.total_hours)
    × employees.salary_amount
```

This is a valid **simple payroll V1** for hourly employees.

It is not sufficient for truck-driver settlement.

---

# 22. HOS calculation input V1

The current schema already has an unusually strong HOS foundation.

Use:

```text
driver_hos_statuses.driving_minutes_remaining
driver_hos_statuses.on_duty_minutes_remaining
driver_hos_statuses.cycle_minutes_remaining
driver_hos_statuses.time_until_break_required
driver_hos_statuses.is_in_violation
driver_hos_statuses.next_mandatory_break_at
```

The dispatch optimizer should not recalculate all HOS history every time if a trusted HOS service has already produced current status.

Architecture:

```text
HOS synchronization / compliance engine
        ↓
driver_hos_statuses
        ↓
Dispatch feasibility
        ↓
Optimization scoring
```

---

# 23. Safety metrics V1

Current sources:

```text
driver_behavior_events
hos_violations
```

Prefer objective raw rates before inventing a composite safety score.

Example:

```text
BehaviorEventsPer1000Miles =
    BehaviorEventCount / Miles × 1000
```

```text
HOSViolationsPer1000Miles =
    HOSViolationCount / Miles × 1000
```

Only compute these if the mileage denominator is sufficiently reliable.

A future `SafetyScore` can be added only as a versioned company policy.

---

# 24. Metrics that current schema CANNOT calculate correctly yet

These must return `UNAVAILABLE` or `PARTIAL`.

## 24.1 True actual cost per load

Why unavailable:

```text
expenses is linked to truck, but not reliably to load/trip
maintenance is linked to truck, not load
driver settlement cost does not exist
```

Required:

```text
expenses.load_id / trip_id
and/or
shipment_costs
```

---

## 24.2 True load profitability

Need:

```text
actual load revenue
actual allocated load cost
driver settlement cost
approved accessorial revenue/cost
```

Revenue is mostly available.

Cost attribution is not complete.

---

## 24.3 Loaded vs empty miles

Current schema has no explicit:

```text
loaded_miles
empty_miles
```

Therefore do not fabricate:

```text
LoadedMilePercent
EmptyMilePercent
```

until mileage data is introduced.

---

## 24.4 Historical fleet utilization

`trucks.status` is a current state.

A current status is not enough for:

```text
productive hours / available hours over a month
```

Need history:

```text
vehicle_status_events
```

or equivalent telemetry/event history.

---

## 24.5 Detention

`trip_stops` currently contains:

```text
arrived_at
```

but not:

```text
departed_at
service_started_at
service_completed_at
appointment window
```

Therefore dwell/detention is not reliably calculable.

---

## 24.6 ETA accuracy

A current ETA can be computed in application logic, but historical accuracy requires persisted predictions.

Use:

```text
calculation_snapshots
```

with:

```text
calculation_type = ETA
entity_type = TRIP_STOP
entity_id = stop id
prediction timestamp
predicted arrival
```

Then compare with actual arrival.

---

# 25. ALTER 1 — `trips`

Current:

```text
id
number
name
total_distance
dispatched_at
completed_at
cancelled_at
status
truck_id
```

Recommended additions:

```sql
ALTER TABLE trips
    ADD COLUMN planned_distance_miles NUMERIC(12,3),
    ADD COLUMN actual_distance_miles  NUMERIC(12,3),
    ADD COLUMN loaded_miles           NUMERIC(12,3),
    ADD COLUMN empty_miles            NUMERIC(12,3);
```

Do not immediately drop `total_distance`.

Migration strategy:

```text
1. Add new nullable fields.
2. Audit how total_distance is populated today.
3. Backfill only when semantics are proven.
4. Change new code to write explicit distance fields.
5. Deprecate total_distance.
6. Remove total_distance only in a future breaking migration.
```

Invariant after migration:

```text
actual_distance_miles
≈ loaded_miles + empty_miles
```

Only enforce the equality if the business definition treats all actual miles as loaded/empty transport miles.

---

# 26. ALTER 2 — `trip_stops`

Current:

```text
id
type
trip_id
order
arrived_at
load_id
address...
location...
```

Recommended additions:

```sql
ALTER TABLE trip_stops
    ADD COLUMN status VARCHAR(40),
    ADD COLUMN appointment_start TIMESTAMPTZ,
    ADD COLUMN appointment_end TIMESTAMPTZ,
    ADD COLUMN service_started_at TIMESTAMPTZ,
    ADD COLUMN service_completed_at TIMESTAMPTZ,
    ADD COLUMN departed_at TIMESTAMPTZ;
```

This enables:

```text
stop lifecycle
dwell
detention
appointment compliance
ETA accuracy
```

Core timeline:

```text
appointment_start
appointment_end

arrived_at
service_started_at
service_completed_at
departed_at
```

Dwell:

```text
Dwell =
    departed_at - arrived_at
```

Service duration:

```text
ServiceDuration =
    service_completed_at - service_started_at
```

---

# 27. ALTER 3 — `expenses`

Current expense model should be preserved.

Add business attribution:

```sql
ALTER TABLE expenses
    ADD COLUMN load_id UUID,
    ADD COLUMN trip_id UUID,
    ADD COLUMN employee_id UUID,
    ADD COLUMN maintenance_record_id UUID,
    ADD COLUMN document_id UUID;
```

Recommended FKs:

```sql
ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_load
        FOREIGN KEY (load_id)
        REFERENCES loads(id)
        ON DELETE SET NULL;

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id)
        ON DELETE SET NULL;

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_employee
        FOREIGN KEY (employee_id)
        REFERENCES employees(id)
        ON DELETE SET NULL;

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_maintenance_record
        FOREIGN KEY (maintenance_record_id)
        REFERENCES maintenance_records(id)
        ON DELETE SET NULL;

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE SET NULL;
```

Do not force every expense to have load/trip.

Valid examples:

```text
company-wide expense
truck-period expense
trip expense
load expense
driver reimbursement
maintenance expense
```

---

# 28. Expense categorization policy

Current schema has multiple potentially overlapping fields:

```text
type
category
truck_expense_category
```

Do not create another parallel categorization field before auditing these values.

Create an application-level normalization layer:

```java
public interface ExpenseCategoryResolver {

    ExpenseCategory resolve(Expense expense);
}
```

Output canonical values:

```text
FUEL
TOLL
PARKING
SCALE
REPAIR
MAINTENANCE
TIRE
LUMPER
PERMIT
OTHER
```

After production values are known, migrate toward one canonical persisted category.

---

# 29. NEW — `trip_driver_assignments`

This is one of the highest-priority schema additions.

Reason:

```text
trips -> truck
```

exists, but there is no historical:

```text
trip -> driver
```

`trucks.main_driver_id` is a current relationship and must not be used to reconstruct historical payroll.

Example problem:

```text
September:
    Truck T01 -> Driver A

October:
    Truck T01 -> Driver B

If September payroll is recalculated in November
using trucks.main_driver_id,
Driver B could incorrectly receive Driver A's historical trip.
```

Schema:

```sql
CREATE TABLE trip_driver_assignments (
    id UUID PRIMARY KEY,

    trip_id UUID NOT NULL,
    driver_id UUID NOT NULL,

    assignment_type VARCHAR(30) NOT NULL,

    assigned_at TIMESTAMPTZ NOT NULL,

    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,

    planned_miles NUMERIC(12,3),
    actual_miles NUMERIC(12,3),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),

    CONSTRAINT fk_trip_driver_assignment_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_trip_driver_assignment_driver
        FOREIGN KEY (driver_id)
        REFERENCES employees(id)
        ON DELETE RESTRICT
);
```

Recommended assignment types:

```java
public enum TripDriverAssignmentType {
    PRIMARY,
    SECONDARY,
    TEAM,
    RELIEF
}
```

Index:

```sql
CREATE INDEX idx_trip_driver_assignments_trip
ON trip_driver_assignments(trip_id);

CREATE INDEX idx_trip_driver_assignments_driver_period
ON trip_driver_assignments(driver_id, effective_from, effective_to);
```

---

# 30. NEW — `load_events`

Current `loads` stores several useful milestone timestamps and `load_exceptions` stores exceptions.

A dedicated event history is still needed for:

```text
complete tracking timeline
source of each status change
geofence/GPS/driver-app events
historical state reconstruction
ETA evaluation
```

Schema:

```sql
CREATE TABLE load_events (
    id UUID PRIMARY KEY,

    load_id UUID NOT NULL,
    trip_id UUID,
    trip_stop_id UUID,

    event_type VARCHAR(60) NOT NULL,

    previous_status VARCHAR(40),
    new_status VARCHAR(40),

    occurred_at TIMESTAMPTZ NOT NULL,

    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,

    source VARCHAR(30) NOT NULL,

    actor_id UUID,

    document_id UUID,

    note VARCHAR(2000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_load_events_load
        FOREIGN KEY (load_id)
        REFERENCES loads(id)
        ON DELETE CASCADE
);
```

Possible sources:

```java
public enum LoadEventSource {
    DRIVER_APP,
    DISPATCHER,
    GPS,
    GEOFENCE,
    ELD,
    SYSTEM,
    API_IMPORT
}
```

Do not duplicate `load_exceptions`.

Relationship:

```text
load_events
    = normal timeline / milestones

load_exceptions
    = abnormal event / exception workflow
```

---

# 31. NEW — `shipment_costs`

This remains necessary even though `expenses` exists.

Different responsibilities:

```text
expenses
    source financial/business transaction

maintenance_records
    source maintenance transaction

settlements
    source driver compensation

shipment_costs
    canonical cost attributed to load/trip
```

A cost can be:

```text
direct
allocated
estimated
accrued
actual
approved
posted
```

Schema:

```sql
CREATE TABLE shipment_costs (
    id UUID PRIMARY KEY,

    load_id UUID NOT NULL,
    trip_id UUID,
    truck_id UUID,
    driver_id UUID,

    category VARCHAR(40) NOT NULL,
    stage VARCHAR(30) NOT NULL,

    source_type VARCHAR(40) NOT NULL,
    source_id UUID,

    allocation_method VARCHAR(40),

    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    unit_rate NUMERIC(19,6),

    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    incurred_at TIMESTAMPTZ,

    verified_at TIMESTAMPTZ,
    verified_by UUID,

    approved_at TIMESTAMPTZ,
    approved_by UUID,

    posted_at TIMESTAMPTZ,

    calculation_snapshot_id UUID,

    note VARCHAR(2000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),

    CONSTRAINT fk_shipment_cost_load
        FOREIGN KEY (load_id)
        REFERENCES loads(id)
        ON DELETE CASCADE
);
```

Cost stages:

```java
public enum CalculationStage {
    ESTIMATE,
    RATED,
    ACCRUAL,
    ACTUAL,
    VERIFIED,
    APPROVED,
    POSTED,
    CLOSED
}
```

Categories:

```java
public enum ShipmentCostCategory {
    FUEL,
    DRIVER,
    MAINTENANCE,
    TOLL,
    ACCESSORIAL,
    EQUIPMENT,
    INSURANCE,
    TIRE,
    PERMIT,
    PARKING,
    SCALE,
    REPAIR,
    OTHER
}
```

Source types:

```java
public enum CostSourceType {
    EXPENSE,
    MAINTENANCE_RECORD,
    DRIVER_SETTLEMENT,
    ACCESSORIAL,
    MANUAL,
    ALLOCATION,
    RATE_ENGINE
}
```

---

# 32. Shipment-cost ingestion

Example expense:

```text
expenses.id = EXP-100
load_id = LOAD-9
category = FUEL
amount = 350 USD
status = APPROVED
```

Create:

```text
shipment_costs

load_id       LOAD-9
category      FUEL
stage         ACTUAL
source_type   EXPENSE
source_id     EXP-100
amount        350
currency      USD
```

Use an idempotency constraint in application logic:

```text
(source_type, source_id, stage, allocation target)
```

must not accidentally create duplicate costs.

---

# 33. Maintenance allocation

Maintenance is normally truck-level.

Example:

```text
Truck maintenance in period = 3,000 USD
Truck miles in same period   = 20,000
```

Maintenance rate:

```text
MaintenanceCPM =
    3,000 / 20,000
    = 0.15 USD/mile
```

Load allocation:

```text
AllocatedMaintenance =
    LoadEligibleTruckMiles
    × MaintenanceCPM
```

This creates:

```text
shipment_costs.category = MAINTENANCE
shipment_costs.source_type = ALLOCATION
shipment_costs.allocation_method = PER_MILE
```

The source calculation must be snapshotted.

---

# 34. NEW — `calculation_snapshots`

Use this table for:

```text
rating
shipment cost estimate
cost allocation
profitability
driver settlement
payroll
ETA
dispatch candidate score
route optimization
```

Schema:

```sql
CREATE TABLE calculation_snapshots (
    id UUID PRIMARY KEY,

    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID NOT NULL,

    calculation_type VARCHAR(60) NOT NULL,

    engine_name VARCHAR(100) NOT NULL,
    engine_version VARCHAR(50) NOT NULL,

    policy_type VARCHAR(80),
    policy_id UUID,
    policy_version VARCHAR(50),

    input_json JSONB NOT NULL,
    result_json JSONB NOT NULL,

    currency VARCHAR(3),

    checksum VARCHAR(128),

    calculated_at TIMESTAMPTZ NOT NULL,
    calculated_by UUID,

    correlation_id VARCHAR(100),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Core requirement:

```text
Approved financial calculation
must be explainable using:
    input snapshot
    policy version
    engine version
    result snapshot
```

---

# 35. Actual load profitability after `shipment_costs`

Revenue:

```text
ActualRevenue =
    SUM(eligible invoice subtotals)
```

Cost:

```text
ActualLoadCost =
    SUM(
        shipment_costs.amount
        where load_id = X
        and stage in approved actual stages
        and currency = report currency
    )
```

Contribution:

```text
ContributionMargin =
    ActualRevenue - ActualVariableCost
```

Allocated profit:

```text
AllocatedProfit =
    ActualRevenue
    - ActualVariableCost
    - AllocatedFixedCost
```

Margin:

```text
AllocatedMargin% =
    AllocatedProfit / ActualRevenue × 100
```

If revenue is zero:

```text
margin = null
```

Do not divide by zero.

---

# 36. Cost variance

When an authoritative estimate exists:

```text
CostVariance =
    ActualCost - EstimatedCost
```

```text
CostVariancePercent =
    CostVariance / EstimatedCost × 100
```

Do not use `loads.delivery_cost_amount` as the estimate until its current semantic has been confirmed.

---

# 37. Mileage model after ALTER

Recommended authoritative fields:

```text
trips.planned_distance_miles
trips.actual_distance_miles
trips.loaded_miles
trips.empty_miles
```

Then:

```text
ActualTotalMiles =
    loaded_miles + empty_miles
```

Loaded ratio:

```text
LoadedMilePercent =
    loaded_miles / actual_total_miles × 100
```

Empty ratio:

```text
EmptyMilePercent =
    empty_miles / actual_total_miles × 100
```

Revenue per total mile:

```text
RevenuePerTotalMile =
    Revenue / actual_total_miles
```

Revenue per loaded mile:

```text
RevenuePerLoadedMile =
    Revenue / loaded_miles
```

Break-even loaded rate:

```text
BreakEvenLoadedRate =
    ActualLoadCost / loaded_miles
```

---

# 38. ALTER — stop appointment and detention model

After `trip_stops` is extended:

```text
DwellMinutes =
    departed_at - arrived_at
```

Free time is a policy value:

```text
FreeMinutes
```

Chargeable minutes:

```text
ChargeableMinutes =
    max(0, DwellMinutes - FreeMinutes)
```

Continuous hourly detention:

```text
Detention =
    ChargeableMinutes / 60
    × HourlyRate
```

Block billing:

```text
Blocks =
    ceil(
        ChargeableMinutes / BlockMinutes
    )
```

```text
Detention =
    Blocks × RatePerBlock
```

Do not hard-code:

```text
2 free hours
30-minute blocks
$X/hour
```

These are customer/driver/company policy values.

---

# 39. NEW — `accessorial_charges`

Invoice line items can represent billed accessorial revenue.

However, they do not fully represent operational approval, company cost, and driver pay.

Therefore add:

```sql
CREATE TABLE accessorial_charges (
    id UUID PRIMARY KEY,

    load_id UUID NOT NULL,
    trip_id UUID,
    trip_stop_id UUID,

    type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,

    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    rate NUMERIC(19,6),

    free_quantity NUMERIC(19,6),

    customer_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    company_cost_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    driver_pay_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    currency VARCHAR(3) NOT NULL,

    occurred_at TIMESTAMPTZ,

    approved_at TIMESTAMPTZ,
    approved_by UUID,

    document_id UUID,

    calculation_snapshot_id UUID,

    note VARCHAR(2000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Keep these three amounts separate:

```text
customer_amount
company_cost_amount
driver_pay_amount
```

Example:

```text
Customer detention charge = 150
Driver detention pay       = 75
Company direct cost        = 75 or policy-defined
```

Do not assume customer charge equals driver pay.

---

# 40. Driver compensation architecture

Current:

```text
employees
time_entries
```

Target:

```text
Employee / Driver
       │
       ├── HR/base salary
       │      employees.salary_*
       │
       └── trucking compensation
              driver_pay_policies
                     │
                     ▼
                 settlement
                     │
                     ▼
                  payroll
                     │
                     ▼
                  payslip
```

---

# 41. NEW — `driver_pay_policies`

Schema:

```sql
CREATE TABLE driver_pay_policies (
    id UUID PRIMARY KEY,

    policy_code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,

    driver_id UUID,

    pay_method VARCHAR(40) NOT NULL,

    per_mile_rate NUMERIC(19,6),
    per_load_rate NUMERIC(19,4),
    hourly_rate NUMERIC(19,6),
    daily_rate NUMERIC(19,4),
    flat_rate NUMERIC(19,4),
    revenue_percentage NUMERIC(9,6),

    mileage_basis VARCHAR(40),
    revenue_basis VARCHAR(50),

    detention_rate NUMERIC(19,6),
    detention_free_minutes INTEGER,
    detention_block_minutes INTEGER,

    layover_rate NUMERIC(19,4),
    stop_pay_rate NUMERIC(19,4),

    currency VARCHAR(3) NOT NULL,

    effective_from DATE NOT NULL,
    effective_to DATE,

    policy_version INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(policy_code, policy_version)
);
```

Pay methods:

```java
public enum DriverPayMethod {
    PER_MILE,
    PER_LOAD,
    PERCENT_REVENUE,
    HOURLY,
    DAILY,
    FLAT_RATE
}
```

Mileage bases:

```java
public enum MileageBasis {
    ACTUAL_ALL_MILES,
    ACTUAL_LOADED_MILES,
    PLANNED_ALL_MILES,
    PLANNED_LOADED_MILES,
    PRACTICAL_MILES,
    CONTRACT_MILES
}
```

Revenue bases:

```java
public enum RevenueBasis {
    LINEHAUL_ONLY,
    LINEHAUL_PLUS_FSC,
    TOTAL_REVENUE,
    REVENUE_EXCLUDING_ACCESSORIALS,
    CUSTOM
}
```

---

# 42. Driver pay formulas

Per-mile:

```text
MileagePay =
    EligibleMiles × PerMileRate
```

Per-load:

```text
LoadPay =
    EligibleCompletedLoads × PerLoadRate
```

Percentage:

```text
PercentagePay =
    EligibleRevenueBasis × RevenuePercentage
```

Hourly:

```text
HourlyPay =
    EligibleHours × HourlyRate
```

Driver gross operational earnings:

```text
SettlementGross =
    MileagePay
  + LoadPay
  + PercentagePay
  + HourlyPay
  + DetentionPay
  + LayoverPay
  + StopPay
  + Bonuses
  + OtherEarnings
```

---

# 43. NEW — `pay_periods`

```sql
CREATE TABLE pay_periods (
    id UUID PRIMARY KEY,

    period_code VARCHAR(50) NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    payment_date DATE,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(period_code)
);
```

Constraint:

```text
start_date <= end_date
```

---

# 44. NEW — `settlements`

```sql
CREATE TABLE settlements (
    id UUID PRIMARY KEY,

    settlement_number VARCHAR(60) NOT NULL,

    driver_id UUID NOT NULL,
    pay_period_id UUID NOT NULL,

    pay_policy_id UUID NOT NULL,
    pay_policy_version INTEGER NOT NULL,

    status VARCHAR(40) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    mileage_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    load_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    percentage_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    hourly_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    accessorial_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    bonus_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    gross_earnings NUMERIC(19,4) NOT NULL,
    settlement_net NUMERIC(19,4) NOT NULL,

    eligible_miles NUMERIC(19,3),
    loaded_miles NUMERIC(19,3),
    empty_miles NUMERIC(19,3),
    eligible_hours NUMERIC(19,3),

    calculation_snapshot_id UUID NOT NULL,

    calculated_at TIMESTAMPTZ,

    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,

    approved_at TIMESTAMPTZ,
    approved_by UUID,

    locked_at TIMESTAMPTZ,
    locked_by UUID,

    paid_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(settlement_number),
    UNIQUE(driver_id, pay_period_id)
);
```

---

# 45. Settlement status

```java
public enum SettlementStatus {
    DRAFT,
    CALCULATED,
    VALIDATION_REQUIRED,
    IN_REVIEW,
    APPROVED,
    LOCKED,
    PAYMENT_SCHEDULED,
    PAID,
    REJECTED,
    VOIDED,
    ADJUSTED
}
```

Main path:

```text
DRAFT
 ↓
CALCULATED
 ↓
VALIDATION_REQUIRED
 ↓
IN_REVIEW
 ↓
APPROVED
 ↓
LOCKED
 ↓
PAYMENT_SCHEDULED
 ↓
PAID
```

Once locked:

```text
do not mutate historical calculated amounts
```

Use:

```text
adjustment
reversal
new settlement version
```

---

# 46. NEW — `settlement_lines`

```sql
CREATE TABLE settlement_lines (
    id UUID PRIMARY KEY,

    settlement_id UUID NOT NULL,

    line_type VARCHAR(50) NOT NULL,

    load_id UUID,
    trip_id UUID,

    accessorial_charge_id UUID,
    expense_id UUID,

    description VARCHAR(300) NOT NULL,

    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    rate NUMERIC(19,6),

    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    taxable BOOLEAN,

    line_class VARCHAR(30) NOT NULL,

    source_type VARCHAR(40),
    source_id UUID,

    calculation_snapshot_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Recommended line classes:

```java
public enum SettlementLineClass {
    EARNING,
    DEDUCTION,
    REIMBURSEMENT
}
```

Avoid three overlapping booleans when one enum can express the concept safely.

---

# 47. Settlement reconciliation

After currency rounding:

```text
GrossEarnings =
    SUM(lines where class = EARNING)
```

```text
Deductions =
    SUM(lines where class = DEDUCTION)
```

```text
Reimbursements =
    SUM(lines where class = REIMBURSEMENT)
```

```text
SettlementNet =
    GrossEarnings
  - Deductions
  + Reimbursements
```

Tolerance after final currency rounding:

```text
0 minor currency units
```

A mismatch is an error.

---

# 48. NEW — payroll tables

Customer invoices must no longer be the long-term payroll statement model.

Create:

```text
payroll_runs
payroll_run_items
payslips
payroll_payments
```

---

# 49. `payroll_runs`

```sql
CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY,

    payroll_number VARCHAR(60) NOT NULL,

    pay_period_id UUID NOT NULL,

    status VARCHAR(40) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    driver_count INTEGER NOT NULL DEFAULT 0,

    gross_pay_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_pay_total NUMERIC(19,4) NOT NULL DEFAULT 0,

    calculation_snapshot_id UUID,

    calculated_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    locked_at TIMESTAMPTZ,
    locked_by UUID,
    paid_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(payroll_number)
);
```

---

# 50. `payroll_run_items`

```sql
CREATE TABLE payroll_run_items (
    id UUID PRIMARY KEY,

    payroll_run_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    settlement_id UUID NOT NULL,

    gross_pay NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_pay NUMERIC(19,4) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(payroll_run_id, driver_id)
);
```

---

# 51. `payslips`

```sql
CREATE TABLE payslips (
    id UUID PRIMARY KEY,

    payslip_number VARCHAR(60) NOT NULL,

    payroll_run_id UUID NOT NULL,
    payroll_run_item_id UUID NOT NULL,

    driver_id UUID NOT NULL,
    pay_period_id UUID NOT NULL,

    currency VARCHAR(3) NOT NULL,

    gross_pay NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_pay NUMERIC(19,4) NOT NULL,

    document_id UUID,

    snapshot_json JSONB NOT NULL,

    issued_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(payslip_number)
);
```

The snapshot preserves the issued statement.

Do not regenerate old payslips using current driver policy values.

---

# 52. `payroll_payments`

Do not reuse customer `payments` as the payroll ledger.

Recommended:

```sql
CREATE TABLE payroll_payments (
    id UUID PRIMARY KEY,

    payroll_run_item_id UUID NOT NULL,
    driver_id UUID NOT NULL,

    status VARCHAR(30) NOT NULL,

    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    payment_method VARCHAR(40),

    provider VARCHAR(50),
    provider_reference VARCHAR(200),

    scheduled_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,

    failure_code VARCHAR(80),
    failure_message VARCHAR(1000),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

This can later integrate with the existing:

```text
employees.stripe_connected_account_id
```

without mixing driver payout with customer invoice payments.

---

# 53. Payroll formula

Operational settlement and formal payroll remain separate.

Conceptual formula:

```text
GrossTaxablePay =
    TaxableSettlementEarnings
  + OtherTaxablePay
```

```text
NetPay =
    GrossPay
  - Taxes
  - Deductions
  + EligibleReimbursements
```

Tax/legal treatment must be delegated to a jurisdiction-specific rule/provider.

Do not hard-code a universal tax formula.

---

# 54. NEW — `rate_rules`

`load_board_listings` already contains:

```text
rate_per_mile
total_rate_amount
distance
weight
equipment_type
pickup window
delivery window
```

This is excellent for:

```text
market benchmark
external load opportunity
candidate profitability
```

It is not a substitute for the company's customer contract/rating policy.

Create `rate_rules` when contract rating is implemented.

Recommended rule methods:

```text
FLAT
PER_MILE
PER_WEIGHT
PER_UNIT
PERCENTAGE
INDEX_BASED
TIERED
```

Base rate:

```text
BaseCharge =
    EligibleQuantity × Rate
```

Min/max:

```text
Final =
    min(
        max(Calculated, MinimumCharge),
        MaximumCharge
    )
```

where configured.

---

# 55. Fuel surcharge

Do not hard-code a universal FSC formula.

Support policy.

A common configurable model:

```text
FSCPerMile =
    max(
        0,
        CurrentFuelPrice - BaseFuelPrice
    )
    / ContractMPG
```

```text
FSC =
    FSCPerMile × EligibleMiles
```

Persist:

```text
index source
index date
current price
base price
contract MPG
eligible miles
policy version
```

---

# 56. Dispatch feasibility using current schema

The current database already contains useful inputs:

```text
load origin latitude/longitude
load destination latitude/longitude

truck current latitude/longitude
truck capacity
truck hazmat/ADR capability

driver HOS remaining

load-board pickup/delivery windows
load-board equipment type
load-board weight/rate
```

This means candidate filtering can begin before the complete optimizer is built.

---

# 57. Hard feasibility first

Do not score an impossible candidate.

Pseudo-code:

```java
boolean feasible =
       truckCapacityCompatible
    && equipmentCompatible
    && hazmatCompatible
    && truckAvailable
    && driverAvailable
    && hosFeasible
    && pickupReachable;
```

If false:

```text
candidate.feasible = false
candidate.rejectionReason = ...
```

Do not let a high profitability score override a hard safety/compliance constraint.

---

# 58. Candidate scoring after feasibility

Configurable company policy:

```text
Score =
    wDeadhead × DeadheadScore
  + wLate     × LateRisk
  + wHos      × HOSRisk
  + wCost     × CostRisk
  - wMargin   × MarginScore
```

This is a LogisticsX optimization policy, not an industry-mandated formula.

Persist every component and weight.

Never store only:

```text
final_score = 0.72
```

without explanation.

---

# 59. NEW — `optimization_runs`

```sql
CREATE TABLE optimization_runs (
    id UUID PRIMARY KEY,

    run_type VARCHAR(50) NOT NULL,
    status VARCHAR(40) NOT NULL,

    solver_name VARCHAR(100) NOT NULL,
    solver_version VARCHAR(50),

    objective_type VARCHAR(50) NOT NULL,
    objective_value NUMERIC(19,6),

    load_count INTEGER NOT NULL DEFAULT 0,
    driver_count INTEGER NOT NULL DEFAULT 0,
    truck_count INTEGER NOT NULL DEFAULT 0,

    assigned_count INTEGER NOT NULL DEFAULT 0,
    unassigned_count INTEGER NOT NULL DEFAULT 0,

    input_json JSONB NOT NULL,
    constraint_json JSONB NOT NULL,
    weight_json JSONB,
    result_json JSONB,

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,

    triggered_by UUID,

    failure_code VARCHAR(80),
    failure_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

# 60. NEW — `optimization_assignments`

```sql
CREATE TABLE optimization_assignments (
    id UUID PRIMARY KEY,

    optimization_run_id UUID NOT NULL,

    load_id UUID NOT NULL,
    trip_id UUID,

    driver_id UUID,
    truck_id UUID,

    rank INTEGER,

    deadhead_miles NUMERIC(12,3),
    estimated_total_miles NUMERIC(12,3),

    estimated_cost NUMERIC(19,4),
    estimated_revenue NUMERIC(19,4),
    estimated_profit NUMERIC(19,4),
    estimated_margin_percent NUMERIC(9,4),

    eta_pickup TIMESTAMPTZ,
    eta_delivery TIMESTAMPTZ,

    hos_remaining_minutes INTEGER,

    late_risk_score NUMERIC(10,6),
    cost_score NUMERIC(10,6),
    margin_score NUMERIC(10,6),
    hos_risk_score NUMERIC(10,6),
    final_score NUMERIC(10,6),

    feasible BOOLEAN NOT NULL,
    rejection_reason VARCHAR(500),

    selected BOOLEAN NOT NULL DEFAULT FALSE,
    selected_by UUID,
    selected_at TIMESTAMPTZ
);
```

---

# 61. Company / fleet live metrics

Current `trucks.status` allows a live snapshot:

```text
total trucks
available
driving
maintenance
offline
```

However:

```text
current status != historical utilization
```

Do not calculate monthly utilization from today's current state.

For real utilization:

```text
FleetUtilization =
    ProductiveAvailableTime
    / AvailableCapacityTime
```

requires historical state or telemetry intervals.

Introduce `vehicle_status_events` only when this feature is implemented.

---

# 62. Dashboard KPI classification

## 62.1 AVAILABLE NOW

```text
invoice revenue
invoice total
tax total
customer paid amount
open invoice balance

recorded revenue per mile

approved expense totals
expense by category

maintenance cost
maintenance cost by truck

OTD V1
delivery delay
transit time
dispatch-to-pickup time

load exception count
load exception resolution time

hourly employee gross V1

HOS current availability
HOS violation count

driver behavior event count
```

---

## 62.2 PARTIAL NOW

```text
known operating CPM
fuel CPM
MPG
safety event rate
```

Partial because mileage/source normalization may be incomplete.

---

## 62.3 AVAILABLE AFTER V1 ALTER/NEW TABLES

```text
actual load cost
actual load profit
actual margin
cost variance
profit forecast error

loaded mile %
empty mile %
break-even loaded rate

detention
accessorial margin

per-mile driver pay
settlement
driver CPM
```

---

## 62.4 AVAILABLE AFTER PAYROLL

```text
official gross pay
official deductions
official reimbursement
official net pay
payroll reconciliation
payslip history
paid/unpaid payroll
```

---

## 62.5 AVAILABLE AFTER OPTIMIZATION / HISTORY

```text
historical fleet utilization
ETA MAE
ETA bias
ETA RMSE
ETA P95 error
dispatch recommendation score
optimizer savings
optimizer acceptance rate
```

---

# 63. Metric response model

Do not return fake zero for missing data.

Recommended:

```java
public enum MetricAvailability {
    AVAILABLE,
    PARTIAL,
    UNAVAILABLE,
    NOT_APPLICABLE
}
```

DTO:

```java
public record MetricDto(
    String code,
    BigDecimal value,
    String unit,
    BigDecimal numerator,
    BigDecimal denominator,
    MetricAvailability availability,
    String basis,
    String reason
) {}
```

Example:

```json
{
  "code": "LOADED_MILE_PERCENT",
  "value": null,
  "unit": "PERCENT",
  "availability": "UNAVAILABLE",
  "basis": null,
  "reason": "LOADED_AND_EMPTY_MILES_NOT_RECORDED"
}
```

---

# 64. Calculation service architecture

Recommended:

```text
calculation/
│
├── money/
│   ├── Money.java
│   ├── MoneyMath.java
│   └── CurrencyGuard.java
│
├── mileage/
│   └── MileageCalculator.java
│
├── revenue/
│   ├── RevenueCalculator.java
│   └── InvoiceReconciliationService.java
│
├── cost/
│   ├── OperatingCostCalculator.java
│   ├── ShipmentCostEngine.java
│   └── CostAllocator.java
│
├── profitability/
│   └── ProfitabilityCalculator.java
│
├── operations/
│   ├── OnTimeCalculator.java
│   ├── DelayCalculator.java
│   ├── DetentionCalculator.java
│   └── EtaAccuracyCalculator.java
│
├── driverpay/
│   ├── DriverPayEngine.java
│   └── SettlementCalculator.java
│
└── metrics/
    └── MetricFactory.java
```

Do not put everything in:

```text
CalculationUtils
```

---

# 65. Core Java money rule

Use:

```java
BigDecimal
```

Never:

```java
double
float
```

for money/rates/payroll.

Example:

```java
public record Money(
    BigDecimal amount,
    Currency currency
) {

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(
            amount.add(other.amount),
            currency
        );
    }
}
```

The existing schema already uses numeric types for money fields.

Keep calculations in BigDecimal end-to-end.

---

# 66. Revenue service

```java
public interface RevenueCalculator {

    LoadRevenueSummary calculateLoadRevenue(
        UUID loadId,
        Currency currency
    );
}
```

Response:

```java
public record LoadRevenueSummary(
    UUID loadId,
    BigDecimal subtotalRevenue,
    BigDecimal tax,
    BigDecimal invoiceTotal,
    BigDecimal paidAmount,
    BigDecimal openBalance,
    Currency currency
) {}
```

---

# 67. Cost service V1

```java
public interface OperatingCostCalculator {

    TruckOperatingCostSummary calculateTruckPeriod(
        UUID truckId,
        Instant from,
        Instant to,
        Currency currency
    );
}
```

Response:

```java
public record TruckOperatingCostSummary(
    UUID truckId,
    BigDecimal fuelCost,
    BigDecimal maintenanceCost,
    BigDecimal tollCost,
    BigDecimal parkingCost,
    BigDecimal otherKnownCost,
    BigDecimal knownOperatingCost,
    BigDecimal miles,
    BigDecimal knownOperatingCostPerMile,
    String mileageBasis
) {}
```

---

# 68. Shipment cost service V2

```java
public interface ShipmentCostEngine {

    ShipmentCostSummary estimate(UUID loadId);

    ShipmentCostSummary accrue(UUID loadId);

    ShipmentCostSummary actual(UUID loadId);

    ShipmentCostSummary reconcile(UUID loadId);
}
```

---

# 69. Profitability service

```java
public interface ProfitabilityService {

    LoadProfitability calculateLoad(
        UUID loadId,
        Currency currency
    );
}
```

```java
public record LoadProfitability(
    UUID loadId,

    BigDecimal revenue,

    BigDecimal variableCost,
    BigDecimal allocatedFixedCost,

    BigDecimal contributionMargin,
    BigDecimal allocatedProfit,

    BigDecimal marginPercent,

    BigDecimal totalMiles,
    BigDecimal loadedMiles,

    BigDecimal revenuePerTotalMile,
    BigDecimal costPerTotalMile,
    BigDecimal breakEvenLoadedRate,

    Currency currency
) {}
```

Fields can be nullable/unavailable when prerequisite data is missing.

---

# 70. Driver pay engine

```java
public interface DriverPayEngine {

    DriverPayCalculation calculate(
        UUID driverId,
        UUID payPeriodId
    );
}
```

It must source historical work through:

```text
trip_driver_assignments
time_entries
accessorial_charges
```

and resolve the effective:

```text
driver_pay_policy
```

by work date.

Never use:

```text
trucks.main_driver_id
```

as a historical payroll source.

---

# 71. Settlement calculator

```java
public interface SettlementCalculator {

    Settlement calculate(
        UUID driverId,
        UUID payPeriodId
    );

    Settlement recalculate(
        UUID settlementId
    );
}
```

Process:

```text
1. Resolve pay period.
2. Resolve effective pay policy versions.
3. Load driver trip assignments.
4. Load eligible mileage.
5. Load eligible time entries.
6. Load approved driver accessorial pay.
7. Load reimbursements/deductions.
8. Generate settlement lines.
9. Reconcile totals.
10. Save calculation snapshot.
11. Persist settlement.
```

---

# 72. Payroll workflow

Separate:

```text
SettlementCalculator
    calculates operational compensation

PayrollEngine
    creates formal payroll amount

PayrollWorkflowService
    controls approval/lock/payment state

PayslipService
    issues immutable driver-facing statement
```

Avoid:

```java
calculateApproveAndPay()
```

in one method.

---

# 73. API plan — V1 existing-schema metrics

```text
GET /api/reports/revenue
GET /api/reports/financials/monthly

GET /api/reports/expenses
GET /api/reports/costs/by-category

GET /api/reports/fleet/maintenance
GET /api/reports/fleet/fuel

GET /api/reports/operations/on-time-delivery
GET /api/reports/operations/delays
GET /api/reports/operations/transit-time

GET /api/customers/{id}/balance

GET /api/loads/{id}/financial-summary
```

Example:

```text
GET /api/loads/{id}/financial-summary
```

V1 response can explicitly show incomplete cost:

```json
{
  "loadId": "...",
  "revenue": {
    "amount": 3200,
    "currency": "USD"
  },
  "cost": {
    "amount": null,
    "availability": "PARTIAL",
    "reason": "DIRECT_LOAD_COST_ATTRIBUTION_INCOMPLETE"
  },
  "profit": {
    "amount": null,
    "availability": "UNAVAILABLE"
  }
}
```

Do not manufacture a profit from incomplete cost inputs.

---

# 74. API plan — trip and tracking improvements

```text
GET /api/trips/{id}/drivers

POST /api/trips/{id}/drivers
DELETE /api/trips/{id}/drivers/{assignmentId}

GET /api/loads/{id}/timeline

POST /api/loads/{id}/events

GET /api/trips/{id}/stops

POST /api/trip-stops/{id}/arrive
POST /api/trip-stops/{id}/start-service
POST /api/trip-stops/{id}/complete-service
POST /api/trip-stops/{id}/depart
```

---

# 75. API plan — cost V2

```text
GET /api/loads/{id}/costs
GET /api/loads/{id}/cost-summary

POST /api/loads/{id}/costs/estimate
POST /api/loads/{id}/costs/reconcile

POST /api/shipment-costs/{id}/verify
POST /api/shipment-costs/{id}/approve
POST /api/shipment-costs/{id}/post

GET /api/reports/profitability/by-load
GET /api/reports/profitability/by-truck
GET /api/reports/profitability/by-customer
GET /api/reports/profitability/by-lane
```

---

# 76. API plan — settlement/payroll

```text
GET  /api/driver-pay-policies
POST /api/driver-pay-policies
POST /api/driver-pay-policies/{id}/new-version

GET  /api/pay-periods
POST /api/pay-periods

GET  /api/driver-settlements
GET  /api/driver-settlements/{id}

POST /api/driver-settlements/calculate
POST /api/driver-settlements/{id}/recalculate
POST /api/driver-settlements/{id}/submit-review
POST /api/driver-settlements/{id}/approve
POST /api/driver-settlements/{id}/lock
POST /api/driver-settlements/{id}/schedule-payment
POST /api/driver-settlements/{id}/mark-paid

GET /api/drivers/{id}/settlements
GET /api/drivers/{id}/earnings/summary

GET  /api/payroll-runs
POST /api/payroll-runs
POST /api/payroll-runs/{id}/calculate
POST /api/payroll-runs/{id}/validate
POST /api/payroll-runs/{id}/approve
POST /api/payroll-runs/{id}/lock
POST /api/payroll-runs/{id}/mark-paid

GET /api/driver/me/payslips
GET /api/payslips/{id}
GET /api/payslips/{id}/pdf
```

---

# 77. Dashboard V1

The current DB can power:

```text
FINANCE
    invoice revenue
    customer payments
    open balance
    expenses
    maintenance cost

OPERATIONS
    active load counts
    delivered loads
    OTD V1
    average delay
    transit time
    exceptions

FLEET
    current truck status snapshot
    fuel expense
    maintenance expense
    known operating CPM when mileage basis exists

DRIVER / SAFETY
    current HOS
    HOS violations
    behavior event counts
```

---

# 78. Dashboard V2

After the cost/pay model:

```text
actual load cost
true CPM
driver CPM
actual load profit
margin %
revenue per loaded mile
break-even loaded rate
loaded/empty miles
settlement totals
payroll totals
```

---

# 79. Report formulas

## Revenue

```text
Revenue =
    SUM(eligible invoice subtotal)
```

## Cash received

```text
CashReceived =
    SUM(eligible completed customer payments)
```

## Open receivable

```text
OpenReceivable =
    InvoiceTotal - CompletedPayments
```

## Known operating cost

```text
KnownOperatingCost =
    normalized eligible expenses
  + canonical maintenance cost
```

subject to double-count prevention.

## Actual shipment cost

```text
ActualShipmentCost =
    SUM(approved actual shipment_costs)
```

## Profit

```text
Profit =
    Revenue - ActualShipmentCost
```

## Margin

```text
Margin% =
    Profit / Revenue × 100
```

## Cost per mile

```text
CPM =
    ActualShipmentCost / ActualMiles
```

## Fuel CPM

```text
FuelCPM =
    FuelCost / ActualMiles
```

## Maintenance CPM

```text
MaintenanceCPM =
    MaintenanceCost / ActualMiles
```

## Driver CPM

```text
DriverCPM =
    DriverCost / ActualMiles
```

## OTD V1

```text
OTD% =
    delivered on/before requested_delivery_date
    / eligible delivered loads
    × 100
```

## OTD V2

```text
OTD% =
    actual stop arrival <= appointment_end
    / eligible delivery stops
    × 100
```

---

# 80. ETA accuracy model

Persist each prediction via `calculation_snapshots`.

For each comparable prediction:

```text
ErrorMinutes =
    ActualArrival - PredictedArrival
```

MAE:

```text
MAE =
    mean(abs(ErrorMinutes))
```

Bias:

```text
Bias =
    mean(ErrorMinutes)
```

RMSE:

```text
RMSE =
    sqrt(mean(ErrorMinutes²))
```

P95:

```text
P95AbsoluteError =
    percentile95(abs(ErrorMinutes))
```

Evaluation must fix:

```text
prediction horizon
population
stop type
time period
```

Do not compare a 4-hour-ahead ETA model with a 15-minute-ahead ETA model as if they are the same task.

---

# 81. Multi-currency

Current schema contains currency per money field.

This is good.

Reporting rule:

```text
Never sum different currencies directly.
```

Choose one:

```text
filter to report currency
group by currency
explicit versioned FX conversion
```

Until an FX engine exists:

```text
prefer filter/group-by
```

---

# 82. Status transition validation

Do not let controller code directly set arbitrary financial statuses.

Example settlement transitions:

```text
DRAFT -> CALCULATED
CALCULATED -> IN_REVIEW
IN_REVIEW -> APPROVED
APPROVED -> LOCKED
LOCKED -> PAYMENT_SCHEDULED
PAYMENT_SCHEDULED -> PAID
```

Rejected/void/adjustment transitions must be explicit.

The same principle applies to:

```text
shipment cost approval
accessorial approval
payroll
```

---

# 83. Reconciliation rules

## Invoice

```text
subtotal == sum(line amounts)
```

subject to the application's tax/rounding design.

## Maintenance

```text
total_cost == labor_cost + parts_cost
```

and optionally:

```text
parts_cost == sum(maintenance_parts.total_cost)
```

## Settlement

```text
gross == sum earning lines
deductions == sum deduction lines
reimbursements == sum reimbursement lines
net == gross - deductions + reimbursements
```

## Payroll

```text
payroll gross total == sum payroll item gross
payroll net total == sum payroll item net
```

## Payslip

```text
payslip net == payroll item net
```

---

# 84. Idempotency

Money-changing operations must be idempotent.

Examples:

```text
create settlement
calculate payroll
mark payroll payment
post shipment cost
generate invoice from load
provider webhook
```

Use:

```text
Idempotency-Key
```

or an equivalent persisted provider/business key.

Do not create duplicate money records after HTTP/provider retry.

---

# 85. Optimistic locking

Use:

```java
@Version
```

for approval-sensitive aggregates:

```text
settlement
payroll run
rate policy
shipment cost
accessorial
```

Two accountants approving/editing concurrently must not silently overwrite each other.

---

# 86. Data migration safety

Never perform a destructive migration before validating production semantics.

Specifically:

```text
do not rename/drop loads.delivery_cost_amount yet
do not drop trips.total_distance yet
do not drop invoice employee/payroll fields yet
do not remove employees.salary_* yet
```

First:

```text
instrument
observe
backfill
compare
switch reads/writes
deprecate
remove later
```

---

# 87. Flyway migration sequence

Suggested names can be adapted to the project's current migration numbering.

## Migration A — Trip driver history

```text
Vxxx__create_trip_driver_assignments.sql
```

Create:

```text
trip_driver_assignments
```

No destructive changes.

---

## Migration B — Explicit trip mileage

```text
Vxxx__add_trip_mileage_breakdown.sql
```

Add:

```text
planned_distance_miles
actual_distance_miles
loaded_miles
empty_miles
```

Keep `total_distance`.

---

## Migration C — Stop execution timestamps

```text
Vxxx__extend_trip_stops_execution.sql
```

Add:

```text
status
appointment_start
appointment_end
service_started_at
service_completed_at
departed_at
```

---

## Migration D — Expense attribution

```text
Vxxx__extend_expenses_attribution.sql
```

Add:

```text
load_id
trip_id
employee_id
maintenance_record_id
document_id
```

---

## Migration E — Load event history

```text
Vxxx__create_load_events.sql
```

---

## Migration F — Calculation snapshots

```text
Vxxx__create_calculation_snapshots.sql
```

---

## Migration G — Shipment cost ledger

```text
Vxxx__create_shipment_costs.sql
```

---

## Migration H — Accessorials

```text
Vxxx__create_accessorial_charges.sql
```

---

## Migration I — Driver pay

```text
Vxxx__create_driver_pay_policies.sql
Vxxx__create_pay_periods.sql
Vxxx__create_settlements.sql
Vxxx__create_settlement_lines.sql
```

---

## Migration J — Payroll

```text
Vxxx__create_payroll_runs.sql
Vxxx__create_payroll_run_items.sql
Vxxx__create_payslips.sql
Vxxx__create_payroll_payments.sql
```

---

## Migration K — Rating

```text
Vxxx__create_rate_rules.sql
```

---

## Migration L — Optimization audit

```text
Vxxx__create_optimization_runs.sql
Vxxx__create_optimization_assignments.sql
```

---

# 88. Implementation phases

# Phase 0 — semantic audit

Before schema mutation, confirm:

```text
loads.delivery_cost_amount meaning
loads.distance meaning
trips.total_distance meaning

existing expense type/category values

existing invoice type/status values
whether invoices currently act as payroll invoices

employee salary_type values

load/trip status enums
```

Deliverable:

```text
docs/current-domain-semantics.md
```

This is mandatory because DBML alone cannot prove these meanings.

---

# Phase 1 — metrics from current DB

No major new business tables required.

Implement:

```text
RevenueCalculator
InvoiceReconciliationService
CustomerBalanceCalculator

ExpenseReportService
MaintenanceReportService

OnTimeCalculator
DelayCalculator
TransitTimeCalculator
```

Endpoints:

```text
/api/reports/financials/monthly
/api/reports/costs/by-category
/api/reports/operations/on-time-delivery
/api/customers/{id}/balance
```

Important:

```text
metrics explicitly mark PARTIAL/UNAVAILABLE
```

---

# Phase 2 — execution correctness

Migrations:

```text
trip_driver_assignments
trip mileage fields
trip stop execution fields
expense attribution
load_events
calculation_snapshots
```

Result:

```text
historical driver assignment
actual/loaded/empty mileage model
dwell/detention foundation
auditable calculations
better load timeline
```

---

# Phase 3 — true cost and profitability

Create:

```text
shipment_costs
accessorial_charges
```

Implement:

```text
ShipmentCostEngine
CostAllocator
ProfitabilityCalculator
DetentionCalculator
```

Result:

```text
actual load cost
true load profit
margin
CPM
break-even rate
cost variance
accessorial profitability
```

---

# Phase 4 — driver settlement

Create:

```text
driver_pay_policies
pay_periods
settlements
settlement_lines
```

Implement:

```text
DriverPayEngine
SettlementCalculator
SettlementValidator
SettlementWorkflowService
```

Result:

```text
per-mile pay
per-load pay
percentage pay
hourly pay
detention/layover pay
reimbursement
deduction
settlement statement
```

---

# Phase 5 — formal payroll

Create:

```text
payroll_runs
payroll_run_items
payslips
payroll_payments
```

Stop generating new payroll behavior through `invoices`.

Result:

```text
official pay run
official payslip
payment history
audit/lock
```

---

# Phase 6 — contract rating

Create:

```text
rate_rules
```

Use `load_board_listings` as:

```text
external market intelligence
```

not contract policy.

Result:

```text
quote
base charge
fuel surcharge
accessorial estimate
minimum/maximum rate
```

---

# Phase 7 — optimization

Create:

```text
optimization_runs
optimization_assignments
```

Use:

```text
loads
trucks
trip_driver_assignments
driver_hos_statuses
load_board_listings
shipment costs
rate/profitability
```

Result:

```text
feasible candidate ranking
cost-aware assignment
margin-aware assignment
HOS-aware assignment
optimization audit
```

---

# 89. First backend ticket set based on the real schema

## BE-CALC-001 — Audit legacy financial semantics

Check:

```text
loads.delivery_cost_amount
loads.distance
trips.total_distance
invoice types/statuses
employee salary types
expense categories
```

Acceptance:

```text
Every ambiguous current field has a documented semantic.
No new calculation assumes an unverified meaning.
```

---

## BE-CALC-002 — Revenue V1

Implement:

```text
invoice revenue
invoice reconciliation
paid amount
open balance
currency guard
```

---

## BE-CALC-003 — Operations KPI V1

Implement:

```text
OTD V1
delivery delay
transit time
dispatch-to-pickup
exception count/resolution
```

---

## BE-CALC-004 — Cost V1

Implement:

```text
approved expense totals
fuel expense
maintenance cost
known operating cost
partial CPM
```

Prevent maintenance double count.

---

## BE-CALC-005 — Trip driver assignment history

Create:

```text
trip_driver_assignments
```

Update dispatch workflow to snapshot assigned driver(s).

---

## BE-CALC-006 — Mileage model

Add:

```text
planned
actual
loaded
empty miles
```

---

## BE-CALC-007 — Trip stop execution

Add:

```text
appointments
service start/end
departure
```

---

## BE-CALC-008 — Expense attribution

Add:

```text
load_id
trip_id
employee_id
maintenance_record_id
document_id
```

---

## BE-CALC-009 — Calculation snapshots

Create immutable calculation audit.

---

## BE-CALC-010 — Shipment cost ledger

Create:

```text
shipment_costs
```

Ingest approved expense/maintenance sources.

---

## BE-CALC-011 — Profitability

Implement:

```text
actual cost
profit
margin
CPM
RPM
break-even rate
variance
```

---

## BE-CALC-012 — Accessorials

Implement:

```text
detention
layover
lumper
extra stop
toll
other
```

Keep customer/company/driver values separate.

---

## BE-CALC-013 — Driver pay policy

Create versioned/effective driver policies.

---

## BE-CALC-014 — Settlement

Implement:

```text
pay period
settlement
settlement lines
reconciliation
approval/lock
```

---

## BE-CALC-015 — Payroll

Implement:

```text
payroll run
payslip
payment
```

Separate from invoice.

---

## BE-CALC-016 — Optimization foundation

Use current HOS/truck/load data plus true costing.

---

# 90. Test matrix

## Revenue

```text
single invoice
multiple invoices
wrong currency
void/non-eligible invoice
invoice subtotal mismatch
partial customer payment
full payment
overpayment policy
```

## Expense

```text
approved
rejected
wrong currency
truck-only
trip-linked
load-linked
maintenance-linked
```

## Maintenance

```text
labor + parts = total
parts lines reconcile
zero miles denominator
```

## OTD

```text
exactly on deadline
before deadline
after deadline
missing requested delivery
missing delivered_at
cancelled load
```

## Mileage

```text
loaded + empty
zero miles
missing actual miles
legacy-only total_distance
```

## Settlement

```text
per-mile
per-load
percentage revenue
hourly
detention
reimbursement
deduction
policy effective boundary
trip assignment history
locked settlement
```

## Payroll

```text
one driver
many drivers
duplicate driver
settlement not approved
rounding
locked payroll
duplicate provider callback
```

## Optimization

```text
capacity fail
hazmat fail
HOS fail
pickup-window fail
maintenance unavailable
valid candidate
multiple valid candidates
```

---

# 91. Source-of-truth rules

## Load revenue

```text
invoices / invoice_line_items
```

## Customer cash received

```text
payments
```

## Expense source

```text
expenses
```

## Maintenance source

```text
maintenance_records / maintenance_parts
```

## Driver HOS

```text
driver_hos_statuses
historical verification:
hos_logs
hos_violations
```

## Driver assignment

Target:

```text
trip_driver_assignments
```

Never historical current truck driver.

## Shipment cost

Target:

```text
shipment_costs
```

fed by source transactions.

## Official driver compensation

```text
settlements
```

## Official payroll

```text
payroll_runs
payroll_run_items
payslips
payroll_payments
```

---

# 92. Target data lineage

```text
                           CURRENT SOURCES

     invoices ──────────────────────────────┐
                                           │
     payments                              │
                                           ▼
                                      Revenue Engine
                                           │
                                           │
     expenses ────────┐                    │
                      ▼                    │
                Shipment Cost              │
                      ▲                    │
                      │                    │
 maintenance_records ─┤                    │
                      │                    │
 settlement ──────────┘                    │
                      │                    │
                      └────────────┬───────┘
                                   ▼
                          Profitability Engine
                                   │
                 ┌─────────────────┼────────────────┐
                 ▼                 ▼                ▼
               Profit             CPM             Margin


 trips + trip_driver_assignments
                 │
                 ▼
           Driver Pay Engine
                 │
                 ▼
             Settlement
                 │
                 ▼
              Payroll
                 │
                 ▼
              Payslip


 loads + trips + trucks + HOS + cost + revenue
                 │
                 ▼
           Dispatch Optimizer
                 │
                 ▼
       optimization_runs / assignments


 Every material calculation
                 │
                 ▼
       calculation_snapshots
```

---

# 93. Important anti-patterns

Do not:

```text
store profit directly on loads and treat it as source truth
```

Do not:

```text
calculate historical driver pay from trucks.main_driver_id
```

Do not:

```text
sum expenses + maintenance blindly
```

Do not:

```text
use invoices as both customer invoice and official payslip forever
```

Do not:

```text
call loads.distance actual miles without proof
```

Do not:

```text
call known partial operating cost TotalCPM
```

Do not:

```text
sum multiple currencies
```

Do not:

```text
use double for money
```

Do not:

```text
edit LOCKED settlement/payroll amounts
```

Do not:

```text
hide missing data as zero
```

---

# 94. Reference industry/technical sources

These references support the target business patterns, but company-specific rates/tolerances remain LogisticsX policies.

## Oracle Transportation Management

Rate and shipment-cost concepts, including accessorial handling:

https://docs.oracle.com/en/cloud/saas/transportation/26a/otmol/planning/power_data/rates_and_codes/accessorial_costs.htm

https://docs.oracle.com/en/cloud/saas/transportation/26c/otmol/planning/shipment_manager/shared_tabs/ship_mgrs_financials.htm

## SAP Transportation Management

Transportation charge calculation / calculation methods:

https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/e3dc5400c1cc41d1bc0ae0e7fd9aa5a2/dd0911e248a240eb980bd7cf8dc701bb.html

## FMCSA

Hours-of-Service regulatory reference:

https://www.fmcsa.dot.gov/regulations/hours-service/summary-hours-service-regulations

## Google OR-Tools

Vehicle routing / time-window optimization reference:

https://developers.google.com/optimization/routing/vrp

https://developers.google.com/optimization/routing/vrptw

---

# 95. Final migration strategy

The correct evolution for the **actual LogisticsX database** is:

```text
CURRENT DB
   │
   ├── calculate what is already trustworthy
   │
   ▼
Metrics V1
   │
   ├── document ambiguous legacy fields
   │
   ▼
Execution History
   │
   ├── trip_driver_assignments
   ├── explicit mileage
   ├── stop timestamps
   ├── expense attribution
   └── load_events
   │
   ▼
Calculation Audit
   │
   └── calculation_snapshots
   │
   ▼
True Cost Ledger
   │
   └── shipment_costs
   │
   ▼
Profitability
   │
   ├── true CPM
   ├── actual profit
   ├── margin
   └── variance
   │
   ▼
Driver Compensation
   │
   ├── pay policy
   ├── settlement
   └── settlement lines
   │
   ▼
Formal Payroll
   │
   ├── payroll run
   ├── payslip
   └── payroll payment
   │
   ▼
Contract Rating
   │
   └── rate_rules
   │
   ▼
Optimization
   │
   ├── HOS-aware
   ├── cost-aware
   └── margin-aware
   │
   ▼
Executive Reporting
```

The key principle is:

```text
Do not rebuild the database.
Evolve the existing domain.
```

Your current schema already contains substantial operational value.

The new calculation architecture should fill the missing links:

```text
historical driver assignment
explicit mileage semantics
load/trip cost attribution
calculation audit
driver compensation lifecycle
formal payroll
optimization audit
```

while preserving the working modules already present.

---

# 96. Definition of Done for Version 2 implementation

```text
[ ] Semantics of legacy distance/cost/status fields documented
[ ] Existing expenses retained as primary expense source
[ ] Existing HOS subsystem reused
[ ] Historical trip-driver assignment persisted
[ ] Planned/actual/loaded/empty mileage distinguished
[ ] Trip stops record full service lifecycle
[ ] Expenses can link to load/trip/driver/maintenance/document
[ ] Maintenance double counting prevented
[ ] Calculation snapshots persisted
[ ] Shipment cost ledger implemented
[ ] Profitability uses actual attributed costs
[ ] Missing KPI returns PARTIAL/UNAVAILABLE, not fake zero
[ ] Driver pay policy is effective-dated/versioned
[ ] Settlement lines reconcile exactly
[ ] Payroll is separate from customer invoice
[ ] Payslip is immutable after issue
[ ] Customer payment is separate from payroll payment
[ ] Money uses BigDecimal
[ ] Currency mismatch is blocked
[ ] Approval/lock state transitions are validated
[ ] Optimization stores feasibility, weights, component scores and results
```

---

# 97. Immediate next implementation target

The first code batch should be deliberately small:

```text
BE-CALC-001
Semantic audit

BE-CALC-002
Revenue + invoice/payment reconciliation

BE-CALC-003
Operations KPI V1

BE-CALC-004
Expense + maintenance cost V1

BE-CALC-005
trip_driver_assignments

BE-CALC-006
explicit mileage fields

BE-CALC-007
trip stop execution timestamps

BE-CALC-008
expense attribution

BE-CALC-009
calculation_snapshots

BE-CALC-010
shipment_costs
```

Only after this foundation is stable should the project implement:

```text
settlement
payroll
rating
optimization
```

This order minimizes schema churn and avoids building payroll/optimization on ambiguous operational data.

---

**End of Version 2 — Schema-Aligned Specification**
