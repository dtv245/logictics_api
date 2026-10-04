# Phase 1 reporting contract

Financial report and customer-balance endpoints require ADMIN, ACCOUNTANT, PAYROLL or PAYROLL_MANAGER. Tenant selection comes from authenticated infrastructure, not a report query parameter. No report performs FX conversion. Mixed, blank or missing source currencies reject the calculation; a caller-supplied currency cannot relabel source amounts.

Revenue uses eligible invoice subtotals, never quoted load freight. Completed payments reduce customer balances; pending/failed payments do not. Invoice line currencies and their rounded subtotal must reconcile. No legacy invoice payroll field is a revenue source.

Rounding is configured independently for invoice, report and allocation boundaries in `app.calculation.rounding`, with an explicit version. Deployment defaults are HALF_UP and LOGISTICSX-ROUNDING-V1; change version whenever rounding behavior changes. Raw sums are rounded at the applicable output boundary, using currency scale (for example USD 2, VND 0).

OTD/delay/transit/resolution calculations compare timestamp instants. Any positive delay, including less than one minute, counts as late. Durations retain sub-minute precision before final rounding. Empty populations and invalid/missing duration measurements return null averages rather than invented zeroes. Counts remain factual.

Legacy `loads.distance` and `trips.total_distance` are not proven miles and never feed mileage metrics. Fuel MPG requires complete explicit V3 actual trip mileage and complete fuel quantities. US gallons and liters are supported (1 US gallon = 3.785411784 liters); unknown units make the metric unavailable, not partially divided. Missing quantity is not zero.

Maintenance reports return labor + parts, not legacy total_cost. The legacy maintenance schema has no currency: `currency: null`, `currencyAvailability: PARTIAL`, reason `MAINTENANCE_CURRENCY_NOT_STORED_IN_LEGACY_SCHEMA`. Monthly reports expose this separately; their top-level currency applies only to qualified revenue/expense amounts. No combined monetary total is published with unknown maintenance currency.

Known operating costs exclude driver compensation. Expense and maintenance subtotals remain separate; the combined metric remains PARTIAL with `DUPLICATE_SOURCE_NOT_FULLY_RECONCILED`. No amount/date/vendor heuristic is used to detect duplicates. V5 maintenance-record references permit future deterministic reconciliation only once source currency semantics are established. Missing eligible mileage produces UNAVAILABLE CPM. An expense-only CPM can be PARTIAL, never labeled fully reconciled.

Example metric: `{"value":null,"availability":"UNAVAILABLE","basis":"EXPLICIT_ACTUAL_TRIP_MILES","reason":"NO_EXPLICIT_ACTUAL_MILES_FOUND_IN_PERIOD"}`. Consumers must honor availability/basis/reason rather than substituting zero.
