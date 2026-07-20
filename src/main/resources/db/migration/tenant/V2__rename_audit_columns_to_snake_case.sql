-- Rename mixed-case audit columns to standard snake_case
-- on all business-schema tables that inherit auditing columns.

-- Tables with audit columns from V1__baseline_business_schema.sql.
-- Tables without the four legacy audit columns are intentionally omitted.

ALTER TABLE ai_dispatch_sessions RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE ai_dispatch_sessions RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE ai_dispatch_sessions RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE ai_dispatch_sessions RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE containers RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE containers RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE containers RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE containers RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE customers RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE customers RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE customers RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE customers RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE customer_users RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE customer_users RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE customer_users RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE customer_users RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE employees RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE employees RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE employees RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE employees RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE expenses RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE expenses RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE expenses RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE expenses RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE invoices RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE invoices RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE invoices RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE invoices RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE loads RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE loads RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE loads RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE loads RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE load_board_listings RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE load_board_listings RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE load_board_listings RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE load_board_listings RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE load_condition_reports RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE load_condition_reports RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE load_condition_reports RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE load_condition_reports RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE load_exceptions RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE load_exceptions RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE load_exceptions RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE load_exceptions RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE payments RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE payments RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE payments RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE payments RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE payment_links RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE payment_links RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE payment_links RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE payment_links RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE trips RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE trips RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE trips RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE trips RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE dvir_reports RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE dvir_reports RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE dvir_reports RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE dvir_reports RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE terminals RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE terminals RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE terminals RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE terminals RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE maintenance_schedules RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE maintenance_schedules RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE maintenance_schedules RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE maintenance_schedules RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE posted_trucks RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE posted_trucks RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE posted_trucks RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE posted_trucks RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE tracking_links RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE tracking_links RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE tracking_links RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE tracking_links RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE accident_reports RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE accident_reports RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE accident_reports RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE accident_reports RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE maintenance_records RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE maintenance_records RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE maintenance_records RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE maintenance_records RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE time_entries RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE time_entries RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE time_entries RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE time_entries RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE documents RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE documents RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE documents RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE documents RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE driver_licenses RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE driver_licenses RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE driver_licenses RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE driver_licenses RENAME COLUMN "LastModifiedBy" TO last_modified_by;
