-- Rename mixed-case audit columns to standard snake_case
-- on all business-schema tables that inherit auditing columns.

-- Tables with audit columns (24 tables from V1__baseline_business_schema.sql)

ALTER TABLE ai_dispatch_sessions RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE ai_dispatch_sessions RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE ai_dispatch_sessions RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE ai_dispatch_sessions RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE ai_dispatch_decisions RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE ai_dispatch_decisions RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE ai_dispatch_decisions RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE ai_dispatch_decisions RENAME COLUMN "LastModifiedBy" TO last_modified_by;

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

ALTER TABLE load_board_configurations RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE load_board_configurations RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE load_board_configurations RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE load_board_configurations RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE load_condition_reports RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE load_condition_reports RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE load_condition_reports RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE load_condition_reports RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE load_exceptions RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE load_exceptions RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE load_exceptions RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE load_exceptions RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE messages RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE messages RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE messages RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE messages RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE notifications RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE notifications RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE notifications RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE notifications RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE payments RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE payments RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE payments RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE payments RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE payment_links RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE payment_links RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE payment_links RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE payment_links RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE tenants RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE tenants RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE tenants RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE tenants RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE tenant_roles RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE tenant_roles RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE tenant_roles RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE tenant_roles RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE telegram_chats RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE telegram_chats RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE telegram_chats RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE telegram_chats RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE trips RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE trips RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE trips RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE trips RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE trip_stops RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE trip_stops RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE trip_stops RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE trip_stops RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE trucks RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE trucks RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE trucks RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE trucks RENAME COLUMN "LastModifiedBy" TO last_modified_by;

ALTER TABLE dvir_reports RENAME COLUMN "CreatedAt" TO created_at;
ALTER TABLE dvir_reports RENAME COLUMN "CreatedBy" TO created_by;
ALTER TABLE dvir_reports RENAME COLUMN "LastModifiedAt" TO last_modified_at;
ALTER TABLE dvir_reports RENAME COLUMN "LastModifiedBy" TO last_modified_by;