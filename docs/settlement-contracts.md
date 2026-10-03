# Driver settlement calculation contracts

Phase 4B uses explicit assignment actual/planned all miles, never legacy/trip/planned fallback. Missing selected input raises MILEAGE_VALIDATION_REQUIRED before any settlement/snapshot is persisted. Snapshot records basis, assignment ID, policy ID/version, quantity/rate, raw/rounded amounts and rounding version.

Phase 4C uses persisted time_entries.total_hours for HOURLY, not legacy Invoice payroll fields. Duration columns have explicit INTERVAL_SECOND JDBC mapping, verified against real PostgreSQL intervals. Zero explicit hours is valid; absent/negative inputs fail validation. Per-load pay uses each distinct eligible load once within calculation. Daily pay uses one unit per date/policy; flat rate uses one unit per pay period with policy effective at period start. Work calculations snapshot source IDs, method, quantity, rate and policy/rounding versions. Each payable line rounds once before aggregation so settlement totals reconcile with lines.

Current calendar dependency: existing audit specifies UTC persistence and LocalDate for periods/policy dates; settlement period boundaries currently use UTC. Region-specific payroll jurisdiction does not silently change historical settlement calendar semantics.

Phase 4D–4F remain separate gates: invoice subtotal reconciliation, accessorial recipient attribution, work-date/source deduplication across periods, lock/reconciliation/concurrent original creation and append-only correction idempotency. No full Phase 4 completion is claimed at the calculator-only checkpoints.
