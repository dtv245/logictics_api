# Backend Pagination Audit

**Audit date:** 2026-10-28
**Scope:** GET endpoints in the current working tree that return `List`, `Page`, `Slice`, a paged wrapper, or an unbounded nested list. Includes the uncommitted AI/Lark work already present in this checkout.
**State:** Baseline inventory was saved before source edits. Implementation is in progress. Runtime query counts and representative large-data plans remain unmeasured; details below.

## Evidence and measurement limits

- Spring Boot uses PostgreSQL and Flyway. `spring.jpa.open-in-view=false` is already configured. Most core read services use `@Transactional(readOnly = true)`.
- `PaginationValidator` already normalizes the existing CRUD convention to **1-based pages**, default size 20, maximum size 100 (clamps oversized sizes). The direct payroll/AI page endpoints use **0-based pages**. Preserve these separate frontend conventions.
- `PagedResponse` currently serializes `items`, `totalItems`, `totalPages`, `currentPage`, and `pageSize`; it has no `hasNext`. Most list endpoints are wrapped in `ApiResponse.data`. `/api/payroll/reconciliation-cases` returns Spring `Page` directly, so its shape differs.
- Sort field mapping already exists in `QuerySortPolicy` for customers, employees, trucks, loads, trips, invoices, payments, documents, inspections, and terminals. Unknown fields produce HTTP 400. Sorts do not automatically append `id`, so non-unique sorts can make page boundaries unstable. Payments bypass this policy entirely.
- The current `us_logisticsx` PostgreSQL catalog has only partial/stale `pg_class.reltuples` estimates. Existing `pg_stat_user_tables.n_live_tup` reports 0 for every public table; the separate `logistics` database has no planner estimates (`reltuples = -1`). Treat the numbers below as historical planner estimates, not verified live counts. There is no representative 100k-row dataset available in these databases.
- `PersistenceReadPostgresTest` already turns on `hibernate.generate_statistics` in a test-only Spring context and asserts at most two prepared statements for the Load and Trip service searches. It is guarded by `TASK_DB_URL` matching a disposable `codex_*` database; that variable is unset in this environment, so the test and query counts were **not run**. Main application SQL logging/statistics are not enabled. The running local application was left untouched.
- No `EXPLAIN (ANALYZE, BUFFERS)` was run: current DB statistics/data are not representative of the requested large-table workload, and Hibernate SQL for all parameterized endpoints was not captured. Do not treat empty/stale-table plans as performance evidence. Record actual plans after provisioning a disposable representative dataset.
- No Hibernate warning was found in source/configuration for collection fetch plus pagination. Static search found no pageable `JOIN FETCH` of a collection. Runtime logs were not available for warning inspection.
- There is no `ContainerController` or tracking list route in the current Java source. Container/tracking entities or tables alone do not expose list endpoints.

## GET list and collection endpoints

Row estimates are the available planner estimate for the primary table; `unknown` means the database has no usable estimate. “No” in the paging column means the endpoint has no hard per-request response cap, even if its current domain relationship is usually small.

| Endpoint | Primary table / source | Current pagination | Filter, search, sort | Approx. rows | Audit finding |
|---|---|---|---|---:|---|
| `GET /api/loads` | `loads` | Yes, 1-based; 20/100 | Search name/external broker ref; status, customer, truck, dispatcher; whitelisted `orderBy` | ~67* | ToOne `@EntityGraph` for customer/truck/dispatcher; `%term%` search; full `LoadView` includes non-list fields. |
| `GET /api/trips` | `trips` | Yes, 1-based; 20/100 | Search name; status, truck; whitelisted sort | ~44* | ToOne `@EntityGraph` for truck; full entity-backed view. |
| `GET /api/invoices` | `invoices` | Yes, 1-based; 20/100 | Status, type, customer; legacy employee filter rejected; whitelisted sort | ~44* | Mapper reads lazy load/customer/employee relationships; no list-query `EntityGraph`/projection found. View includes notes. |
| `GET /api/payments` | `payments` | Yes in repository, not safely at API boundary | Status, invoice; `orderBy` accepted directly | unknown | No max-size clamp, negative page can fail, unknown sort is not explicitly rejected; mapper reads lazy invoice number. |
| `GET /api/customers` | `customers` | Yes, 1-based; 20/100 | Search, status; whitelisted sort | ~79* | Paged but returns the full customer view, including notes/address fields. |
| `GET /api/drivers` | `employees` | Yes, 1-based; 20/100 | Driver-role subset; search, status; whitelisted sort | ~93* | Paged employee search. Route currently has unrelated in-progress changes; preserve them when editing. |
| `GET /api/employees` | `employees` | Yes, 1-based; 20/100 | Search, status, role; whitelisted sort | ~93* | Paged but returns full employee view. |
| `GET /api/trucks` | `trucks` | Yes, 1-based; 20/100 | Search number/VIN/plate; status, type; whitelisted sort | ~29* | `TruckView` reads main/secondary driver names; no list entity graph found, likely N+1. Search is leading-wildcard `LIKE`. |
| `GET /api/terminals` | `terminals` | Yes, 1-based; 20/100 | Search name/code/city; type, country; whitelisted sort | unknown | Search uses leading-wildcard `LIKE`; full terminal view contains address fields. |
| `GET /api/inspections` | `load_condition_reports` | Yes, 1-based; 20/100 | Load, type; whitelisted sort | unknown | View reads lazy load/inspector relationships; possible N+1. Includes notes. |
| `GET /api/documents` | `documents` | Yes, 1-based; 20/100 | Type, status, load, truck, employee; whitelisted sort | unknown | View reads uploader; no list entity graph found. Payload includes descriptions/notes and storage metadata. |
| `GET /api/notifications` | `notifications` | Yes, 1-based; 20/100 | No filters; fixed `createdDate DESC` | unknown | Uses common `PagedResponse`; `Page` may issue a count query. |
| `GET /api/roles` | `tenant_roles` (+ `tenant_role_claims`) | Yes, 1-based; 20/100 | No filters; fixed name sort | ~13* | `RoleView` traverses the lazy claims collection per role; collection fetch is not joined, but mapping may issue N+1 selects. |
| `GET /api/payroll/reconciliation-cases` | `payroll_payment_events` | Yes, 0-based; default 25/max 100 | Fixed open/unresolved filter and `occurredAt, id` order | ~9* | Returns raw Spring `Page`; DTO traverses lazy payment relation. Contract differs from common `PagedResponse`. |
| `GET /api/ai/dispatch/sessions` | `ai_dispatch_sessions` | Yes, 0-based; 20/100 | Tenant, optional status; fixed `startedAt DESC` | unknown | Paged, but returns entity objects rather than a screen projection; no automatic `id` tie-breaker. |
| `GET /api/ai/runs` | `ai_runs` | Yes, 0-based; 20/100 | Tenant, load, type, status; fixed `createdAt DESC` | unknown | Paged; no automatic `id` tie-breaker; entity query selects rows before mapping to a view. |
| `GET /api/loads/{loadId}/accessorials` | `accessorial_charges` | No | Parent load only; repository has no explicit ordering | ~32* | No response cap. Typical per-load size may be small but there is no enforced limit. |
| `GET /api/loads/{loadId}/costs` | `shipment_costs` | No | Parent load only; no explicit ordering | ~173* | No response cap; highest observed child-table estimate. |
| `GET /api/trips/{tripId}/drivers` | `trip_driver_assignments` | No | Parent trip; `effectiveFrom DESC` | ~60* | Assignment history can grow without a cap; mapper relationship query count needs measurement. |
| `GET /api/trips/{tripId}/stops` | `trip_stops` | No | Parent trip; stop order | ~78* | No enforced maximum stop count or response cap. |
| `GET /api/driver-settlements` | `settlements` | No | Pay period, driver, status, settlement type; repository ordering not exposed by endpoint | ~91* | Returns an unbounded list. Current view omits lines in this endpoint. |
| `GET /api/driver-pay-policies` | `driver_pay_policies` | No | Fixed policy code/version order | ~41* | Unbounded list; also used as a policy/driver lookup candidate. |
| `GET /api/pay-periods` | `pay_periods` | No | Fixed start-date order | ~41* | Unbounded list; lookup-like endpoint. |
| `GET /api/payroll/items/{id}/payments` | `payroll_payments` | No | Parent payroll item; attempt number order | ~9* | Usually few attempts, but no hard limit. |
| `GET /api/driver/me/payslips` | `payslips` | No | Authenticated driver; issued time descending | ~11* | Unbounded history; response includes `snapshotJson`, a potentially large JSON field. Entity also stores PDF bytes, though the view does not return them. |
| `GET /api/fleet/policies` | `fleet_policy_versions` | No | No filter/order exposed | unknown | Unbounded policy history list. |
| `GET /api/ai/dispatch/pending` | `ai_dispatch_decisions` | No | Tenant and `Suggested` status | unknown | **N+1:** service loads each referenced truck separately and then reads the lazy main driver. No page cap. |
| `GET /api/ai/gaps?loadId=…` | `ai_load_gaps` | No page; scoped to one load | Load required; optional severity/status | unknown | Returns a summary containing per-load gap data; row count has no database/page bound. |
| `GET /api/lark/base/missing` | `lark_base_sync_links` | `limit/offset`, max 100 | Domain; default 20/0 | unknown | Service validates `1 <= limit <= 100` and `offset >= 0`; no total/count response. |
| `GET /api/lark/base/loads` | External Lark Base | External `pageSize/pageToken` | Cursor token; default size 20 | N/A | Positive `pageSize` is forwarded to upstream without a local maximum clamp. |
| `GET /api/reports/profitability/by-load` | `loads` plus cost/revenue tables | No | Optional `loadId`; no sort | ~67* loads | **Unbounded + N+1:** `findAll()` then summary queries per load. |
| `GET /api/reports/profitability/by-lane` | `loads` plus cost/revenue tables | No | No filters/sort | ~67* loads | **Unbounded + N+1:** reads all loads and computes per-load summaries before grouping. |
| `GET /api/reports/profitability/by-truck` | `loads` plus cost/revenue tables | No | No filters/sort | ~67* loads | **Unbounded + N+1:** reads all loads and computes per-load summaries before grouping. |

\* Stored planner estimate from `us_logisticsx` catalog; not a verified live count. All `n_live_tup` values were 0, so estimates may be stale. `unknown` is not evidence that a table is empty.

### Other list-shaped responses

- `GET /api/loads/{loadId}/timeline` returns a response containing load events, with no explicit event cap.
- `GET /api/payroll/runs/{id}` returns a payroll-run view built with its run items; a large payroll run can produce a large nested payload.
- `GET /api/driver-settlements/{id}` returns settlement lines in its detail view. This is a detail route rather than a top-level list endpoint, but its nested collection is not capped.
- `GET /api/ai/dispatch/sessions/{id}` returns suggestions and currently looks up each truck inside a loop; this detail route also has N+1 risk and an unbounded nested list.
- `GET /api/reports/drivers/ranking` caps the output `limit` at 50, but its calculator loads all employees, assignments, and loads into memory. The report result is capped; the input scan is not.
- Operation/fleet/expense report endpoints return aggregates rather than row lists, but several calculators load whole tables or unbounded period results. They need query-plan/performance treatment separately from screen pagination.

## Unbounded repository reads and in-memory processing

Source search found unpaged `findAll()` calls in:

- `ProfitabilityService`: all loads for by-load/by-lane/by-truck summaries. By-load currently performs per-load summary reads.
- `DriverRankingService`: all employees, trip-driver assignments, and loads, then filters/groups in memory.
- `OnTimeCalculator`, `DelayCalculator`, and `TransitTimeCalculator`: all loads before date filtering/aggregation.
- `OperatingCostCalculator`: all trips before calculation.
- Other period-based report repositories return `List` for expenses, invoices, and maintenance records; large date ranges can materialize every matching row.

No list endpoint was found that paginates with Java `stream().skip()`, `subList()`, or a similar in-memory offset. Other `subList()` matches are tenant migration batching and PDF text layout, not HTTP list pagination.

## N+1 and fetch review

| Endpoint/source | Finding |
|---|---|
| `/api/loads` | Existing ToOne entity graph covers customer, assigned truck, and dispatcher. Existing test asserts `<=2` prepared statements on a 25-row fixture, but it was not executed in this environment. |
| `/api/trips` | Existing ToOne entity graph covers truck. Existing test asserts `<=2` prepared statements on a 25-row fixture, but it was not executed. |
| `/api/invoices`, `/api/payments`, `/api/trucks`, `/api/inspections`, `/api/documents`, `/api/roles`, payroll reconciliation | DTO mapping traverses lazy associations (including Role's collection); repository/page query has no projection or suitable graph. Confirm exact query counts in instrumented tests. |
| `/api/ai/dispatch/pending` and `/api/ai/dispatch/sessions/{id}` | Explicit per-row `truckRepository.findById()` calls, then lazy main-driver name access. |
| `/api/reports/profitability/*` | Per-load report calculation performs repeated repository lookups after an unpaged `findAll()`. |

No pageable query was found joining a collection with `JOIN FETCH`; no matching in-memory-pagination warning was found in repository configuration or source. SQL/runtime log check is pending.

## Baseline risks and order of work

1. **Critical response-size gaps:** payments' unvalidated page parameters, settlements, payslips, pay periods/policies, child collections, AI pending, and profitability list reports have no reliable maximum.
2. **Highest query-count risks:** profitability reports and AI pending/session detail explicitly query per row; invoice/payment/truck/inspection/document/role/page mappers can trigger lazy loads.
3. **Search/index risks:** leading-wildcard `LIKE` queries on loads, trips, trucks, terminals, customers/employees cannot use ordinary B-tree indexes efficiently. Current migrations contain no `pg_trgm` extension or trigram GIN index. Only add indexes after plans from representative data.
4. **Pagination consistency:** most CRUD lists use 1-based `PagedResponse`; AI and payroll use zero-based conventions; `/api/payments` is the parameter-boundary exception. Existing sort whitelist is present but lacks automatic `id` tie-breakers.
5. **Projection/payload risks:** invoice/customer/inspection/document/payslip list DTOs include notes, addresses, metadata, or JSON fields beyond a minimal table row. Preserve existing response field names/consumer expectations while defining separate lightweight lookup/list contracts where needed.

## Baseline measurements

| API group | Query count/request | Latency | `EXPLAIN (ANALYZE, BUFFERS)` |
|---|---:|---:|---|
| Loads / trips | Test source has an assertion of at most 2 SELECT statements for service pagination | Not run | Not run |
| Other list APIs | Not measured | Not run | Not run |

The test-only Hibernate statistics switch exists in `PersistenceReadPostgresTest`; it is not enabled in the running application. `TASK_DB_URL` is unset, the app's active local container was not restarted, and available database row statistics are stale/empty. A performance claim for hundreds of thousands of rows requires a disposable PostgreSQL fixture with representative data. Update this file endpoint-by-endpoint with measured before/after query counts, latency, and plans as implementation proceeds.

## Implementation checkpoint — 2026-10-28

The initial implementation adds bounded database pagination to the following list routes. Unless noted, these retain their current 1-based `page` / `pageSize` inputs, default 20, maximum 100, retain `ApiResponse.data` as an array, and add `meta.pagination` (`page`, `size`, `total`, `totalPages`, `hasNext`, `hasPrevious`). Invalid negative pages normalize to page 1. Lookup routes use maximum size 50.

| Endpoint | Change | Query / mapping change | Query count and latency after change |
|---|---|---|---|
| `GET /api/payments` | Shared bounds, payment sort whitelist and stable `id` tiebreaker | Existing repository page query | Not measured; targeted HTTP contract test passes |
| `GET /api/loads/{loadId}/accessorials` | Page query added | ToOne graph for load/trip/stop | Not measured |
| `GET /api/loads/{loadId}/costs` | Page query added | ToOne graph for load/trip/truck/driver | Not measured |
| `GET /api/trips/{tripId}/drivers` | Page query added | ToOne graph for trip/driver | Not measured |
| `GET /api/trips/{tripId}/stops` | Page query added | ToOne graph for trip/load | Not measured |
| `GET /api/driver-settlements` | Page query added | ToOne graph for driver/pay period/pay policy/parent | Not measured |
| `GET /api/pay-periods` | Page query; max size 50 | Stable start-date/id order | Not measured |
| `GET /api/driver-pay-policies` | Page query; max size 50 | ToOne graph for driver; stable policy/version/id order | Not measured |
| `GET /api/fleet/policies` | JDBC limit/offset page; max size 50 | Stable policy/version/id order and count query | Not measured |
| `GET /api/payroll/items/{id}/payments` | Page query, max size 100; response remains a raw array | JPQL DTO projection avoids loading request JSON | Not measured; response carries `X-Pagination-*` headers |
| `GET /api/driver/me/payslips` | Page query, max size 100; response remains a raw array | Scalar projection avoids `snapshot_json` and PDF bytes; snapshot field remains present as null for compatibility | Not measured; response carries `X-Pagination-*` headers |
| `GET /api/ai/dispatch/pending` | Zero-based page/size, max size 100 | Page of decisions plus one batch truck/driver fetch; stable created-at/id sort | Not measured; no per-row truck lookup remains |
| `GET /api/ai/dispatch/sessions/{id}` | Zero-based page/size for suggestions | Page of decisions plus one batch truck/driver fetch | Not measured; nested `decisionsPagination` is additive |
| `GET /api/ai/gaps` | 1-based page/pageSize for nested gaps | Page query and database counts replace full gap-list materialization | Not measured; nested `pagination` is additive |
| `GET /api/loads/{loadId}/timeline` | 1-based page/pageSize for events | ToOne graph for load/trip/stop | Not measured; nested `pagination` is additive |
| `GET /api/lark/base/loads` | Upstream page size clamps to 1..100 | Existing external cursor token remains in use | Not measured; upstream continuation token is not currently returned by this API |

The existing list searches for invoices, payments, trucks, inspections and documents now fetch their required ToOne relations with entity graphs. Hibernate `default_batch_fetch_size=32` is set for lazy collections such as role claims; no collection is join-fetched alongside a pageable query. `open-in-view=false` remains enabled. No source migration or index was added: representative `EXPLAIN (ANALYZE, BUFFERS)` evidence is still unavailable, and adding speculative indexes would not establish the requested performance.

Targeted verification so far: `./mvnw -Dtest=PaginationHttpContractTest test` passed 9 tests. `./mvnw -DskipTests test-compile` passed. The Postgres statistics test remains skipped because `TASK_DB_URL` is unset. No endpoint-level before/after SQL count, latency, or representative execution plan has been captured yet.

### Remaining unbounded or unmeasured work

- `/api/reports/profitability/by-load`, `/by-lane`, and `/by-truck` still load every load; by-load also performs per-load financial queries. They need a batch financial-query design before paging so report totals remain correct and request cost stops scaling by query-per-load.
- `/api/reports/drivers/ranking` bounds its output to 50 but still reads all employees, assignments and loads into memory. On-time, delay, transit-time and operating-cost calculators still use unpaged full-table reads.
- `/api/payroll/runs/{id}` and `/api/driver-settlements/{id}` still return nested item/line collections without pagination metadata.
- `GET /api/lark/base/loads` is size-capped, but the current facade discards the upstream `has_more` and next-page token, so frontend cursor navigation is not yet exposed.
- Several list DTOs still include non-screen fields (for example invoice notes and payslip JSON is now null); splitting those contracts safely requires consumer review.
- No EXPLAIN-driven migration has been made. `pg_trgm`/GIN indexes remain pending representative query plans and data.

## Contract and client inventory

- Existing 1-based routes use `page` + `pageSize`; AI and payroll routes use 0-based `page` + `size`.
- Existing `PagedResponse` keys are `items`, `totalItems`, `totalPages`, `currentPage`, and `pageSize`; current frontend contracts also include a raw Spring Page route.
- Postman collections are present under `postman/`. Any newly added lookup/cursor endpoint or changed pagination parameter must be reflected in the collections and the portable frontend API contract.
- Preserve all existing dirty working-tree changes. In particular, `DriverController`, `LoadService`, and `application.yml` already contain user changes at audit time.
