-- public."__EFMigrationsHistory" definition

-- Drop table

-- DROP TABLE "__EFMigrationsHistory";

CREATE TABLE "__EFMigrationsHistory" (
migration_id varchar(150) NOT NULL,
product_version varchar(32) NOT NULL,
CONSTRAINT pk___ef_migrations_history PRIMARY KEY (migration_id)
);


-- public.ai_dispatch_sessions definition

-- Drop table

-- DROP TABLE ai_dispatch_sessions;

CREATE TABLE ai_dispatch_sessions (
id uuid NOT NULL,
"number" int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
"mode" text NOT NULL,
status text NOT NULL,
triggered_by_user_id uuid NULL,
started_at timestamptz NOT NULL,
completed_at timestamptz NULL,
instructions text NULL,
input_tokens_used int4 NOT NULL,
output_tokens_used int4 NOT NULL,
cache_read_tokens int4 NOT NULL,
cache_creation_tokens int4 NOT NULL,
estimated_cost_usd numeric NOT NULL,
model_used text NULL,
decision_count int4 NOT NULL,
summary varchar(4000) NULL,
error_message varchar(2000) NULL,
request_cost int4 DEFAULT 1 NOT NULL,
is_overage bool NOT NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_ai_dispatch_sessions PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_ai_dispatch_sessions_number ON public.ai_dispatch_sessions USING btree (number);


-- public.api_keys definition

-- Drop table

-- DROP TABLE api_keys;

CREATE TABLE api_keys (
id uuid NOT NULL,
"name" varchar(128) NOT NULL,
key_hash varchar(128) NOT NULL,
key_prefix varchar(64) NOT NULL,
created_at timestamptz NOT NULL,
last_used_at timestamptz NULL,
CONSTRAINT pk_api_keys PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_api_keys_key_hash ON public.api_keys USING btree (key_hash);


-- public.customers definition

-- Drop table

-- DROP TABLE customers;

CREATE TABLE customers (
id uuid NOT NULL,
"name" text NOT NULL,
email text NULL,
phone text NULL,
status text NOT NULL,
notes text NULL,
tax_id varchar(50) NULL,
is_vat_exempt bool NOT NULL,
address_city text NULL,
address_country text NULL,
address_line1 text NULL,
address_line2 text NULL,
address_state text NULL,
address_zip_code text NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_customers PRIMARY KEY (id)
);


-- public.eld_provider_configurations definition

-- Drop table

-- DROP TABLE eld_provider_configurations;

CREATE TABLE eld_provider_configurations (
id uuid NOT NULL,
provider_type text NOT NULL,
api_key varchar(500) NOT NULL,
api_secret varchar(500) NULL,
access_token varchar(2000) NULL,
refresh_token varchar(2000) NULL,
token_expires_at timestamptz NULL,
webhook_secret varchar(500) NULL,
is_active bool NOT NULL,
last_synced_at timestamptz NULL,
external_account_id varchar(100) NULL,
CONSTRAINT pk_eld_provider_configurations PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_eld_provider_configurations_provider_type ON public.eld_provider_configurations USING btree (provider_type);


-- public.load_board_configurations definition

-- Drop table

-- DROP TABLE load_board_configurations;

CREATE TABLE load_board_configurations (
id uuid NOT NULL,
provider_type text NOT NULL,
api_key varchar(500) NOT NULL,
api_secret varchar(500) NULL,
access_token varchar(2000) NULL,
refresh_token varchar(2000) NULL,
token_expires_at timestamptz NULL,
webhook_secret varchar(500) NULL,
is_active bool NOT NULL,
last_synced_at timestamptz NULL,
external_account_id varchar(100) NULL,
company_dot_number varchar(20) NULL,
company_mc_number varchar(20) NULL,
CONSTRAINT pk_load_board_configurations PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_load_board_configurations_provider_type ON public.load_board_configurations USING btree (provider_type);


-- public.notifications definition

-- Drop table

-- DROP TABLE notifications;

CREATE TABLE notifications (
id uuid NOT NULL,
title text NOT NULL,
message text NOT NULL,
is_read bool NOT NULL,
created_date timestamptz NOT NULL,
CONSTRAINT pk_notifications PRIMARY KEY (id)
);


-- public.telegram_chats definition

-- Drop table

-- DROP TABLE telegram_chats;

CREATE TABLE telegram_chats (
id uuid NOT NULL,
chat_id int8 NOT NULL,
chat_type text NOT NULL,
"role" text NULL,
user_id uuid NULL,
username varchar(128) NULL,
first_name varchar(128) NULL,
group_title varchar(256) NULL,
notifications_enabled bool NOT NULL,
connected_at timestamptz NOT NULL,
last_interaction_at timestamptz NULL,
CONSTRAINT pk_telegram_chats PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_telegram_chats_chat_id ON public.telegram_chats USING btree (chat_id);
CREATE INDEX ix_telegram_chats_user_id ON public.telegram_chats USING btree (user_id);


-- public.tenant_roles definition

-- Drop table

-- DROP TABLE tenant_roles;

CREATE TABLE tenant_roles (
id uuid NOT NULL,
"name" text NOT NULL,
display_name text NULL,
normalized_name text NOT NULL,
CONSTRAINT pk_tenant_roles PRIMARY KEY (id)
);


-- public.terminals definition

-- Drop table

-- DROP TABLE terminals;

CREATE TABLE terminals (
id uuid NOT NULL,
"name" varchar(200) NOT NULL,
code varchar(5) NOT NULL,
country_code varchar(2) NOT NULL,
"type" text NOT NULL,
notes varchar(2000) NULL,
address_city text NOT NULL,
address_country text NOT NULL,
address_line1 text NOT NULL,
address_line2 text NULL,
address_state text NOT NULL,
address_zip_code text NOT NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_terminals PRIMARY KEY (id)
);
CREATE UNIQUE INDEX ix_terminals_code ON public.terminals USING btree (code);


-- public.ai_dispatch_decisions definition

-- Drop table

-- DROP TABLE ai_dispatch_decisions;

CREATE TABLE ai_dispatch_decisions (
id uuid NOT NULL,
session_id uuid NOT NULL,
"type" text NOT NULL,
status text NOT NULL,
reasoning varchar(4000) NOT NULL,
tool_name varchar(100) NULL,
tool_input text NULL,
tool_output text NULL,
load_id uuid NULL,
truck_id uuid NULL,
trip_id uuid NULL,
created_at timestamptz NOT NULL,
executed_at timestamptz NULL,
approved_by_user_id uuid NULL,
rejection_reason varchar(1000) NULL,
CONSTRAINT pk_ai_dispatch_decisions PRIMARY KEY (id),
CONSTRAINT fk_ai_dispatch_decisions_ai_dispatch_session_session_id FOREIGN KEY (session_id) REFERENCES ai_dispatch_sessions(id) ON DELETE CASCADE
);
CREATE INDEX ix_ai_dispatch_decisions_session_id ON public.ai_dispatch_decisions USING btree (session_id);
CREATE INDEX ix_ai_dispatch_decisions_status ON public.ai_dispatch_decisions USING btree (status);


-- public.containers definition

-- Drop table

-- DROP TABLE containers;

CREATE TABLE containers (
id uuid NOT NULL,
"number" varchar(11) NOT NULL,
iso_type text NOT NULL,
seal_number varchar(50) NULL,
booking_reference varchar(100) NULL,
bill_of_lading_number varchar(100) NULL,
is_laden bool NOT NULL,
gross_weight numeric(18, 2) NOT NULL,
status text NOT NULL,
current_terminal_id uuid NULL,
notes varchar(2000) NULL,
loaded_at timestamptz NULL,
delivered_at timestamptz NULL,
returned_at timestamptz NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_containers PRIMARY KEY (id),
CONSTRAINT fk_containers_terminal_current_terminal_id FOREIGN KEY (current_terminal_id) REFERENCES terminals(id) ON DELETE SET NULL
);
CREATE INDEX ix_containers_current_terminal_id ON public.containers USING btree (current_terminal_id);
CREATE UNIQUE INDEX ix_containers_number ON public.containers USING btree (number);


-- public.customer_users definition

-- Drop table

-- DROP TABLE customer_users;

CREATE TABLE customer_users (
id uuid NOT NULL,
user_id uuid NOT NULL,
customer_id uuid NOT NULL,
email varchar(256) NOT NULL,
is_active bool NOT NULL,
last_login_at timestamptz NULL,
display_name varchar(256) NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_customer_users PRIMARY KEY (id),
CONSTRAINT fk_customer_users_customers_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ix_customer_users_customer_id_user_id ON public.customer_users USING btree (customer_id, user_id);
CREATE INDEX ix_customer_users_email ON public.customer_users USING btree (email);
CREATE INDEX ix_customer_users_user_id ON public.customer_users USING btree (user_id);


-- public.employees definition

-- Drop table

-- DROP TABLE employees;

CREATE TABLE employees (
id uuid NOT NULL,
email text NOT NULL,
first_name text NOT NULL,
last_name text NOT NULL,
phone_number text NULL,
salary_type text NOT NULL,
status text NOT NULL,
joined_date timestamptz NOT NULL,
device_token text NULL,
stripe_connected_account_id text NULL,
role_id uuid NULL,
address_city text NULL,
address_country text NULL,
address_line1 text NULL,
address_line2 text NULL,
address_state text NULL,
address_zip_code text NULL,
salary_amount numeric(18, 2) NOT NULL,
salary_currency varchar(3) NOT NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_employees PRIMARY KEY (id),
CONSTRAINT fk_employees_tenant_role_role_id FOREIGN KEY (role_id) REFERENCES tenant_roles(id) ON DELETE SET NULL
);
CREATE INDEX ix_employees_role_id ON public.employees USING btree (role_id);


-- public.hos_logs definition

-- Drop table

-- DROP TABLE hos_logs;

CREATE TABLE hos_logs (
id uuid NOT NULL,
employee_id uuid NOT NULL,
log_date timestamptz NOT NULL,
duty_status text NOT NULL,
start_time timestamptz NOT NULL,
end_time timestamptz NULL,
duration_minutes int4 NOT NULL,
"location" varchar(500) NULL,
latitude float8 NULL,
longitude float8 NULL,
remark varchar(1000) NULL,
external_log_id varchar(100) NULL,
provider_type text NOT NULL,
CONSTRAINT pk_hos_logs PRIMARY KEY (id),
CONSTRAINT fk_hos_logs_employees_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);
CREATE INDEX ix_hos_logs_employee_id_log_date ON public.hos_logs USING btree (employee_id, log_date);
CREATE INDEX ix_hos_logs_external_log_id ON public.hos_logs USING btree (external_log_id);


-- public.hos_violations definition

-- Drop table

-- DROP TABLE hos_violations;

CREATE TABLE hos_violations (
id uuid NOT NULL,
employee_id uuid NOT NULL,
violation_date timestamptz NOT NULL,
violation_type text NOT NULL,
description varchar(1000) NOT NULL,
severity_level int4 NOT NULL,
is_resolved bool NOT NULL,
resolved_at timestamptz NULL,
external_violation_id varchar(100) NULL,
provider_type text NOT NULL,
rule_set_code varchar(32) DEFAULT 'FMCSA'::character varying NOT NULL,
CONSTRAINT pk_hos_violations PRIMARY KEY (id),
CONSTRAINT fk_hos_violations_employees_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);
CREATE INDEX ix_hos_violations_employee_id_violation_date ON public.hos_violations USING btree (employee_id, violation_date);
CREATE INDEX ix_hos_violations_external_violation_id ON public.hos_violations USING btree (external_violation_id);


-- public.tenant_role_claims definition

-- Drop table

-- DROP TABLE tenant_role_claims;

CREATE TABLE tenant_role_claims (
id uuid NOT NULL,
claim_type text NOT NULL,
claim_value text NOT NULL,
role_id uuid NOT NULL,
CONSTRAINT pk_tenant_role_claims PRIMARY KEY (id),
CONSTRAINT fk_tenant_role_claims_tenant_role_role_id FOREIGN KEY (role_id) REFERENCES tenant_roles(id) ON DELETE CASCADE
);
CREATE INDEX ix_tenant_role_claims_role_id ON public.tenant_role_claims USING btree (role_id);


-- public.trucks definition

-- Drop table

-- DROP TABLE trucks;

CREATE TABLE trucks (
id uuid NOT NULL,
"number" text NOT NULL,
"type" text NOT NULL,
vehicle_capacity int4 NOT NULL,
status text NOT NULL,
make text NULL,
model text NULL,
"year" int4 NULL,
vin text NULL,
license_plate text NULL,
license_plate_state text NULL,
is_hazmat_placarded bool NOT NULL,
main_driver_id uuid NULL,
secondary_driver_id uuid NULL,
adr_equipment_adr_cert_expires_at timestamptz NULL,
adr_equipment_allowed_classes text NOT NULL,
adr_equipment_is_adr_certified bool NOT NULL,
adr_equipment_orange_plate_number varchar(8) NULL,
current_address_city text NULL,
current_address_country text NULL,
current_address_line1 text NULL,
current_address_line2 text NULL,
current_address_state text NULL,
current_address_zip_code text NULL,
current_location_latitude float8 NULL,
current_location_longitude float8 NULL,
CONSTRAINT pk_trucks PRIMARY KEY (id),
CONSTRAINT fk_trucks_employees_main_driver_id FOREIGN KEY (main_driver_id) REFERENCES employees(id) ON DELETE SET NULL,
CONSTRAINT fk_trucks_employees_secondary_driver_id FOREIGN KEY (secondary_driver_id) REFERENCES employees(id) ON DELETE SET NULL
);
CREATE INDEX ix_trucks_main_driver_id ON public.trucks USING btree (main_driver_id);
CREATE UNIQUE INDEX ix_trucks_number ON public.trucks USING btree (number);
CREATE INDEX ix_trucks_secondary_driver_id ON public.trucks USING btree (secondary_driver_id);


-- public.driver_behavior_events definition

-- Drop table

-- DROP TABLE driver_behavior_events;

CREATE TABLE driver_behavior_events (
id uuid NOT NULL,
employee_id uuid NOT NULL,
truck_id uuid NULL,
event_type text NOT NULL,
occurred_at timestamptz NOT NULL,
provider_type text NOT NULL,
latitude float8 NULL,
longitude float8 NULL,
"location" varchar(500) NULL,
speed_mph float8 NULL,
speed_limit_mph float8 NULL,
g_force float8 NULL,
duration_seconds int4 NULL,
external_event_id varchar(100) NULL,
raw_event_data_json text NULL,
is_reviewed bool NOT NULL,
reviewed_by_id uuid NULL,
reviewed_at timestamptz NULL,
review_notes varchar(1000) NULL,
is_dismissed bool NULL,
CONSTRAINT pk_driver_behavior_events PRIMARY KEY (id),
CONSTRAINT fk_driver_behavior_events_employee_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
CONSTRAINT fk_driver_behavior_events_employee_reviewed_by_id FOREIGN KEY (reviewed_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_driver_behavior_events_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE SET NULL
);
CREATE INDEX ix_driver_behavior_events_employee_id_occurred_at ON public.driver_behavior_events USING btree (employee_id, occurred_at);
CREATE INDEX ix_driver_behavior_events_event_type ON public.driver_behavior_events USING btree (event_type);
CREATE INDEX ix_driver_behavior_events_external_event_id ON public.driver_behavior_events USING btree (external_event_id);
CREATE INDEX ix_driver_behavior_events_reviewed_by_id ON public.driver_behavior_events USING btree (reviewed_by_id);
CREATE INDEX ix_driver_behavior_events_truck_id ON public.driver_behavior_events USING btree (truck_id);


-- public.driver_hos_statuses definition

-- Drop table

-- DROP TABLE driver_hos_statuses;

CREATE TABLE driver_hos_statuses (
id uuid NOT NULL,
employee_id uuid NOT NULL,
external_driver_id varchar(100) NULL,
provider_type text NOT NULL,
current_duty_status text NOT NULL,
status_changed_at timestamptz NOT NULL,
driving_minutes_remaining int4 NOT NULL,
on_duty_minutes_remaining int4 NOT NULL,
cycle_minutes_remaining int4 NOT NULL,
time_until_break_required interval NULL,
is_in_violation bool NOT NULL,
last_updated_at timestamptz NOT NULL,
next_mandatory_break_at timestamptz NULL,
CONSTRAINT pk_driver_hos_statuses PRIMARY KEY (id),
CONSTRAINT fk_driver_hos_statuses_employee_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ix_driver_hos_statuses_employee_id ON public.driver_hos_statuses USING btree (employee_id);


-- public.eld_driver_mappings definition

-- Drop table

-- DROP TABLE eld_driver_mappings;

CREATE TABLE eld_driver_mappings (
id uuid NOT NULL,
employee_id uuid NOT NULL,
provider_type text NOT NULL,
external_driver_id varchar(100) NOT NULL,
external_driver_name varchar(200) NULL,
is_sync_enabled bool NOT NULL,
last_synced_at timestamptz NULL,
CONSTRAINT pk_eld_driver_mappings PRIMARY KEY (id),
CONSTRAINT fk_eld_driver_mappings_employee_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);
CREATE INDEX ix_eld_driver_mappings_employee_id ON public.eld_driver_mappings USING btree (employee_id);
CREATE UNIQUE INDEX ix_eld_driver_mappings_provider_type_employee_id ON public.eld_driver_mappings USING btree (provider_type, employee_id);
CREATE UNIQUE INDEX ix_eld_driver_mappings_provider_type_external_driver_id ON public.eld_driver_mappings USING btree (provider_type, external_driver_id);


-- public.eld_vehicle_mappings definition

-- Drop table

-- DROP TABLE eld_vehicle_mappings;

CREATE TABLE eld_vehicle_mappings (
id uuid NOT NULL,
truck_id uuid NOT NULL,
provider_type text NOT NULL,
external_vehicle_id varchar(100) NOT NULL,
external_vehicle_name varchar(200) NULL,
is_sync_enabled bool NOT NULL,
last_synced_at timestamptz NULL,
CONSTRAINT pk_eld_vehicle_mappings PRIMARY KEY (id),
CONSTRAINT fk_eld_vehicle_mappings_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ix_eld_vehicle_mappings_provider_type_external_vehicle_id ON public.eld_vehicle_mappings USING btree (provider_type, external_vehicle_id);
CREATE UNIQUE INDEX ix_eld_vehicle_mappings_provider_type_truck_id ON public.eld_vehicle_mappings USING btree (provider_type, truck_id);
CREATE INDEX ix_eld_vehicle_mappings_truck_id ON public.eld_vehicle_mappings USING btree (truck_id);


-- public.expenses definition

-- Drop table

-- DROP TABLE expenses;

CREATE TABLE expenses (
id uuid NOT NULL,
"number" int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
"type" text NOT NULL,
status text NOT NULL,
vendor_name varchar(255) NULL,
expense_date timestamptz NOT NULL,
receipt_blob_path varchar(500) NULL,
notes varchar(2000) NULL,
approved_by_id varchar(50) NULL,
approved_at timestamptz NULL,
rejection_reason varchar(500) NULL,
amount_amount numeric(18, 2) NOT NULL,
amount_currency varchar(3) NOT NULL,
truck_id uuid NULL,
vendor_address varchar(500) NULL,
vendor_phone varchar(20) NULL,
repair_description varchar(2000) NULL,
estimated_completion_date timestamptz NULL,
actual_completion_date timestamptz NULL,
category text NULL,
truck_expense_truck_id uuid NULL,
truck_expense_category text NULL,
odometer_reading int4 NULL,
quantity numeric NULL,
quantity_unit text NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_expenses PRIMARY KEY (id),
CONSTRAINT fk_expenses_trucks_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE RESTRICT,
CONSTRAINT fk_expenses_trucks_truck_id1 FOREIGN KEY (truck_expense_truck_id) REFERENCES trucks(id) ON DELETE RESTRICT
);
CREATE INDEX ix_expenses_expense_date ON public.expenses USING btree (expense_date);
CREATE UNIQUE INDEX ix_expenses_number ON public.expenses USING btree (number);
CREATE INDEX ix_expenses_status ON public.expenses USING btree (status);
CREATE INDEX ix_expenses_truck_id ON public.expenses USING btree (truck_id);
CREATE INDEX ix_expenses_truck_id1 ON public.expenses USING btree (truck_expense_truck_id);
CREATE INDEX ix_expenses_type ON public.expenses USING btree (type);


-- public.loads definition

-- Drop table

-- DROP TABLE loads;

CREATE TABLE loads (
id uuid NOT NULL,
"number" int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
"name" text NOT NULL,
"type" text NOT NULL,
status text NOT NULL,
distance float8 NOT NULL,
is_in_proximity bool NOT NULL,
dispatched_at timestamptz NULL,
picked_up_at timestamptz NULL,
delivered_at timestamptz NULL,
cancelled_at timestamptz NULL,
customer_id uuid NOT NULL,
assigned_truck_id uuid NULL,
assigned_dispatcher_id uuid NULL,
"source" text NOT NULL,
requested_pickup_date timestamptz NULL,
requested_delivery_date timestamptz NULL,
notes varchar(2000) NULL,
is_hazmat bool NOT NULL,
hazmat_class text NULL,
un_number varchar(16) NULL,
container_id uuid NULL,
origin_terminal_id uuid NULL,
destination_terminal_id uuid NULL,
external_source_provider text NULL,
external_source_id varchar(100) NULL,
external_broker_reference varchar(100) NULL,
delivery_cost_amount numeric(18, 2) NOT NULL,
delivery_cost_currency varchar(3) NOT NULL,
destination_address_city text NOT NULL,
destination_address_country text NOT NULL,
destination_address_line1 text NOT NULL,
destination_address_line2 text NULL,
destination_address_state text NOT NULL,
destination_address_zip_code text NOT NULL,
destination_location_latitude float8 NOT NULL,
destination_location_longitude float8 NOT NULL,
origin_address_city text NOT NULL,
origin_address_country text NOT NULL,
origin_address_line1 text NOT NULL,
origin_address_line2 text NULL,
origin_address_state text NOT NULL,
origin_address_zip_code text NOT NULL,
origin_location_latitude float8 NOT NULL,
origin_location_longitude float8 NOT NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_loads PRIMARY KEY (id),
CONSTRAINT fk_loads_containers_container_id FOREIGN KEY (container_id) REFERENCES containers(id) ON DELETE SET NULL,
CONSTRAINT fk_loads_customers_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
CONSTRAINT fk_loads_employees_assigned_dispatcher_id FOREIGN KEY (assigned_dispatcher_id) REFERENCES employees(id),
CONSTRAINT fk_loads_terminal_destination_terminal_id FOREIGN KEY (destination_terminal_id) REFERENCES terminals(id) ON DELETE SET NULL,
CONSTRAINT fk_loads_terminal_origin_terminal_id FOREIGN KEY (origin_terminal_id) REFERENCES terminals(id) ON DELETE SET NULL,
CONSTRAINT fk_loads_truck_assigned_truck_id FOREIGN KEY (assigned_truck_id) REFERENCES trucks(id) ON DELETE SET NULL
);
CREATE INDEX ix_loads_assigned_dispatcher_id ON public.loads USING btree (assigned_dispatcher_id);
CREATE INDEX ix_loads_assigned_truck_id ON public.loads USING btree (assigned_truck_id);
CREATE INDEX ix_loads_container_id ON public.loads USING btree (container_id);
CREATE INDEX ix_loads_customer_id ON public.loads USING btree (customer_id);
CREATE INDEX ix_loads_destination_terminal_id ON public.loads USING btree (destination_terminal_id);
CREATE UNIQUE INDEX ix_loads_number ON public.loads USING btree (number);
CREATE INDEX ix_loads_origin_terminal_id ON public.loads USING btree (origin_terminal_id);


-- public.maintenance_schedules definition

-- Drop table

-- DROP TABLE maintenance_schedules;

CREATE TABLE maintenance_schedules (
id uuid NOT NULL,
truck_id uuid NOT NULL,
maintenance_type text NOT NULL,
interval_type text NOT NULL,
mileage_interval int4 NULL,
days_interval int4 NULL,
engine_hours_interval int4 NULL,
last_service_mileage int4 NULL,
last_service_date timestamptz NULL,
last_service_engine_hours int4 NULL,
next_due_mileage int4 NULL,
next_due_date timestamptz NULL,
next_due_engine_hours int4 NULL,
is_active bool NOT NULL,
notes varchar(1000) NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_maintenance_schedules PRIMARY KEY (id),
CONSTRAINT fk_maintenance_schedules_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);
CREATE INDEX ix_maintenance_schedules_is_active ON public.maintenance_schedules USING btree (is_active);
CREATE INDEX ix_maintenance_schedules_next_due_date ON public.maintenance_schedules USING btree (next_due_date);
CREATE INDEX ix_maintenance_schedules_truck_id_maintenance_type ON public.maintenance_schedules USING btree (truck_id, maintenance_type);


-- public.posted_trucks definition

-- Drop table

-- DROP TABLE posted_trucks;

CREATE TABLE posted_trucks (
id uuid NOT NULL,
truck_id uuid NOT NULL,
provider_type text NOT NULL,
external_post_id varchar(100) NULL,
destination_radius int4 NULL,
available_from timestamptz NOT NULL,
available_to timestamptz NULL,
equipment_type varchar(50) NULL,
max_weight int4 NULL,
max_length int4 NULL,
status text NOT NULL,
expires_at timestamptz NULL,
last_refreshed_at timestamptz NULL,
available_at_address_city text NOT NULL,
available_at_address_country text NOT NULL,
available_at_address_line1 text NOT NULL,
available_at_address_line2 text NULL,
available_at_address_state text NOT NULL,
available_at_address_zip_code text NOT NULL,
available_at_location_latitude float8 NOT NULL,
available_at_location_longitude float8 NOT NULL,
destination_preference_city text NULL,
destination_preference_country text NULL,
destination_preference_line1 text NULL,
destination_preference_line2 text NULL,
destination_preference_state text NULL,
destination_preference_zip_code text NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_posted_trucks PRIMARY KEY (id),
CONSTRAINT fk_posted_trucks_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);
CREATE INDEX ix_posted_trucks_external_post_id ON public.posted_trucks USING btree (external_post_id);
CREATE INDEX ix_posted_trucks_status ON public.posted_trucks USING btree (status);
CREATE UNIQUE INDEX ix_posted_trucks_truck_id_provider_type ON public.posted_trucks USING btree (truck_id, provider_type);


-- public.tracking_links definition

-- Drop table

-- DROP TABLE tracking_links;

CREATE TABLE tracking_links (
id uuid NOT NULL,
"token" varchar(128) NOT NULL,
load_id uuid NOT NULL,
expires_at timestamptz NOT NULL,
is_active bool NOT NULL,
created_by_user_id uuid NOT NULL,
access_count int4 NOT NULL,
last_accessed_at timestamptz NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_tracking_links PRIMARY KEY (id),
CONSTRAINT fk_tracking_links_loads_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);
CREATE INDEX ix_tracking_links_expires_at ON public.tracking_links USING btree (expires_at);
CREATE INDEX ix_tracking_links_load_id ON public.tracking_links USING btree (load_id);
CREATE UNIQUE INDEX ix_tracking_links_token ON public.tracking_links USING btree (token);


-- public.trips definition

-- Drop table

-- DROP TABLE trips;

CREATE TABLE trips (
id uuid NOT NULL,
"number" int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
"name" text NOT NULL,
total_distance float8 NOT NULL,
dispatched_at timestamptz NULL,
completed_at timestamptz NULL,
cancelled_at timestamptz NULL,
status text NOT NULL,
truck_id uuid NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_trips PRIMARY KEY (id),
CONSTRAINT fk_trips_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id)
);
CREATE UNIQUE INDEX ix_trips_number ON public.trips USING btree (number);
CREATE INDEX ix_trips_truck_id ON public.trips USING btree (truck_id);


-- public.accident_reports definition

-- Drop table

-- DROP TABLE accident_reports;

CREATE TABLE accident_reports (
id uuid NOT NULL,
driver_id uuid NOT NULL,
truck_id uuid NOT NULL,
trip_id uuid NULL,
status text NOT NULL,
accident_type text NOT NULL,
severity text NOT NULL,
accident_date_time timestamptz NOT NULL,
latitude float8 NOT NULL,
longitude float8 NOT NULL,
address varchar(500) NULL,
description varchar(4000) NULL,
weather_conditions varchar(200) NULL,
road_conditions varchar(200) NULL,
any_injuries bool NOT NULL,
number_of_injuries int4 NULL,
injury_description varchar(2000) NULL,
vehicle_damaged bool NOT NULL,
vehicle_damage_description varchar(2000) NULL,
estimated_damage_cost numeric(18, 2) NULL,
vehicle_drivable bool NOT NULL,
police_report_filed bool NOT NULL,
police_report_number varchar(100) NULL,
police_officer_name varchar(200) NULL,
police_officer_badge varchar(50) NULL,
police_department varchar(200) NULL,
insurance_notified bool NOT NULL,
insurance_notified_at timestamptz NULL,
insurance_claim_number varchar(100) NULL,
driver_statement varchar(4000) NULL,
driver_signature text NULL,
driver_signed_at timestamptz NULL,
reviewed_by_id uuid NULL,
reviewed_at timestamptz NULL,
review_notes varchar(2000) NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_accident_reports PRIMARY KEY (id),
CONSTRAINT fk_accident_reports_employee_driver_id FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_accident_reports_employee_reviewed_by_id FOREIGN KEY (reviewed_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_accident_reports_trip_trip_id FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
CONSTRAINT fk_accident_reports_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE RESTRICT
);
CREATE INDEX ix_accident_reports_driver_id_accident_date_time ON public.accident_reports USING btree (driver_id, accident_date_time);
CREATE INDEX ix_accident_reports_reviewed_by_id ON public.accident_reports USING btree (reviewed_by_id);
CREATE INDEX ix_accident_reports_severity ON public.accident_reports USING btree (severity);
CREATE INDEX ix_accident_reports_status ON public.accident_reports USING btree (status);
CREATE INDEX ix_accident_reports_trip_id ON public.accident_reports USING btree (trip_id);
CREATE INDEX ix_accident_reports_truck_id ON public.accident_reports USING btree (truck_id);


-- public.accident_third_parties definition

-- Drop table

-- DROP TABLE accident_third_parties;

CREATE TABLE accident_third_parties (
id uuid NOT NULL,
accident_report_id uuid NOT NULL,
"name" varchar(200) NOT NULL,
phone_number varchar(50) NULL,
address varchar(500) NULL,
driver_license varchar(50) NULL,
vehicle_make varchar(100) NULL,
vehicle_model varchar(100) NULL,
vehicle_year int4 NULL,
vehicle_license_plate varchar(20) NULL,
vehicle_vin varchar(20) NULL,
vehicle_color varchar(50) NULL,
insurance_company varchar(200) NULL,
insurance_policy_number varchar(100) NULL,
insurance_agent_phone varchar(50) NULL,
CONSTRAINT pk_accident_third_parties PRIMARY KEY (id),
CONSTRAINT fk_accident_third_parties_accident_reports_accident_report_id FOREIGN KEY (accident_report_id) REFERENCES accident_reports(id) ON DELETE CASCADE
);
CREATE INDEX ix_accident_third_parties_accident_report_id ON public.accident_third_parties USING btree (accident_report_id);


-- public.accident_witnesses definition

-- Drop table

-- DROP TABLE accident_witnesses;

CREATE TABLE accident_witnesses (
id uuid NOT NULL,
accident_report_id uuid NOT NULL,
"name" varchar(200) NOT NULL,
phone_number varchar(50) NULL,
email varchar(200) NULL,
address varchar(500) NULL,
"statement" varchar(4000) NULL,
CONSTRAINT pk_accident_witnesses PRIMARY KEY (id),
CONSTRAINT fk_accident_witnesses_accident_reports_accident_report_id FOREIGN KEY (accident_report_id) REFERENCES accident_reports(id) ON DELETE CASCADE
);
CREATE INDEX ix_accident_witnesses_accident_report_id ON public.accident_witnesses USING btree (accident_report_id);


-- public.conversations definition

-- Drop table

-- DROP TABLE conversations;

CREATE TABLE conversations (
id uuid NOT NULL,
"name" varchar(200) NULL,
load_id uuid NULL,
is_tenant_chat bool NOT NULL,
created_at timestamptz NOT NULL,
last_message_at timestamptz NULL,
CONSTRAINT pk_conversations PRIMARY KEY (id),
CONSTRAINT fk_conversations_load_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE SET NULL
);
CREATE INDEX ix_conversations_last_message_at ON public.conversations USING btree (last_message_at);
CREATE INDEX ix_conversations_load_id ON public.conversations USING btree (load_id);


-- public.dvir_reports definition

-- Drop table

-- DROP TABLE dvir_reports;

CREATE TABLE dvir_reports (
id uuid NOT NULL,
truck_id uuid NOT NULL,
driver_id uuid NOT NULL,
"type" text NOT NULL,
status text NOT NULL,
inspection_date timestamptz NOT NULL,
latitude float8 NULL,
longitude float8 NULL,
odometer_reading int4 NULL,
has_defects bool NOT NULL,
driver_signature text NULL,
driver_notes varchar(2000) NULL,
reviewed_by_id uuid NULL,
reviewed_at timestamptz NULL,
mechanic_signature text NULL,
mechanic_notes varchar(2000) NULL,
defects_corrected bool NULL,
trip_id uuid NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_dvir_reports PRIMARY KEY (id),
CONSTRAINT fk_dvir_reports_employee_driver_id FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_dvir_reports_employee_reviewed_by_id FOREIGN KEY (reviewed_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_dvir_reports_trip_trip_id FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
CONSTRAINT fk_dvir_reports_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);
CREATE INDEX ix_dvir_reports_driver_id_inspection_date ON public.dvir_reports USING btree (driver_id, inspection_date);
CREATE INDEX ix_dvir_reports_reviewed_by_id ON public.dvir_reports USING btree (reviewed_by_id);
CREATE INDEX ix_dvir_reports_status ON public.dvir_reports USING btree (status);
CREATE INDEX ix_dvir_reports_trip_id ON public.dvir_reports USING btree (trip_id);
CREATE INDEX ix_dvir_reports_truck_id_inspection_date ON public.dvir_reports USING btree (truck_id, inspection_date);


-- public.invoices definition

-- Drop table

-- DROP TABLE invoices;

CREATE TABLE invoices (
id uuid NOT NULL,
"number" int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
"type" text NOT NULL,
status text NOT NULL,
tax_behavior text DEFAULT 'exclusive'::text NOT NULL,
tax_breakdown_json text NULL,
notes text NULL,
due_date timestamptz NULL,
stripe_invoice_id text NULL,
sent_at timestamptz NULL,
sent_to_email text NULL,
subtotal_amount numeric(18, 2) NOT NULL,
subtotal_currency varchar(3) NOT NULL,
tax_total_amount numeric(18, 2) NOT NULL,
tax_total_currency varchar(3) NOT NULL,
total_amount numeric(18, 2) NOT NULL,
total_currency varchar(3) NOT NULL,
load_id uuid NULL,
customer_id uuid NULL,
employee_id uuid NULL,
period_start timestamptz NULL,
period_end timestamptz NULL,
total_distance_driven float8 NULL,
total_hours_worked numeric(10, 2) NULL,
approved_by_id uuid NULL,
approved_at timestamptz NULL,
approval_notes varchar(1000) NULL,
rejection_reason varchar(1000) NULL,
subscription_id uuid NULL,
billing_period_start timestamptz NULL,
billing_period_end timestamptz NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_invoices PRIMARY KEY (id),
CONSTRAINT fk_invoices_customers_customer_id FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
CONSTRAINT fk_invoices_employees_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
CONSTRAINT fk_invoices_loads_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);
CREATE INDEX ix_invoices_customer_id ON public.invoices USING btree (customer_id);
CREATE INDEX ix_invoices_employee_id ON public.invoices USING btree (employee_id);
CREATE UNIQUE INDEX ix_invoices_load_id ON public.invoices USING btree (load_id);
CREATE UNIQUE INDEX ix_invoices_number ON public.invoices USING btree (number);


-- public.load_board_listings definition

-- Drop table

-- DROP TABLE load_board_listings;

CREATE TABLE load_board_listings (
id uuid NOT NULL,
external_listing_id varchar(100) NOT NULL,
provider_type text NOT NULL,
rate_per_mile numeric(18, 2) NULL,
distance float8 NULL,
weight int4 NULL,
length int4 NULL,
pickup_date_start timestamptz NULL,
pickup_date_end timestamptz NULL,
delivery_date_start timestamptz NULL,
delivery_date_end timestamptz NULL,
equipment_type varchar(50) NULL,
commodity varchar(200) NULL,
broker_name varchar(200) NULL,
broker_phone varchar(30) NULL,
broker_email varchar(200) NULL,
broker_mc_number varchar(20) NULL,
status text NOT NULL,
booked_at timestamptz NULL,
load_id uuid NULL,
notes varchar(2000) NULL,
raw_json text NULL,
expires_at timestamptz NOT NULL,
destination_address_city text NOT NULL,
destination_address_country text NOT NULL,
destination_address_line1 text NOT NULL,
destination_address_line2 text NULL,
destination_address_state text NOT NULL,
destination_address_zip_code text NOT NULL,
destination_location_latitude float8 NOT NULL,
destination_location_longitude float8 NOT NULL,
origin_address_city text NOT NULL,
origin_address_country text NOT NULL,
origin_address_line1 text NOT NULL,
origin_address_line2 text NULL,
origin_address_state text NOT NULL,
origin_address_zip_code text NOT NULL,
origin_location_latitude float8 NOT NULL,
origin_location_longitude float8 NOT NULL,
total_rate_amount numeric(18, 2) NULL,
total_rate_currency varchar(3) NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_load_board_listings PRIMARY KEY (id),
CONSTRAINT fk_load_board_listings_load_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE SET NULL
);
CREATE INDEX ix_load_board_listings_expires_at ON public.load_board_listings USING btree (expires_at);
CREATE UNIQUE INDEX ix_load_board_listings_external_listing_id_provider_type ON public.load_board_listings USING btree (external_listing_id, provider_type);
CREATE INDEX ix_load_board_listings_load_id ON public.load_board_listings USING btree (load_id);
CREATE INDEX ix_load_board_listings_status ON public.load_board_listings USING btree (status);


-- public.load_condition_reports definition

-- Drop table

-- DROP TABLE load_condition_reports;

CREATE TABLE load_condition_reports (
id uuid NOT NULL,
load_id uuid NOT NULL,
"type" text NOT NULL,
vin varchar(17) NULL,
vehicle_year int4 NULL,
vehicle_make varchar(100) NULL,
vehicle_model varchar(100) NULL,
vehicle_body_class varchar(100) NULL,
container_number varchar(20) NULL,
seal_number varchar(50) NULL,
notes varchar(2000) NULL,
inspector_signature varchar(2048) NULL,
latitude float8 NULL,
longitude float8 NULL,
inspected_at timestamptz NOT NULL,
inspected_by_id uuid NOT NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_load_condition_reports PRIMARY KEY (id),
CONSTRAINT fk_load_condition_reports_employees_inspected_by_id FOREIGN KEY (inspected_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_load_condition_reports_load_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);
CREATE INDEX ix_load_condition_reports_container_number ON public.load_condition_reports USING btree (container_number);
CREATE INDEX ix_load_condition_reports_inspected_at ON public.load_condition_reports USING btree (inspected_at);
CREATE INDEX ix_load_condition_reports_inspected_by_id ON public.load_condition_reports USING btree (inspected_by_id);
CREATE INDEX ix_load_condition_reports_load_id ON public.load_condition_reports USING btree (load_id);
CREATE INDEX ix_load_condition_reports_vin ON public.load_condition_reports USING btree (vin);


-- public.load_exceptions definition

-- Drop table

-- DROP TABLE load_exceptions;

CREATE TABLE load_exceptions (
id uuid NOT NULL,
load_id uuid NOT NULL,
"type" text NOT NULL,
reason varchar(1000) NOT NULL,
occurred_at timestamptz NOT NULL,
resolved_at timestamptz NULL,
reported_by_id uuid NOT NULL,
reported_by_name varchar(200) NOT NULL,
resolution varchar(1000) NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_load_exceptions PRIMARY KEY (id),
CONSTRAINT fk_load_exceptions_loads_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);
CREATE INDEX ix_load_exceptions_load_id ON public.load_exceptions USING btree (load_id);
CREATE INDEX ix_load_exceptions_resolved_at ON public.load_exceptions USING btree (resolved_at);


-- public.maintenance_records definition

-- Drop table

-- DROP TABLE maintenance_records;

CREATE TABLE maintenance_records (
id uuid NOT NULL,
truck_id uuid NOT NULL,
maintenance_schedule_id uuid NULL,
maintenance_type text NOT NULL,
service_date timestamptz NOT NULL,
odometer_reading int4 NOT NULL,
engine_hours int4 NULL,
vendor_name varchar(200) NULL,
vendor_address varchar(500) NULL,
invoice_number varchar(100) NULL,
labor_cost numeric(18, 2) NOT NULL,
parts_cost numeric(18, 2) NOT NULL,
total_cost numeric(18, 2) NOT NULL,
description varchar(1000) NULL,
work_performed varchar(2000) NULL,
performed_by_id uuid NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_maintenance_records PRIMARY KEY (id),
CONSTRAINT fk_maintenance_records_employees_performed_by_id FOREIGN KEY (performed_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_maintenance_records_maintenance_schedule_maintenance_schedu FOREIGN KEY (maintenance_schedule_id) REFERENCES maintenance_schedules(id) ON DELETE SET NULL,
CONSTRAINT fk_maintenance_records_truck_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);
CREATE INDEX ix_maintenance_records_maintenance_schedule_id ON public.maintenance_records USING btree (maintenance_schedule_id);
CREATE INDEX ix_maintenance_records_maintenance_type ON public.maintenance_records USING btree (maintenance_type);
CREATE INDEX ix_maintenance_records_performed_by_id ON public.maintenance_records USING btree (performed_by_id);
CREATE INDEX ix_maintenance_records_truck_id_service_date ON public.maintenance_records USING btree (truck_id, service_date);


-- public.messages definition

-- Drop table

-- DROP TABLE messages;

CREATE TABLE messages (
id uuid NOT NULL,
conversation_id uuid NOT NULL,
sender_id uuid NOT NULL,
"content" varchar(2000) NOT NULL,
sent_at timestamptz NOT NULL,
is_deleted bool NOT NULL,
deleted_at timestamptz NULL,
CONSTRAINT pk_messages PRIMARY KEY (id),
CONSTRAINT fk_messages_conversations_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
CONSTRAINT fk_messages_employees_sender_id FOREIGN KEY (sender_id) REFERENCES employees(id) ON DELETE RESTRICT
);
CREATE INDEX ix_messages_conversation_id ON public.messages USING btree (conversation_id);
CREATE INDEX ix_messages_sender_id ON public.messages USING btree (sender_id);
CREATE INDEX ix_messages_sent_at ON public.messages USING btree (sent_at);


-- public.payment_links definition

-- Drop table

-- DROP TABLE payment_links;

CREATE TABLE payment_links (
id uuid NOT NULL,
"token" varchar(128) NOT NULL,
invoice_id uuid NOT NULL,
expires_at timestamptz NOT NULL,
is_active bool NOT NULL,
created_by_user_id uuid NOT NULL,
access_count int4 NOT NULL,
last_accessed_at timestamptz NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_payment_links PRIMARY KEY (id),
CONSTRAINT fk_payment_links_invoices_invoice_id FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);
CREATE INDEX ix_payment_links_expires_at ON public.payment_links USING btree (expires_at);
CREATE INDEX ix_payment_links_invoice_id ON public.payment_links USING btree (invoice_id);
CREATE UNIQUE INDEX ix_payment_links_token ON public.payment_links USING btree (token);


-- public.payments definition

-- Drop table

-- DROP TABLE payments;

CREATE TABLE payments (
id uuid NOT NULL,
status text NOT NULL,
stripe_payment_method_id text NULL,
tenant_id uuid NOT NULL,
description text NULL,
stripe_payment_intent_id text NULL,
reference_number text NULL,
recorded_by_user_id uuid NULL,
recorded_at timestamptz NULL,
invoice_id uuid NULL,
amount_amount numeric(18, 2) NOT NULL,
amount_currency varchar(3) NOT NULL,
billing_address_city text NOT NULL,
billing_address_country text NOT NULL,
billing_address_line1 text NOT NULL,
billing_address_line2 text NULL,
billing_address_state text NOT NULL,
billing_address_zip_code text NOT NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_payments PRIMARY KEY (id),
CONSTRAINT fk_payments_invoices_invoice_id FOREIGN KEY (invoice_id) REFERENCES invoices(id)
);
CREATE INDEX ix_payments_invoice_id ON public.payments USING btree (invoice_id);


-- public.time_entries definition

-- Drop table

-- DROP TABLE time_entries;

CREATE TABLE time_entries (
id uuid NOT NULL,
employee_id uuid NOT NULL,
"date" timestamptz NOT NULL,
start_time interval NOT NULL,
end_time interval NOT NULL,
total_hours numeric(10, 2) NOT NULL,
"type" text NOT NULL,
payroll_invoice_id uuid NULL,
notes varchar(500) NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_time_entries PRIMARY KEY (id),
CONSTRAINT fk_time_entries_employees_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
CONSTRAINT fk_time_entries_invoice_payroll_invoice_id FOREIGN KEY (payroll_invoice_id) REFERENCES invoices(id) ON DELETE SET NULL
);
CREATE INDEX ix_time_entries_employee_id_date ON public.time_entries USING btree (employee_id, date);
CREATE INDEX ix_time_entries_payroll_invoice_id ON public.time_entries USING btree (payroll_invoice_id);


-- public.trip_stops definition

-- Drop table

-- DROP TABLE trip_stops;

CREATE TABLE trip_stops (
id uuid NOT NULL,
"type" text NOT NULL,
trip_id uuid NOT NULL,
"order" int4 NOT NULL,
arrived_at timestamptz NULL,
load_id uuid NOT NULL,
address_city text NOT NULL,
address_country text NOT NULL,
address_line1 text NOT NULL,
address_line2 text NULL,
address_state text NOT NULL,
address_zip_code text NOT NULL,
location_latitude float8 NOT NULL,
location_longitude float8 NOT NULL,
CONSTRAINT pk_trip_stops PRIMARY KEY (id),
CONSTRAINT fk_trip_stops_loads_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE RESTRICT,
CONSTRAINT fk_trip_stops_trips_trip_id FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
);
CREATE INDEX ix_trip_stops_load_id ON public.trip_stops USING btree (load_id);
CREATE INDEX ix_trip_stops_trip_id ON public.trip_stops USING btree (trip_id);


-- public.condition_defects definition

-- Drop table

-- DROP TABLE condition_defects;

CREATE TABLE condition_defects (
id uuid NOT NULL,
load_condition_report_id uuid NOT NULL,
part_category varchar(64) NOT NULL,
description varchar(1000) NOT NULL,
severity varchar(32) NOT NULL,
CONSTRAINT pk_condition_defects PRIMARY KEY (id),
CONSTRAINT fk_condition_defects_load_condition_report_load_condition_repo FOREIGN KEY (load_condition_report_id) REFERENCES load_condition_reports(id) ON DELETE CASCADE
);
CREATE INDEX ix_condition_defects_load_condition_report_id ON public.condition_defects USING btree (load_condition_report_id);
CREATE INDEX ix_condition_defects_part_category ON public.condition_defects USING btree (part_category);


-- public.conversation_participants definition

-- Drop table

-- DROP TABLE conversation_participants;

CREATE TABLE conversation_participants (
id uuid NOT NULL,
conversation_id uuid NOT NULL,
employee_id uuid NOT NULL,
joined_at timestamptz NOT NULL,
last_read_at timestamptz NULL,
is_muted bool NOT NULL,
CONSTRAINT pk_conversation_participants PRIMARY KEY (id),
CONSTRAINT fk_conversation_participants_conversations_conversation_id FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
CONSTRAINT fk_conversation_participants_employee_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ix_conversation_participants_conversation_id_employee_id ON public.conversation_participants USING btree (conversation_id, employee_id);
CREATE INDEX ix_conversation_participants_employee_id ON public.conversation_participants USING btree (employee_id);


-- public.documents definition

-- Drop table

-- DROP TABLE documents;

CREATE TABLE documents (
id uuid NOT NULL,
owner_type text NOT NULL,
file_name varchar(255) NOT NULL,
original_file_name varchar(255) NOT NULL,
content_type varchar(128) NOT NULL,
file_size_bytes int8 NOT NULL,
blob_path varchar(512) NOT NULL,
blob_container varchar(128) NOT NULL,
"type" text NOT NULL,
status text DEFAULT 'active'::text NOT NULL,
description varchar(1000) NULL,
uploaded_by_id uuid NOT NULL,
employee_id uuid NULL,
load_id uuid NULL,
load_condition_report_id uuid NULL,
recipient_name varchar(255) NULL,
recipient_signature varchar(2048) NULL,
capture_latitude float8 NULL,
capture_longitude float8 NULL,
captured_at timestamptz NULL,
trip_stop_id uuid NULL,
notes varchar(2000) NULL,
truck_id uuid NULL,
accident_report_id uuid NULL,
dvir_report_id uuid NULL,
maintenance_record_id uuid NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_documents PRIMARY KEY (id),
CONSTRAINT fk_documents_accident_reports_accident_report_id FOREIGN KEY (accident_report_id) REFERENCES accident_reports(id),
CONSTRAINT fk_documents_dvir_reports_dvir_report_id FOREIGN KEY (dvir_report_id) REFERENCES dvir_reports(id),
CONSTRAINT fk_documents_employee_uploaded_by_id FOREIGN KEY (uploaded_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
CONSTRAINT fk_documents_employees_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
CONSTRAINT fk_documents_load_condition_reports_load_condition_report_id FOREIGN KEY (load_condition_report_id) REFERENCES load_condition_reports(id),
CONSTRAINT fk_documents_loads_load_id FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE,
CONSTRAINT fk_documents_maintenance_records_maintenance_record_id FOREIGN KEY (maintenance_record_id) REFERENCES maintenance_records(id),
CONSTRAINT fk_documents_trip_stops_trip_stop_id FOREIGN KEY (trip_stop_id) REFERENCES trip_stops(id) ON DELETE SET NULL,
CONSTRAINT fk_documents_trucks_truck_id FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE CASCADE
);
CREATE INDEX ix_documents_accident_report_id ON public.documents USING btree (accident_report_id);
CREATE INDEX ix_documents_dvir_report_id ON public.documents USING btree (dvir_report_id);
CREATE INDEX ix_documents_employee_id ON public.documents USING btree (employee_id);
CREATE INDEX ix_documents_load_condition_report_id ON public.documents USING btree (load_condition_report_id);
CREATE INDEX ix_documents_load_id ON public.documents USING btree (load_id);
CREATE INDEX ix_documents_maintenance_record_id ON public.documents USING btree (maintenance_record_id);
CREATE INDEX ix_documents_trip_stop_id ON public.documents USING btree (trip_stop_id);
CREATE INDEX ix_documents_truck_id ON public.documents USING btree (truck_id);
CREATE INDEX ix_documents_uploaded_by_id ON public.documents USING btree (uploaded_by_id);


-- public.driver_licenses definition

-- Drop table

-- DROP TABLE driver_licenses;

CREATE TABLE driver_licenses (
id uuid NOT NULL,
employee_id uuid NOT NULL,
license_number varchar(64) NOT NULL,
license_class text NOT NULL,
endorsements text NOT NULL,
issuing_country varchar(2) NOT NULL,
issuing_region varchar(64) NULL,
issued_date timestamptz NOT NULL,
expires_at timestamptz NOT NULL,
medical_cert_expires_at timestamptz NULL,
status text DEFAULT 'active'::text NOT NULL,
document_id uuid NULL,
last_reminder_sent_at timestamptz NULL,
last_reminder_threshold_days int4 NULL,
"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
"CreatedBy" varchar(50) NULL,
"LastModifiedAt" timestamptz NULL,
"LastModifiedBy" varchar(50) NULL,
CONSTRAINT pk_driver_licenses PRIMARY KEY (id),
CONSTRAINT fk_driver_licenses_document_document_id FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL,
CONSTRAINT fk_driver_licenses_employee_employee_id FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE
);
CREATE INDEX ix_driver_licenses_document_id ON public.driver_licenses USING btree (document_id);
CREATE UNIQUE INDEX ix_driver_licenses_employee_id_license_number_issuing_country ON public.driver_licenses USING btree (employee_id, license_number, issuing_country);
CREATE INDEX ix_driver_licenses_expires_at ON public.driver_licenses USING btree (expires_at);


-- public.dvir_defects definition

-- Drop table

-- DROP TABLE dvir_defects;

CREATE TABLE dvir_defects (
id uuid NOT NULL,
dvir_report_id uuid NOT NULL,
category text NOT NULL,
description varchar(1000) NOT NULL,
severity text NOT NULL,
is_corrected bool NOT NULL,
correction_notes varchar(1000) NULL,
corrected_at timestamptz NULL,
corrected_by_id uuid NULL,
CONSTRAINT pk_dvir_defects PRIMARY KEY (id),
CONSTRAINT fk_dvir_defects_dvir_report_dvir_report_id FOREIGN KEY (dvir_report_id) REFERENCES dvir_reports(id) ON DELETE CASCADE,
CONSTRAINT fk_dvir_defects_employee_corrected_by_id FOREIGN KEY (corrected_by_id) REFERENCES employees(id) ON DELETE RESTRICT
);
CREATE INDEX ix_dvir_defects_category_severity ON public.dvir_defects USING btree (category, severity);
CREATE INDEX ix_dvir_defects_corrected_by_id ON public.dvir_defects USING btree (corrected_by_id);
CREATE INDEX ix_dvir_defects_dvir_report_id ON public.dvir_defects USING btree (dvir_report_id);


-- public.invoice_line_items definition

-- Drop table

-- DROP TABLE invoice_line_items;

CREATE TABLE invoice_line_items (
id uuid NOT NULL,
invoice_id uuid NOT NULL,
description varchar(500) NOT NULL,
"type" text NOT NULL,
quantity int4 NOT NULL,
"order" int4 NOT NULL,
notes varchar(1000) NULL,
tax_rate_percent numeric(5, 2) NOT NULL,
tax_amount numeric(18, 2) NOT NULL,
tax_code varchar(50) NULL,
amount_amount numeric(18, 2) NOT NULL,
amount_currency varchar(3) NOT NULL,
CONSTRAINT pk_invoice_line_items PRIMARY KEY (id),
CONSTRAINT fk_invoice_line_items_invoices_invoice_id FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
);
CREATE INDEX ix_invoice_line_items_invoice_id ON public.invoice_line_items USING btree (invoice_id);


-- public.maintenance_parts definition

-- Drop table

-- DROP TABLE maintenance_parts;

CREATE TABLE maintenance_parts (
id uuid NOT NULL,
maintenance_record_id uuid NOT NULL,
part_name varchar(200) NOT NULL,
part_number varchar(100) NULL,
quantity int4 NOT NULL,
unit_cost numeric(18, 2) NOT NULL,
total_cost numeric(18, 2) NOT NULL,
CONSTRAINT pk_maintenance_parts PRIMARY KEY (id),
CONSTRAINT fk_maintenance_parts_maintenance_record_maintenance_record_id FOREIGN KEY (maintenance_record_id) REFERENCES maintenance_records(id) ON DELETE CASCADE
);
CREATE INDEX ix_maintenance_parts_maintenance_record_id ON public.maintenance_parts USING btree (maintenance_record_id);


-- public.message_read_receipts definition

-- Drop table

-- DROP TABLE message_read_receipts;

CREATE TABLE message_read_receipts (
id uuid NOT NULL,
message_id uuid NOT NULL,
read_by_id uuid NOT NULL,
read_at timestamptz NOT NULL,
CONSTRAINT pk_message_read_receipts PRIMARY KEY (id),
CONSTRAINT fk_message_read_receipts_employees_read_by_id FOREIGN KEY (read_by_id) REFERENCES employees(id) ON DELETE CASCADE,
CONSTRAINT fk_message_read_receipts_messages_message_id FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ix_message_read_receipts_message_id_read_by_id ON public.message_read_receipts USING btree (message_id, read_by_id);
CREATE INDEX ix_message_read_receipts_read_by_id ON public.message_read_receipts USING btree (read_by_id);
