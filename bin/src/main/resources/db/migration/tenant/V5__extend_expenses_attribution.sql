-- V5__extend_expenses_attribution.sql
ALTER TABLE expenses
    ADD COLUMN load_id UUID,
    ADD COLUMN trip_id UUID,
    ADD COLUMN employee_id UUID,
    ADD COLUMN maintenance_record_id UUID,
    ADD COLUMN document_id UUID;

ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_load
        FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_expenses_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_expenses_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_expenses_maintenance_record
        FOREIGN KEY (maintenance_record_id) REFERENCES maintenance_records(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_expenses_document
        FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL;

CREATE INDEX ix_expenses_load_id ON expenses(load_id);
CREATE INDEX ix_expenses_trip_id ON expenses(trip_id);
CREATE INDEX ix_expenses_maintenance_record_id ON expenses(maintenance_record_id);
