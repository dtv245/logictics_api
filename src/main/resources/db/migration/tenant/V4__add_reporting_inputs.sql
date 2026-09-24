-- Inputs the reporting endpoints need and the baseline schema never recorded.
--
-- None of this creates data. Every column added here is NULL for every existing row, and the
-- aggregates that depend on them report "unavailable" rather than treating NULL as zero or false.
-- The migration opens the door; a feed has to walk through it.

-- ── Fleet odometer ────────────────────────────────────────────────────────────────────────────
-- trucks had no odometer at all, which is why fleet utilisation and cost-per-mile have never been
-- computable. int4 rather than numeric to match maintenance_records.odometer_reading, the one
-- odometer this schema already stores: the same physical quantity in two types is a join waiting
-- to need a cast.
ALTER TABLE trucks ADD COLUMN current_odometer int4 NULL;
ALTER TABLE trucks ADD COLUMN current_odometer_recorded_at timestamptz NULL;

-- ── Maintenance downtime and classification ───────────────────────────────────────────────────
-- service_date marks when work happened, not how long the vehicle was out, so unplanned downtime
-- was unmeasurable. These two columns make it measurable going forward; they cannot retroactively
-- describe a vehicle that was already repaired.
ALTER TABLE maintenance_records ADD COLUMN downtime_start_at timestamptz NULL;
ALTER TABLE maintenance_records ADD COLUMN downtime_end_at timestamptz NULL;

-- bool NULL, deliberately not "NOT NULL DEFAULT false".
-- A default of false would assert that every historical row was planned and was not a breakdown --
-- an assertion nothing in this database supports. NULL means unclassified, and the aggregate drops
-- unclassified rows from both the numerator and the denominator while reporting how many it
-- dropped. A wrong "0% breakdowns" is worse than an honest "not known".
ALTER TABLE maintenance_records ADD COLUMN is_unplanned bool NULL;
ALTER TABLE maintenance_records ADD COLUMN is_breakdown bool NULL;

-- total_cost was the only monetary column in this schema with no currency beside it, so it could
-- not be summed alongside anything. NULL means the currency was never recorded, not that it is
-- the requested one.
ALTER TABLE maintenance_records ADD COLUMN total_cost_currency varchar(3) NULL;

-- ── Odometer history ──────────────────────────────────────────────────────────────────────────
-- A single current_odometer column gives a position, not a distance: miles driven in a period is
-- the difference between two readings, and that needs the readings kept. Append-only by intent --
-- a reading is a fact about a moment and is never revised; a correction is a new reading.
--
-- No audit columns: recorded_at is the domain timestamp and is NOT NULL, so the four auditing
-- columns would restate it. Nothing writes this table yet; it exists so that the day a telematics
-- feed does, the query behind totalMiles has somewhere to read from.
CREATE TABLE vehicle_mileage_readings (
  id uuid NOT NULL,
  truck_id uuid NOT NULL,
  reading_value int4 NOT NULL,
  recorded_at timestamptz NOT NULL,
  source varchar(50) NULL,
  CONSTRAINT pk_vehicle_mileage_readings PRIMARY KEY (id),
  CONSTRAINT fk_vehicle_mileage_readings_trucks_truck_id
    FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);

-- The only access path that matters: readings for one truck in time order, to difference
-- consecutive rows into a period distance.
CREATE INDEX ix_vehicle_mileage_readings_truck_id_recorded_at
  ON public.vehicle_mileage_readings USING btree (truck_id, recorded_at);

-- ── Deliberately no new indexes on invoices or payments ───────────────────────────────────────
-- The reporting queries filter on total_currency (cardinality 1-2) and lower(status) (cardinality
-- ~9), both over a single tenant's own database. A btree on either is low-cardinality enough that
-- the planner will prefer a sequential scan, and the status predicate is a function call that a
-- plain column index cannot serve anyway. An index nothing reads still costs a write on every
-- invoice insert. If these tables ever grow past what one tenant's report can scan, the index
-- worth adding is composite on (lower(status), total_currency) -- not the single-column pair.
