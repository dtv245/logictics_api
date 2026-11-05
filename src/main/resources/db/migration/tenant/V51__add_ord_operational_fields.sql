-- Additive, nullable ORD fields. No backfill: NULL means "not captured yet" and must
-- never be interpreted as zero weight, zero volume or the (0, 0) coordinate.
ALTER TABLE loads ADD COLUMN weight_lbs float8 NULL;
ALTER TABLE loads ADD COLUMN volume_cuft float8 NULL;
ALTER TABLE loads ADD CONSTRAINT ck_loads_weight_positive
    CHECK (weight_lbs IS NULL OR weight_lbs > 0);
ALTER TABLE loads ADD CONSTRAINT ck_loads_volume_positive
    CHECK (volume_cuft IS NULL OR volume_cuft > 0);

ALTER TABLE terminals ADD COLUMN latitude float8 NULL;
ALTER TABLE terminals ADD COLUMN longitude float8 NULL;
ALTER TABLE terminals ADD COLUMN working_hours_open time NULL;
ALTER TABLE terminals ADD COLUMN working_hours_close time NULL;
ALTER TABLE terminals ADD CONSTRAINT ck_terminals_coordinates_pair
    CHECK ((latitude IS NULL) = (longitude IS NULL));
ALTER TABLE terminals ADD CONSTRAINT ck_terminals_latitude_range
    CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90);
ALTER TABLE terminals ADD CONSTRAINT ck_terminals_longitude_range
    CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180);

ALTER TABLE customers ADD COLUMN payment_terms_days int4 NULL;
ALTER TABLE customers ADD COLUMN default_currency varchar(3) NULL;
ALTER TABLE customers ADD CONSTRAINT ck_customers_payment_terms_days
    CHECK (payment_terms_days IS NULL OR payment_terms_days >= 0);
ALTER TABLE customers ADD CONSTRAINT ck_customers_default_currency
    CHECK (default_currency IS NULL OR default_currency ~ '^[A-Z]{3}$');
