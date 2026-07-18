# Plan: Review Entity vs SQL Schema

Cập nhật lần cuối: 2026-07-18 09:54 (Asia/Ho_Chi_Minh)
Tổng số bảng nghiệp vụ trong schema: 50
Đã kiểm tra: 50 / 50

## Danh sách bảng
- [x] ai_dispatch_sessions — Đã xong (Cảnh báo x1)
- [x] api_keys — Đã xong (Đạt)
- [x] customers — Đã xong (Góp ý x1)
- [x] eld_provider_configurations — Đã xong (Đạt)
- [x] load_board_configurations — Đã xong (Đạt)
- [x] notifications — Đã xong (Đạt)
- [x] telegram_chats — Đã xong (Đạt)
- [x] tenant_roles — Đã xong (Cảnh báo x1)
- [x] terminals — Đã xong (Cảnh báo x1)
- [x] ai_dispatch_decisions — Đã xong (Cảnh báo x1)
- [x] containers — Đã xong (Cảnh báo x1)
- [x] customer_users — Đã xong (Góp ý x1)
- [x] employees — Đã xong (Nghiêm trọng x1)
- [x] hos_logs — Đã xong (Góp ý x1)
- [x] hos_violations — Đã xong (Đạt)
- [x] tenant_role_claims — Đã xong (Cảnh báo x1)
- [x] trucks — Đã xong (Đạt)
- [x] driver_behavior_events — Đã xong (Đạt)
- [x] driver_hos_statuses — Đã xong (Đạt)
- [x] eld_driver_mappings — Đã xong (Góp ý x1)
- [x] eld_vehicle_mappings — Đã xong (Góp ý x1)
- [x] expenses — Đã xong (Đạt)
- [x] loads — Đã xong (Cảnh báo x1)
- [x] maintenance_schedules — Đã xong (Góp ý x1)
- [x] posted_trucks — Đã xong (Góp ý x1)
- [x] tracking_links — Đã xong (Cảnh báo x1)
- [x] trips — Đã xong (Cảnh báo x1)
- [x] accident_reports — Đã xong (Cảnh báo x1)
- [x] accident_third_parties — Đã xong (Đạt)
- [x] accident_witnesses — Đã xong (Đạt)
- [x] conversations — Đã xong (Cảnh báo x1)
- [x] dvir_reports — Đã xong (Cảnh báo x1)
- [x] invoices — Đã xong (Cảnh báo x2)
- [x] load_board_listings — Đã xong (Cảnh báo x1)
- [x] load_condition_reports — Đã xong (Cảnh báo x1)
- [x] load_exceptions — Đã xong (Cảnh báo x1)
- [x] maintenance_records — Đã xong (Cảnh báo x1)
- [x] messages — Đã xong (Cảnh báo x1)
- [x] payment_links — Đã xong (Cảnh báo x1)
- [x] payments — Đã xong (Cảnh báo x1)
- [x] time_entries — Đã xong (Cảnh báo x1)
- [x] trip_stops — Đã xong (Cảnh báo x1)
- [x] condition_defects — Đã xong (Đạt)
- [x] conversation_participants — Đã xong (Cảnh báo x1)
- [x] documents — Đã xong (Cảnh báo x2)
- [x] driver_licenses — Đã xong (Cảnh báo x2)
- [x] dvir_defects — Đã xong (Đạt)
- [x] invoice_line_items — Đã xong (Đạt)
- [x] maintenance_parts — Đã xong (Đạt)
- [x] message_read_receipts — Đã xong (Cảnh báo x1)

## Vấn đề đã phát hiện (tổng hợp, cập nhật dần)
| Bảng | Vấn đề | Mức độ | Trạng thái sửa |
|------|--------|--------|-----------------|
| employees | Entity kế thừa BaseAuditableEntity nhưng bảng SQL không có 4 cột audit | Nghiêm trọng | Đã sửa — cập nhật sql.md và migration 016 |
| 23 bảng dùng BaseAuditableEntity | CreatedBy/LastModifiedBy chưa có cơ chế tự động populate | Cảnh báo | Đã sửa — Spring Data JPA Auditing lấy request principal |
| ai_dispatch_decisions | AiDispatchSession thiếu inverse collection cho decision phụ thuộc session | Cảnh báo | Đã sửa — decisions, PERSIST/MERGE, orphanRemoval |
| tenant_role_claims | TenantRole thiếu inverse collection cho claim phụ thuộc role | Cảnh báo | Đã sửa — claims, PERSIST/MERGE, orphanRemoval |
| conversations/messages/message_read_receipts | Thiếu inverse/orphan management cho aggregate messaging | Cảnh báo | Đã sửa — messages, participants, readReceipts |
| tracking_links/load_exceptions | Load thiếu inverse cho các child có lifecycle phụ thuộc | Cảnh báo | Đã sửa — trackingLinks và exceptions |
| trip_stops | Trip thiếu inverse collection cho stop phụ thuộc trip | Cảnh báo | Đã sửa — stops, PERSIST/MERGE, orphanRemoval |
| invoices | tax_behavior chưa phản ánh DEFAULT 'exclusive' trong columnDefinition | Cảnh báo | Đã sửa — text DEFAULT 'exclusive' |
| documents | status chưa phản ánh DEFAULT 'active' trong columnDefinition | Cảnh báo | Đã sửa — text DEFAULT 'active' |
| driver_licenses | status chưa phản ánh DEFAULT 'active' trong columnDefinition | Cảnh báo | Đã sửa — text DEFAULT 'active' |

---

# Plan: REST API Implementation

Cập nhật lần cuối: 2026-07-18 11:27 (Asia/Ho_Chi_Minh)
Tổng số nhóm endpoint trong `docs/docs/api/overview.md`: 24
Đã hoàn thành: 0 / 24

## Điều kiện tiên quyết
- [ ] JWT Resource Server — Đã thêm integration filter đọc JWT claim `tenant`; vẫn cần cấu hình issuer/audience thực tế của IdentityServer
- [x] Tenant context/isolation — Đã triển khai database-per-tenant bằng routing datasource; không cần tenant key trong bảng nghiệp vụ
- [ ] Realtime adapter — Chưa thiết kế; không dùng STOMP trực tiếp cho client SignalR

## Quyết định kiến trúc chờ xác nhận
- [ ] A. Response format — Chọn format tài liệu `{isSuccess,data,error}` hoặc giữ envelope hiện tại `{success,code,message,data,errors,meta}`
- [ ] B. Realtime protocol — Xác nhận Angular được chuyển từ `@microsoft/signalr` sang STOMP client, hoặc giữ SignalR và cần một gateway/adapter tương thích SignalR
- [ ] C. Thứ tự triển khai — Chọn JWT/tenant infrastructure trước hoặc REST nghiệp vụ trước; khuyến nghị JWT/tenant trước

## Thứ tự nhóm endpoint đề xuất
- [ ] Roles (`/api/roles`) — Chưa làm
- [ ] Customers (`/api/customers`) — Chưa làm
- [ ] Employees (`/api/employees`) — Chưa làm
- [ ] Drivers (`/api/drivers`) — Chưa làm
- [ ] Trucks (`/api/trucks`) — Chưa làm
- [ ] Loads (`/api/loads`) — Chưa làm
- [ ] Trips (`/api/trips`) — Chưa làm
- [ ] Invoices (`/api/invoices`) — Chưa làm
- [ ] Payments (`/api/payments`) — Chưa làm
- [ ] Conversations (`/api/messages/conversations`) — Chưa làm
- [ ] Messages (`/api/messages`) — Chưa làm
- [ ] Unread Count (`/api/messages/unread-count`) — Chưa làm
- [ ] Inspections (`/api/inspections`) — Chưa làm
- [ ] Documents (`/api/documents`) — Chưa làm
- [ ] Proof of Delivery (`/api/documents/pod`) — Chưa làm
- [ ] Bill of Lading (`/api/documents/bol`) — Chưa làm
- [ ] Notifications (`/api/notifications`) — Chưa làm
- [ ] Reports (`/api/reports`) — Chưa làm
- [ ] Stats (`/api/stats`) — Chưa làm
- [ ] Users (`/api/users`) — Chưa làm; chưa có entity tương ứng rõ ràng
- [ ] Tenants (`/api/tenants`) — Chưa làm; chưa có entity `Tenant`
- [ ] Subscriptions (`/api/subscriptions`) — Chưa làm; chưa có entity `Subscription`
- [ ] Inspection Parts (`/api/inspections/parts`) — Chưa làm; chưa có catalog/entity/contract
- [ ] VIN Decoder (`/api/vins/{vin}`) — Chưa làm; chưa xác định provider/response contract

---

# Plan: Database-per-tenant Multi-tenancy

Cập nhật lần cuối: 2026-07-18 11:27 (Asia/Ho_Chi_Minh)

- [x] Tenant Registry DB trung tâm — `tenant_registry` migration + JdbcTemplate service; registry không chứa dữ liệu nghiệp vụ
- [x] Mã hóa tenant DB password — AES/GCM, yêu cầu `TENANT_REGISTRY_ENCRYPTION_KEY` dạng Base64 16/24/32 bytes
- [x] TenantContext — ThreadLocal, set/clear theo request
- [x] Dynamic routing — `TenantRoutingDataSource` extends `AbstractRoutingDataSource`, lookup key từ `TenantContext`
- [x] Tenant connection pools — HikariDataSource per tenant, cache trong memory, giới hạn bằng `TENANT_MAX_POOLS`
- [x] Lazy tenant datasource registration — filter đảm bảo pool được tạo khi request đầu tiên của tenant tới
- [x] JWT claim integration — `TenantJwtClaimFilter` đọc claim `tenant` từ `JwtAuthenticationToken` đã validate và clear trong `finally`
- [x] Migration per tenant — `TenantMigrationService` chạy Flyway tuần tự từng tenant, fail thì dừng và trả danh sách migrated/pending
- [x] Tenant provisioning async — tạo database PostgreSQL, migrate, sau đó insert registry
- [x] Baseline tenant schema — `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql` copy từ `sql.md`
- [x] Test tenant isolation — routing datasource không trả dữ liệu tenant B khi context là tenant A
- [x] Test context cleanup — filter clear TenantContext sau mỗi request cùng thread

## Runtime còn cần cấu hình khi bật multi-tenancy
- [ ] `TENANCY_ENABLED=true`
- [ ] `TENANT_REGISTRY_DB_URL`, `TENANT_REGISTRY_DB_USERNAME`, `TENANT_REGISTRY_DB_PASSWORD`
- [ ] `TENANT_REGISTRY_ENCRYPTION_KEY`
- [ ] IdentityServer issuer/audience cho Resource Server
- [ ] Admin DB credentials cho provisioning nếu cần tạo database tự động
