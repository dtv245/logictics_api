# Task 3.2 accessorial / detention contract

The canonical routes are owned by `AccessorialController`; legacy duplicate adapters are not Spring components. The schema is in the existing canonical V9 shared shipment-cost/accessorial migration, not a second V9.

Create/list accessorials and preview stop detention require ADMIN, ACCOUNTANT or DISPATCHER. Approval requires ADMIN or ACCOUNTANT. The authenticated tenant employee is the audit actor; a caller-supplied approvedBy/actorId is not used. Approval serializes on the accessorial row, accepts PENDING_APPROVAL, and repeats APPROVED/INVOICED without changing original audit. Rejected/voided sources cannot be approved. Company-cost projection commits in the same transaction with a unique source key.

Customer amount is a customer charge, company cost is the incurred company liability, and driver pay is compensation. They are independently validated, nonnegative and rounded to currency scale. Only company cost projects to the ACTUAL/APPROVED shipment ledger. Customer and driver amounts are not added to it; their invoice/settlement workflows have separate ownership. Zero company cost creates no cost record.

Create rejects missing reference IDs, invalid types, negative amounts/quantities/rates, and cross-load/trip/stop attribution. A stop can supply its owning trip when tripId is absent. No silent null references, arbitrary status or approval audit fields are accepted.

Detention is a read-only preview. Arrival/departure are required and compared as instants; missing/reversed timestamps reject. No result/snapshot is persisted merely by previewing. Free minutes and block minutes must be nonnegative; blockMinutes=0 selects continuous hourly pricing. Rates must be nonnegative. Driver rate is independently optional (zero if no compensation specified).

Durations are computed with fractional seconds. Excess = max(0, dwellSeconds - freeMinutes*60). Blocks = ceil(excessSeconds/(blockMinutes*60)); even one second/microsecond beyond a boundary starts another block. Customer amount = round(chargedSeconds*hourlyRate/3600). Driver amount uses its own hourly rate. Multiply before division and round only at the final currency boundary; display units are never reused as pricing inputs.

Legacy integer dwellMinutes/excessMinutes are whole-minute displays. New dwellSeconds/excessSeconds retain the factual fractional duration. billableUnits means blocks in block mode and hours in continuous mode. Example: 45 free minutes, 15-minute blocks, 60 USD/hour, dwell=45m1s → one block, customer=15.00 USD. Exactly 45m → zero charge. Zero is returned only for measured free-window dwell or an explicit zero rate.

Tests cover free/exact/partial block, continuous rounding, currency scale, timezone offsets, missing/reversed timestamps and negative rates. PostgreSQL verifies concurrent approval, one company-only cost, original audit retention, refused voided reapproval and authenticated actor despite spoofed query parameters. FinancialReportSecurityTest checks that drivers/dispatchers cannot approve.
