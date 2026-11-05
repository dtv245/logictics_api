# 04. Lớp và cơ sở dữ liệu

## 4.1 Phạm vi và cách đếm

Các sơ đồ dưới đây phản ánh mã Java hiện tại và lịch sử Flyway đang có trong working tree, không phải kết quả introspection từ một database đang chạy. Có 44 migration tenant từ V1 đến V44; tập lệnh `CREATE TABLE` cho 102 tên khác nhau, V40 thực thi xóa bốn bảng chat nội bộ, nên còn 98 tên trong schema tenant. Trong số này có `__EFMigrationsHistory`; cộng bảng `tenant_registry` của registry cho tổng 99 bảng tính theo migration, hoặc 98 bảng ứng dụng nếu không tính bảng lịch sử migration. V40 chỉ drop `messages`, `conversation_participants`, `conversations`, `message_read_receipts`. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:7-20`, `src/main/resources/db/migration/tenant/V40__drop_internal_chat_tables.sql:1-5`, `src/main/resources/db/migration/tenant/V44__create_ai_gap_tables.sql:43-62`, `src/main/resources/db/migration/registry/V1__create_tenant_registry.sql:1-7`)

Đây là số bảng có thể suy ra từ chuỗi migration trong repository; KHÔNG XÁC MINH ĐƯỢC số bảng đang tồn tại tại một môi trường database bất kỳ vì phiên khảo sát không kết nối/introspect database.

## 4.2 Class Diagram nhóm lõi

Diagram thể hiện các quan hệ được khai báo trong entity JPA. `Load.status` và `Trip.status` là thuộc tính chuỗi; không biểu diễn chúng thành enum/state machine trong sơ đồ. (nguồn: `src/main/java/com/company/logicstic/entity/Load.java:35-88`, `src/main/java/com/company/logicstic/entity/Trip.java:29-82`, `src/main/java/com/company/logicstic/entity/TripStop.java:28-69`, `src/main/java/com/company/logicstic/entity/TripDriverAssignment.java:18-58`)

```plantuml
@startuml
hide methods
skinparam classAttributeIconSize 0
class Customer { +UUID id; +String name; +String status }
class Employee { +UUID id; +String email; +String status; +TenantRole role }
class TenantRole { +UUID id; +String name }
class Truck { +UUID id; +String number; +String status; +Employee mainDriver; +Employee secondaryDriver }
class Load { +UUID id; +Long number; +String name; +String type; +String status; +Long version; +Customer customer; +Truck assignedTruck; +Employee assignedDispatcher }
class Trip { +UUID id; +Long number; +String name; +String status; +Long version; +Truck truck }
class TripStop { +UUID id; +String type; +Integer order; +String status; +OffsetDateTime arrivedAt; +OffsetDateTime departedAt; +Trip trip; +Load load }
class TripDriverAssignment { +UUID id; +Trip trip; +Employee driver; +OffsetDateTime effectiveFrom; +OffsetDateTime effectiveTo }
class LoadEvent { +UUID id; +Load load; +String eventType; +OffsetDateTime occurredAt }
Customer "1" -- "0..*" Load
Truck "0..1" -- "0..*" Load : assigned
Employee "0..1" -- "0..*" Load : dispatcher
Employee "0..1" -- "0..*" Truck : driver roles
Truck "0..1" -- "0..*" Trip
Trip "1" *-- "0..*" TripStop
Load "1" -- "0..*" TripStop
Trip "1" *-- "0..*" TripDriverAssignment
Employee "1" -- "0..*" TripDriverAssignment
TenantRole "0..1" -- "0..*" Employee
Load "1" -- "0..*" LoadEvent
@enduml
```

`TenantRegistryRecord` là DTO biểu diễn bản ghi registry; đây không phải entity nghiệp vụ tenant trong database tenant. Entity mappings và quan hệ trên nằm trong source Java; các FK vật lý được khai báo thêm trong SQL migrations, vì vậy diagram lớp không thay thế ERD. (nguồn: `src/main/java/com/company/logicstic/dto/TenantRegistryRecord.java:1-9`, `src/main/java/com/company/logicstic/entity/Load.java:19-41`, `src/main/java/com/company/logicstic/entity/Trip.java:18-35`)

## 4.3 ERD theo module

Mỗi sơ đồ liệt kê toàn bộ tên bảng hiện có theo migration và một tập con quan hệ FK đã thấy trong DDL; để giữ khả năng đọc, các bảng không có mũi tên trong sơ đồ vẫn hiện trong database nhưng không được suy đoán quan hệ. Bốn bảng hội thoại nội bộ đã bị xóa ở V40 nên không được liệt kê như bảng hiện hành.

### ERD A — Tenant, nhân sự và đội xe

```mermaid
erDiagram
  tenant_roles { uuid id PK }
  tenant_role_claims { uuid id PK; uuid role_id FK }
  employees { uuid id PK; uuid role_id FK }
  customers { uuid id PK }
  customer_users { uuid id PK; uuid customer_id FK }
  trucks { uuid id PK; uuid main_driver_id FK; uuid secondary_driver_id FK }
  terminals { uuid id PK }
  containers { uuid id PK; uuid current_terminal_id FK }
  api_keys { uuid id PK }
  eld_provider_configurations { uuid id PK }
  eld_driver_mappings { uuid id PK; uuid employee_id FK }
  eld_vehicle_mappings { uuid id PK; uuid truck_id FK }
  driver_behavior_events { uuid id PK; uuid employee_id FK; uuid truck_id FK }
  driver_hos_statuses { uuid id PK; uuid employee_id FK }
  driver_licenses { uuid id PK; uuid employee_id FK; uuid document_id FK }
  hos_logs { uuid id PK; uuid employee_id FK }
  hos_violations { uuid id PK; uuid employee_id FK }
  tenant_roles ||--o{ employees : role
  tenant_roles ||--o{ tenant_role_claims : claims
  customers ||--o{ customer_users : users
  employees ||--o{ trucks : assigned_drivers
  terminals ||--o{ containers : current_terminal
  employees ||--o{ eld_driver_mappings : mapping
  trucks ||--o{ eld_vehicle_mappings : mapping
```

Các tên bảng và các FK hiển thị được đọc từ baseline migration. `tenant_registry` nằm ở schema registry riêng, không nằm trong ERD tenant này. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:55-123`, `:190-224`, `:294-347`, `:356-399`, `:408-458`, `:467-567`, `:1459-1484`, `src/main/resources/db/migration/registry/V1__create_tenant_registry.sql:1-7`)

### ERD B — Load, chuyến đi, giao nhận và chứng từ

```mermaid
erDiagram
  loads { uuid id PK; uuid customer_id FK; uuid assigned_truck_id FK; uuid assigned_dispatcher_id FK }
  load_events { uuid id PK; uuid load_id FK }
  load_exceptions { uuid id PK; uuid load_id FK }
  load_pickup_business_date_changes { uuid id PK; uuid load_id FK }
  load_board_configurations { uuid id PK }
  load_board_listings { uuid id PK; uuid load_id FK }
  load_condition_reports { uuid id PK; uuid load_id FK }
  trips { uuid id PK; uuid truck_id FK }
  trip_stops { uuid id PK; uuid trip_id FK; uuid load_id FK }
  trip_driver_assignments { uuid id PK; uuid trip_id FK; uuid driver_id FK }
  tracking_links { uuid id PK; uuid load_id FK }
  documents { uuid id PK; uuid load_id FK; uuid trip_stop_id FK; uuid uploaded_by_id FK }
  containers { uuid id PK }
  terminals { uuid id PK }
  customers { uuid id PK }
  employees { uuid id PK }
  trucks { uuid id PK }
  loads ||--o{ load_events : event
  loads ||--o{ load_exceptions : exception
  loads ||--o{ trip_stops : stop
  trips ||--o{ trip_stops : ordered_stops
  trips ||--o{ trip_driver_assignments : driver_history
  loads ||--o{ documents : evidence
```

Quan hệ stop/load, trip/stop, trip/assignment và load/event được khai báo trong migration; `load_pickup_business_date_changes` được liên kết bổ sung ở migration V22. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:624-694`, `:802-828`, `:1331-1351`, `:1398-1450`, `src/main/resources/db/migration/tenant/V2__create_trip_driver_assignments.sql:2-24`, `src/main/resources/db/migration/tenant/V6__create_load_events.sql:2-22`, `src/main/resources/db/migration/tenant/V22__add_audited_load_pickup_business_date.sql:3`)

### ERD C — Tài chính và payroll

```mermaid
erDiagram
  invoices { uuid id PK; uuid load_id FK; uuid customer_id FK }
  invoice_line_items { uuid id PK; uuid invoice_id FK }
  invoice_billing_commands { uuid id PK }
  invoice_charge_claims { uuid id PK }
  invoice_rebill_credit_evidence { uuid id PK }
  invoice_tax_assessments { uuid id PK }
  accepted_rating_snapshots { uuid id PK; uuid load_id FK }
  contract_load_rating_mileage { uuid id PK }
  customer_rate_rules { uuid id PK }
  customer_rate_rule_versions { uuid id PK }
  customer_rating_contracts { uuid id PK }
  customer_rating_contract_versions { uuid id PK }
  payments { uuid id PK; uuid invoice_id FK }
  payment_links { uuid id PK; uuid invoice_id FK }
  payment_command_events { uuid id PK }
  pay_periods { uuid id PK }
  driver_pay_policies { uuid id PK }
  settlements { uuid id PK; uuid driver_id FK; uuid pay_period_id FK }
  settlement_lines { uuid id PK; uuid settlement_id FK }
  calculation_snapshots { uuid id PK }
  shipment_costs { uuid id PK; uuid pay_period_id FK }
  accessorial_charges { uuid id PK; uuid load_id FK }
  settlement_billing_adjustments { uuid id PK }
  settlement_revenue_commands { uuid id PK }
  payroll_runs { uuid id PK; uuid pay_period_id FK }
  payroll_run_items { uuid id PK; uuid payroll_run_id FK; uuid driver_id FK }
  payroll_run_item_settlements { uuid payroll_run_item_id PK; uuid settlement_id PK }
  payslips { uuid id PK; uuid payroll_run_item_id FK; uuid driver_id FK }
  payroll_payments { uuid id PK; uuid payroll_run_item_id FK }
  payroll_payment_events { uuid id PK }
  payroll_supplements { uuid id PK }
  payroll_jurisdictions { uuid id PK }
  employee_payroll_profiles { uuid id PK }
  tenant_payroll_settings { uuid id PK }
  payroll_policy_versions { uuid id PK }
  invoices ||--o{ invoice_line_items : items
  invoices ||--o{ payments : payments
  invoices ||--o{ payment_links : links
  pay_periods ||--o{ settlements : settles
  settlements ||--o{ settlement_lines : lines
  pay_periods ||--o{ payroll_runs : runs
  payroll_runs ||--o{ payroll_run_items : items
  payroll_run_items ||--o{ payroll_run_item_settlements : allocations
  settlements ||--o{ payroll_run_item_settlements : included
  payroll_run_items ||--o| payslips : artifact
  payroll_run_items ||--o{ payroll_payments : attempts
```

The baseline and later migrations create the billing and payroll structures; exact FK cardinality is only shown where DDL contains the constraint. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:999-1045`, `:1268-1295`, `:1518-1534`, `src/main/resources/db/migration/tenant/V10__create_driver_pay_and_settlement_tables.sql:1-15`, `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql:12-102`, `src/main/resources/db/migration/tenant/V24__create_immutable_accepted_rating_snapshots.sql:1-34`)

### ERD D — Fleet, compliance, chi phí vận hành

```mermaid
erDiagram
  accident_reports { uuid id PK }
  accident_third_parties { uuid id PK; uuid accident_report_id FK }
  accident_witnesses { uuid id PK; uuid accident_report_id FK }
  condition_defects { uuid id PK; uuid load_condition_report_id FK }
  dvir_reports { uuid id PK; uuid truck_id FK; uuid driver_id FK }
  dvir_defects { uuid id PK; uuid dvir_report_id FK }
  expenses { uuid id PK; uuid truck_id FK }
  maintenance_schedules { uuid id PK }
  maintenance_records { uuid id PK; uuid truck_id FK; uuid maintenance_schedule_id FK }
  maintenance_parts { uuid id PK; uuid maintenance_record_id FK }
  vehicle_status_events { uuid id PK; uuid truck_id FK }
  fleet_mileage_attributions { uuid id PK }
  fleet_policy_versions { uuid id PK }
  posted_trucks { uuid id PK; uuid truck_id FK }
  time_entries { uuid id PK; uuid employee_id FK; uuid payroll_invoice_id FK }
  trucks { uuid id PK }
  employees { uuid id PK }
  documents { uuid id PK }
  load_condition_reports { uuid id PK }
  accident_reports ||--o{ accident_third_parties : parties
  accident_reports ||--o{ accident_witnesses : witnesses
  load_condition_reports ||--o{ condition_defects : defects
  dvir_reports ||--o{ dvir_defects : defects
  trucks ||--o{ maintenance_records : service
  maintenance_schedules ||--o{ maintenance_records : planned_service
  maintenance_records ||--o{ maintenance_parts : parts
```

Các bảng và FK ở sơ đồ này có trong baseline hoặc migration riêng cho đội xe và lịch sử bất biến. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:831-936`, `:959-998`, `:1115-1216`, `:1303-1322`, `:1493-1554`, `src/main/resources/db/migration/tenant/V34__capture_immutable_fleet_history_and_mileage_attribution.sql:2`)

### ERD E — AI dispatch, optimization, Lark và hệ thống phụ trợ

```mermaid
erDiagram
  ai_dispatch_sessions { uuid id PK }
  ai_dispatch_decisions { uuid id PK; uuid session_id FK }
  ai_load_gaps { uuid id PK }
  ai_load_gap_history { uuid id PK; uuid gap_id FK }
  ai_runs { uuid id PK }
  optimization_policy_versions { uuid id PK }
  optimization_runs { uuid id PK }
  optimization_assignments { uuid id PK; uuid run_id FK }
  optimization_qualified_inputs { uuid id PK }
  optimization_qualified_forecast_costs { uuid id PK }
  optimization_acceptances { uuid id PK }
  optimization_accept_commands { uuid id PK }
  lark_event_receipts { uuid id PK }
  lark_base_sync_links { varchar tenant_id PK; varchar app_scope PK; varchar table_id PK; varchar domain PK; uuid entity_id PK }
  lark_base_sync_audit { uuid id PK }
  lark_user_mappings { uuid id PK; uuid employee_id FK }
  notifications { uuid id PK }
  telegram_chats { uuid id PK }
  ai_dispatch_sessions ||--o{ ai_dispatch_decisions : decisions
  ai_load_gaps ||--o{ ai_load_gap_history : history
  optimization_runs ||--o{ optimization_assignments : candidates
```

Các migration V30, V39, V41-V44 bổ sung audit/decision/optimization/integration tables; `lark_base_sync_links` có composite key nên sơ đồ đánh dấu hai cột khóa thay vì giả định một UUID `id`. (nguồn: `src/main/resources/db/migration/tenant/V30__create_immutable_optimization_policy_and_run_audit.sql:2-52`, `src/main/resources/db/migration/tenant/V39__create_lark_user_mappings.sql:2`, `src/main/resources/db/migration/tenant/V41__create_lark_event_receipts.sql:2`, `src/main/resources/db/migration/tenant/V42__persist_lark_event_inbox.sql:2`, `src/main/resources/db/migration/tenant/V43__track_lark_base_sync.sql:2-24`, `src/main/resources/db/migration/tenant/V44__create_ai_gap_tables.sql:2-62`)

Các bảng chưa hiện trong sơ đồ quan hệ nhưng vẫn được liệt kê ở đây thuộc schema hiện hành: `__EFMigrationsHistory`, `invoice_charge_claims`, `invoice_rebill_credit_evidence`, `optimization_accept_commands`, `optimization_acceptances`, `optimization_qualified_inputs`, `optimization_qualified_forecast_costs`, `payment_command_events`, `payroll_payment_events`, `payroll_supplements`, `settlement_billing_adjustments`, `settlement_revenue_commands`, `lark_event_receipts`, `lark_base_sync_audit`, `ai_runs`. Tham chiếu DDL: migration baseline và migration chuyên biệt V18, V25-V29, V32-V33, V36-V38, V41-V44. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:7-19`, `src/main/resources/db/migration/tenant/V18__journal_verified_payroll_payment_events.sql:1`, `src/main/resources/db/migration/tenant/V25__create_immutable_invoice_tax_assessments.sql:2`, `src/main/resources/db/migration/tenant/V26__link_rated_primary_invoices_and_command_audit.sql:2`, `src/main/resources/db/migration/tenant/V27__enforce_signed_billing_chains_and_credit_caps.sql:2`, `src/main/resources/db/migration/tenant/V28__audit_settlement_billing_recalculations_and_adjustments.sql:2`, `src/main/resources/db/migration/tenant/V29__preserve_driver_settlement_calculation_history.sql:2`, `src/main/resources/db/migration/tenant/V32__capture_qualified_candidate_source_inputs.sql:2`, `src/main/resources/db/migration/tenant/V33__guard_atomic_optimization_acceptance.sql:2`, `src/main/resources/db/migration/tenant/V36__add_payment_financial_integrity_and_idempotency.sql:2`, `src/main/resources/db/migration/tenant/V37__add_optimistic_locking_to_core_entities.sql:2`, `src/main/resources/db/migration/tenant/V38__protect_payment_command_history.sql:2`, `src/main/resources/db/migration/tenant/V41__create_lark_event_receipts.sql:2`, `src/main/resources/db/migration/tenant/V42__persist_lark_event_inbox.sql:2`, `src/main/resources/db/migration/tenant/V43__track_lark_base_sync.sql:22`, `src/main/resources/db/migration/tenant/V44__create_ai_gap_tables.sql:43-62`)

## 4.4 Từ điển dữ liệu rút gọn cho 20 bảng

Các kiểu dưới đây là kiểu SQL ghi trong migration; `UUID`, `VARCHAR`, `NUMERIC`, `TIMESTAMPTZ`, `BOOLEAN`, `JSONB` giữ nguyên cách gọi kỹ thuật. Danh sách cột nêu các cột định danh/nghiệp vụ và các ràng buộc chính, không thay thế DDL đầy đủ. Nếu không liệt kê index trong hàng, không có nghĩa bảng không có index khác; chỉ ghi index mà migration được nêu minh thị.

| Bảng | Cột và kiểu tiêu biểu | PK / FK | Ràng buộc, index | Ý nghĩa |
|---|---|---|---|---|
| `tenant_registry` | `tenant_id VARCHAR(50)`, `db_url VARCHAR(255)`, `db_username VARCHAR(100)`, `db_password VARCHAR(255)`, `status VARCHAR(20)` | PK `tenant_id` | `status` mặc định `ACTIVE`; không có timestamp trong DDL hiện thấy | Registry thông tin datasource tenant; service mã hóa password trước khi ghi cột `db_password`. (nguồn: `src/main/resources/db/migration/registry/V1__create_tenant_registry.sql:1-7`, `src/main/java/com/company/logicstic/service/TenantRegistryService.java:36-43,65-70`, `src/main/java/com/company/logicstic/service/TenantCredentialCipher.java:33-50`) |
| `customers` | `id UUID`, `name TEXT`, `email TEXT`, `phone TEXT`, `status TEXT`, `tax_id VARCHAR(50)`, địa chỉ dạng `TEXT` | PK `id` | Khóa chính | Đối tác thuê dịch vụ vận tải. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:73-100`) |
| `employees` | `id UUID`, `email/first_name/last_name TEXT`, `salary_amount NUMERIC(18,2)`, `salary_currency VARCHAR(3)`, `role_id UUID` | PK `id`; FK `role_id → tenant_roles.id` | index `role_id` | Nhân viên; có thông tin liên hệ, vai trò và trường payroll. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:320-347`) |
| `trucks` | `id UUID`, `number/type/status TEXT`, `vehicle_capacity INTEGER`, `main_driver_id/secondary_driver_id UUID`, vị trí hiện tại | PK `id`; FK hai tài xế → `employees.id` | `number` unique; index tài xế chính/phụ | Xe đầu kéo/phương tiện và một số thông tin vị trí/đặc tính. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:425-458`) |
| `loads` | `id UUID`, `number BIGINT`, `name/type/status TEXT`, `customer_id UUID`, `assigned_truck_id UUID`, `assigned_dispatcher_id UUID`, dates, `version BIGINT` ở model | PK `id`; FK khách hàng/xe/dispatcher, terminal/container theo DDL | Số Load unique; index các FK; khóa lạc quan `version` ở entity | Đơn/nhu cầu vận chuyển chính. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:624-694`, `src/main/java/com/company/logicstic/entity/Load.java:35-88`) |
| `trips` | `id UUID`, `number BIGINT`, `name/status TEXT`, `truck_id UUID`, khoảng cách và thời điểm thực hiện | PK `id`; FK `truck_id → trucks.id` | `number` unique; index truck | Chuyến vận tải bao gồm các điểm dừng. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:802-828`, `src/main/java/com/company/logicstic/entity/Trip.java:29-82`) |
| `trip_stops` | `id UUID`, `trip_id/load_id UUID`, `type TEXT`, `order INTEGER`, `status`, địa chỉ, tọa độ, các mốc thời gian | PK `id`; FK tới `trips`, `loads` | index `trip_id`, `load_id`, V4 bổ sung index `trip_id,status` | Điểm pickup/delivery và tiến trình thao tác tại stop. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:1331-1351`, `src/main/resources/db/migration/tenant/V4__extend_trip_stops_execution.sql:2-10`) |
| `trip_driver_assignments` | `id UUID`, `trip_id UUID`, `driver_id UUID`, `effective_from/effective_to TIMESTAMPTZ` | PK `id`; FK trip/driver | index trip và driver/khoảng hiệu lực; bảo toàn lịch sử qua V8 | Lịch sử tài xế được phân công cho trip. (nguồn: `src/main/resources/db/migration/tenant/V2__create_trip_driver_assignments.sql:2-24`, `src/main/resources/db/migration/tenant/V8__preserve_trip_driver_assignment_history.sql:2`) |
| `load_events` | `id UUID`, `load_id UUID`, loại event, thời điểm, actor và dữ liệu sự kiện | PK `id`; FK `load_id → loads.id` | `load_id` index theo `occurred_at`; xóa cascade | Audit/event timeline của Load. (nguồn: `src/main/resources/db/migration/tenant/V6__create_load_events.sql:2-22`) |
| `documents` | `id UUID`, tên file, MIME type, dung lượng, `blob_path`, `blob_container`, type/status, các owner FK | PK `id`; FK tùy chủ thể tới load/employee/truck/trip stop/... | index các FK owner và `uploaded_by_id` | Metadata chứng từ và vị trí blob; DDL không chứng minh upload endpoint nào khả dụng. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:1400-1450`) |
| `invoices` | `id UUID`, `number BIGINT`, `status/type TEXT`, `subtotal_amount/tax_total_amount/total_amount NUMERIC`, các currency `VARCHAR(3)`, `load_id/customer_id UUID` | PK `id`; FK load/customer/employee | unique number; V26/V27 bổ sung snapshot và signed billing chain | Hóa đơn công nợ khách hàng. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:1001-1045`, `src/main/resources/db/migration/tenant/V26__link_rated_primary_invoices_and_command_audit.sql:2-44`, `src/main/resources/db/migration/tenant/V27__enforce_signed_billing_chains_and_credit_caps.sql:2`) |
| `payments` | `id UUID`, `invoice_id UUID`, `amount NUMERIC`, currency, status/type và thời điểm | PK `id`; FK `invoice_id → invoices.id` | index invoice; V36/V38 thêm integrity/idempotency/audit | Các khoản thanh toán invoice. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:1268-1295`, `src/main/resources/db/migration/tenant/V36__add_payment_financial_integrity_and_idempotency.sql:2`, `src/main/resources/db/migration/tenant/V38__protect_payment_command_history.sql:2`) |
| `pay_periods` | `id UUID`, mã/khoảng kỳ, thời điểm và trạng thái theo migration | PK `id` | PK; dùng bởi settlement/payroll | Kỳ tính lương/đối soát. (nguồn: `src/main/resources/db/migration/tenant/V10__create_driver_pay_and_settlement_tables.sql:1-3`) |
| `settlements` | `id UUID`, `driver_id/pay_period_id/pay_policy_id UUID`, `settlement_type`, `status`, tiền tệ, nhóm earnings/deductions dạng `NUMERIC(19,4)` | PK `id`; FK tới employee/pay period/policy/calculation snapshot; self-FK parent settlement | unique settlement number; unique có điều kiện cho settlement gốc theo driver/kỳ và parent/sequence | Bảng quyết toán tiền tài xế, lưu snapshot và lịch sử điều chỉnh. (nguồn: `src/main/resources/db/migration/tenant/V10__create_driver_pay_and_settlement_tables.sql:1-15`, `src/main/java/com/company/logicstic/entity/DriverSettlement.java:12-50`) |
| `settlement_lines` | `id UUID`, `settlement_id UUID`, nguồn/loại dòng và amount | PK `id`; FK settlement | index settlement; các khóa và trường chi tiết được mở rộng trong migration kế tiếp | Chi tiết earning/deduction của settlement. (nguồn: `src/main/resources/db/migration/tenant/V10__create_driver_pay_and_settlement_tables.sql:12-15`) |
| `payroll_runs` | `id UUID`, `pay_period_id UUID`, `run_number VARCHAR(60)`, status, thời điểm | PK `id`; FK pay period | run number unique; index `(pay_period_id,status)` | Lần chạy payroll cho kỳ. (nguồn: `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql:12-29`) |
| `payroll_run_items` | `id UUID`, `payroll_run_id/driver_id UUID`, amount/currency, status | PK `id`; FK run/driver | unique `(payroll_run_id,driver_id,currency)`; index driver/run | Tổng hợp khoản trả của tài xế trong một run. (nguồn: `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql:31-54`) |
| `payslips` | `id UUID`, `payroll_run_item_id/driver_id/pdf_document_id UUID`, issued time, checksum/hash fields | PK `id`; FK item/driver/document | unique item; index driver/issued time | Artifact phiếu lương và liên kết document PDF nếu có. (nguồn: `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql:65-77`) |
| `optimization_runs` | `id UUID`, mã run, cấu hình/policy snapshot, tenant/time | PK `id` | PK; các policy/run audit liên quan được tạo cùng migration | Bản ghi chạy optimization. (nguồn: `src/main/resources/db/migration/tenant/V30__create_immutable_optimization_policy_and_run_audit.sql:2-29`) |
| `ai_load_gaps` | `id UUID`, tenant/load, `rule_code`, status, severity/description, timestamps | PK `id`; load tham chiếu ở dữ liệu; không thấy FK load trong V44 DDL | unique `(tenant_id,load_id,rule_code)`; index load/status và tenant | Gap/điểm cần xử lý gắn với Load cho workflow AI Gap. (nguồn: `src/main/resources/db/migration/tenant/V44__create_ai_gap_tables.sql:2-26`) |
| `lark_base_sync_links` | `tenant_id`, app/table scope, domain/entity id, record id, trạng thái | PK ghép `(tenant_id,app_scope,table_id,domain,entity_id)` | unique tenant/scope/table/record; index trạng thái | Mapping giữa entity nội bộ với record Lark Base, kèm trạng thái đồng bộ. (nguồn: `src/main/resources/db/migration/tenant/V43__track_lark_base_sync.sql:2-24`) |

## 4.5 Cô lập tenant

Khi `app.tenancy.enabled=true`, filter đọc tenant claim từ JWT, ràng buộc tenant vào `TenantContext`, kiểm tra mismatch, đăng ký datasource tương ứng, rồi xóa context khi request kết thúc. `TenantRoutingDataSource` chọn datasource theo context. Cấu hình mặc định trong application YAML là `false`, nên repo chứng minh có implementation tenant-aware nhưng KHÔNG chứng minh multi-tenancy đã bật ở production. (nguồn: `src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java:21-25`, `:38-74`, `src/main/java/com/company/logicstic/config/TenantContext.java:7-33`, `src/main/java/com/company/logicstic/config/TenantRoutingDataSource.java:12-34`, `src/main/resources/application.yml:64-82`)

```java
String tenantId = resolveTenantId();
if (StringUtils.hasText(tenantId)) {
    TenantContext.setTenantId(tenantId);
    tenantDataSourceService.ensureTenantDataSource(tenantId);
}
filterChain.doFilter(request, response);
// finally
TenantContext.clear();
```

Đoạn trên rút gọn `TenantJwtClaimFilter`; mã đầy đủ còn xử lý thiếu claim, tenant mismatch, tenant inactive/unavailable và trả lỗi HTTP. (nguồn: `src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java:38-66`)

## 4.6 Quy ước CSDL quan sát được

- Các entity cốt lõi khai báo `UUID` làm primary key; số nghiệp vụ như `number` là trường riêng có unique/index. (nguồn: `src/main/java/com/company/logicstic/entity/Load.java:37-48`, `src/main/java/com/company/logicstic/entity/Trip.java:31-42`, `src/main/java/com/company/logicstic/entity/Invoice.java:32-40`)
- Tiền được lưu với `NUMERIC`/`BigDecimal` và thường tách amount với mã currency ba ký tự; scale thay đổi theo mục đích như amount `19,4` hoặc salary `18,2`. (nguồn: `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql:31-44`, `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:320-347`, `src/main/java/com/company/logicstic/entity/DriverSettlement.java:24-40`)
- Dữ liệu địa chỉ được lưu thành nhiều cột thành phần (`city`, `country`, `line1`, `line2`, `state`, `zip_code`); toạ độ lưu riêng cho Load/TripStop/Truck khi có. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:624-694`, `:1331-1351`, `src/main/java/com/company/logicstic/entity/Truck.java:93-115`)
- Thời gian nghiệp vụ chủ yếu là `TIMESTAMPTZ` trong SQL và `OffsetDateTime` trong entity. Không có một quy ước soft delete thống nhất được xác minh cho toàn bộ entity; KHÔNG TÌM THẤY TRONG CODE bằng chứng rằng toàn bộ bảng đều dùng soft delete. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:73-100`, `src/main/java/com/company/logicstic/entity/Load.java:65-75`)
- JPA đặt `ddl-auto: validate`; Flyway quản lý migration schema theo cấu hình hiện tại. (nguồn: `src/main/resources/application.yml:12-27`)
