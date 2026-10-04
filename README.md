# LogisticsX TMS — Backend API

> Hệ thống quản lý vận tải (Transportation Management System) xây dựng trên Spring Boot 4, hỗ trợ multi-tenancy, tích hợp Lark/Feishu và cung cấp đầy đủ các engine tính toán tài chính vận tải.

## Công nghệ

| Layer | Stack |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.0, Spring Web MVC |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL, Flyway |
| Security | Spring Security OAuth2 Resource Server, JWT (JJWT 0.12.6) |
| API Docs | SpringDoc OpenAPI 3.1.1 (Swagger UI) |
| Mapping | MapStruct 1.6.3, Lombok |
| PDF | Apache PDFBox 3.0.8 |
| Integration | Lark/Feishu (Base, Auth, OAuth) |
| Testing | JUnit 5, REST Assured 6.0.1, H2, Spring Boot Test |
| Build | Maven Wrapper |

## Cấu trúc dự án

```text
logictics_api/
├── pom.xml
├── mvnw / mvnw.cmd
├── .env.example
├── README.md
├── sql.md                          # Baseline DDL schema
├── plan.md                         # Roadmap triển khai 9 Phase
├── plan-convention-v3-implementation-ready.md
├── docs/
│   ├── entity-relationships.md
│   ├── adr/                        # Architecture Decision Records
│   ├── cost-ledger-contracts.md
│   ├── accessorial-contracts.md
│   ├── profitability-contracts.md
│   └── reporting-contracts.md
├── scripts/
│   └── generate_entities.py
└── src/
    ├── main/
    │   ├── java/com/company/logicstic/
    │   │   ├── LogisticApplication.java
    │   │   ├── common/              # Shared enums & utilities
    │   │   │   └── enums/
    │   │   ├── config/              # Spring, Security, Multi-tenancy, OpenAPI
    │   │   ├── controller/          # REST endpoints
    │   │   ├── dto/                 # Request/Response models (17 sub-packages)
    │   │   ├── entity/              # 50+ JPA entities
    │   │   ├── exception/           # Domain exceptions & global handler
    │   │   ├── integration/lark/    # Lark/Feishu integration (auth, base, config)
    │   │   ├── mapper/              # Entity ↔ DTO mapping
    │   │   ├── repository/          # JPA repositories
    │   │   └── service/             # Business logic (xem chi tiết bên dưới)
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/tenant/ # Flyway migrations
    └── test/
        └── java/                    # 47 test classes
```

## Kiến trúc Multi-Tenancy

Hệ thống triển khai **database-per-tenant** với routing động:

```text
JWT Request → TenantJwtClaimFilter → TenantContext
                                         ↓
                              TenantRoutingDataSource
                                    ↓          ↓
                              [Tenant A DB] [Tenant B DB]
```

| Component | Vai trò |
|---|---|
| `TenancyProperties` | Cấu hình tenant từ YAML/env |
| `TenantJwtClaimFilter` | Trích tenant ID từ JWT claim |
| `TenantContext` | ThreadLocal lưu tenant hiện tại |
| `TenantRoutingDataSource` | Chọn DataSource theo tenant |
| `TenantDataSourceConfiguration` | Khởi tạo pool cho từng tenant |
| `TenantMigrationService` | Chạy Flyway migration cho từng tenant |
| `TenantProvisioningService` | Tự động tạo schema/DB cho tenant mới |
| `TenantCredentialCipher` | Mã hóa thông tin kết nối tenant |

## Vai trò các package

| Package | Trách nhiệm |
|---|---|
| `common.enums` | Enum dùng chung: trạng thái, loại chi phí, phương thức thanh toán |
| `config` | Security, JPA Auditing, CORS, OpenAPI, Multi-tenancy beans |
| `controller` | REST API endpoints, request validation |
| `dto` | Request/Response models — 17 domain packages (accessorial, cost, profitability, payroll…) |
| `entity` | 50+ JPA entities ánh xạ PostgreSQL |
| `exception` | Domain exceptions (`CurrencyMismatchException`, `FinancialAggregateLockedException`…) |
| `integration.lark` | Tích hợp Lark/Feishu: OAuth, Base (Bitable) data sync |
| `mapper` | MapStruct converters entity ↔ DTO |
| `repository` | Spring Data JPA repositories |
| `service` | Business logic, calculation engines, workflows |

### Service Layer chi tiết

```text
service/
├── Core CRUD
│   ├── CustomerService          LoadService           TripService
│   ├── EmployeeService          TruckService          InspectionService
│   ├── InvoiceService           PaymentService        DocumentService
│   ├── RoleService              MessageService        NotificationService
│   └── ConversationService
│
├── Trip Execution
│   ├── TripExecutionService     # Workflow điểm dừng (arrive → depart)
│   └── LoadTimelineService      # Event audit timeline
│
├── calculation/                 # Phase 1–2 Metrics & Calculations
│   ├── RevenueCalculator                InvoiceReconciliationService
│   ├── CustomerBalanceCalculator        OnTimeCalculator
│   ├── TransitTimeCalculator            DelayCalculator
│   ├── ExceptionMetricsService          ExpenseReportService
│   ├── MaintenanceReportService         OperatingCostCalculator
│   ├── OperationsMetricsService         TripMileageRatioCalculator
│   ├── ShipmentCostEngine               CalculationSnapshotService
│   └── CostAllocator (→ cost/)
│
├── cost/                        # Phase 3 — Cost Ledger
│   ├── CostAllocator            # Phân bổ chi phí theo dặm
│   ├── ExpenseApprovalService   # Duyệt chi phí + RBAC
│   └── ShipmentCostEngine       # Sổ cái chi phí canonical
│
├── accessorial/                 # Phase 3 — Phụ phí
│   ├── AccessorialService       # CRUD + approval workflow
│   └── DetentionCalculator      # Tính phí lưu bãi (block/hourly)
│
├── profitability/               # Phase 3 — Lợi nhuận
│   ├── ProfitabilityService     # API reports: by-load, by-lane, by-truck
│   ├── ProfitabilityCalculator  # Revenue - Cost, unit economics
│   ├── CostClassificationPolicy # Variable vs Fixed interface
│   ├── DefaultCostClassificationPolicyV1
│   └── CostBehavior             # Enum: VARIABLE, FIXED, UNCLASSIFIED
│
└── payroll/                     # Phase 4–5 — Settlement & Payroll
    ├── DriverPayEngine                  DriverPayPolicyService
    ├── DriverPayPolicyResolver          MileagePayCalculator
    ├── WorkPayCalculator                PercentagePayCalculator
    ├── AccessorialDriverPayCalculator   PayrollGrossCalculator
    ├── PayrollCalculationService        PayrollReconciliationService
    ├── SettlementReconciliationService  PayrollWorkflow(Service)
    ├── PayPeriodService                 PayslipService
    ├── PayslipPdfRenderer
    ├── domain/                  # Domain models
    ├── payment/                 # Payment orchestration
    ├── policy/                  # Pay policy resolution
    └── tax/                     # Tax calculation
```

## Luồng xử lý

```text
HTTP Request
     ↓
Controller (validation, auth check)
     ↓
Service (business logic, state machine, calculation)
     ↓
Repository → PostgreSQL
     ↓
DTO/Mapper → HTTP Response
```

Với financial workflows:

```text
Business Command → Service validates state transition
     ↓
Persist aggregate + Append audit event
     ↓
Create CalculationSnapshot (nếu là persisted business decision)
     ↓
Response DTO (with MetricAvailability nếu là report)
```

## Entity và Database

- **50+ JPA entities** ánh xạ từ baseline schema (`sql.md`)
- UUID primary key dùng Hibernate `@UuidGenerator`
- Quan hệ FK dùng `FetchType.LAZY`
- Hibernate chỉ validate schema: `ddl-auto: validate`
- Aggregate tài chính có `@Version` (optimistic locking)
- Money/Rate dùng `BigDecimal` — **cấm** `double`/`float`
- Chi tiết quan hệ: [`docs/entity-relationships.md`](docs/entity-relationships.md)

Sinh lại entity sau khi thay đổi `sql.md`:

```bash
python3 scripts/generate_entities.py
./mvnw test
```

## Flyway Migrations

Migrations nằm tại `src/main/resources/db/migration/tenant/` và được chạy per-tenant bởi `TenantMigrationService`.

| Version | Nội dung |
|:---:|---|
| V1 | Baseline business schema (50+ bảng) |
| V2 | `trip_driver_assignments` — lịch sử phân công tài xế |
| V3 | Trip mileage breakdown (planned/actual/loaded/empty) |
| V4 | Trip stops execution timestamps & status |
| V5 | Expenses attribution FKs (load, trip, employee, maintenance) |
| V6 | `load_events` — event timeline audit |
| V7 | `calculation_snapshots` — bản chụp tính toán bất biến |
| V8 | Forward-fix assignment history |
| V9 | `shipment_costs` + `accessorial_charges` |
| V10 | `driver_pay_policies` + `settlements` + `settlement_lines` |
| V11 | `payroll_runs` + `payroll_run_items` + `payslips` + `payroll_payments` |
| V12 | Forward-fix audit column alignment |

> **Lưu ý:** Flyway mặc định `enabled: false` trong `application.yml`. Bật khi cần chạy migrations.

## Security

- **OAuth2 Resource Server** xác thực JWT
- **Lark/Feishu OAuth** cho đăng nhập và đồng bộ dữ liệu
- RBAC theo capability: `COST_APPROVE`, `SETTLEMENT_LOCK`, `PAYROLL_APPROVE`…
- Tenant isolation qua JWT claim
- Financial endpoints có audit `actor_id`

## Tích hợp Lark/Feishu

| Module | Chức năng |
|---|---|
| `lark.auth` | OAuth2 login flow, JWT token exchange |
| `lark.base` | Đồng bộ dữ liệu từ Lark Base (Bitable) — loads, drivers |
| `lark.config` | Cấu hình app credentials, redirect URI |

## Cấu hình

### Biến môi trường

| Biến | Mô tả | Ví dụ |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile Spring Boot | `nodb` (không cần DB) |
| `SERVER_PORT` | Port HTTP | `8080` |
| `DB_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5433/us_logisticsx` |
| `DB_USERNAME` | DB user | `postgres` |
| `DB_PASSWORD` | DB password | `change-me` |
| `LARK_APP_ID` | Lark App ID | `cli_xxxxxxxxxxxx` |
| `LARK_APP_SECRET` | Lark App Secret | `xxxxxxxxxxxxxxxxxxxxxxxx` |
| `LARK_REDIRECT_URI` | OAuth callback | `http://localhost:5173/auth/callback` |
| `LARK_BASE_URL` | Lark API endpoint | `https://open.larksuite.com` |
| `LARK_JWT_SECRET` | JWT signing key | (≥256 bits) |
| `LARK_BASE_ENABLED` | Bật đồng bộ Bitable | `true` |
| `LARK_BASE_APP_TOKEN` | Bitable app token | `bascnxxxxxxxxxxxx` |
| `LARK_BASE_*_TABLE_ID` | Table IDs cho loads, drivers | `tblxxxxxxxxxxxx` |

### Setup local

```bash
cp .env.example .env
# Sửa giá trị trong .env theo môi trường local
```

`.env` đã nằm trong `.gitignore` — **không commit mật khẩu hoặc secret**.

## Chạy ứng dụng

### Chế độ không cần DB (kiểm tra Web/Tomcat):

```bash
./mvnw spring-boot:run
# .env mặc định SPRING_PROFILES_ACTIVE=nodb
```

### Chế độ đầy đủ với PostgreSQL:

```bash
# Sửa .env: bỏ SPRING_PROFILES_ACTIVE=nodb, cung cấp DB_URL/DB_USERNAME/DB_PASSWORD
./mvnw spring-boot:run
```

### Windows:

```bat
set SPRING_PROFILES_ACTIVE=nodb
set SERVER_PORT=8080
mvnw.cmd spring-boot:run
```

Ứng dụng chạy tại: `http://localhost:8080`

API Docs (Swagger UI): `http://localhost:8080/swagger-ui.html`

## Build và Test

```bash
# Chạy tests (47 test classes)
./mvnw test

# Build JAR
./mvnw clean package

# Chạy JAR
java -jar target/logicstic-1.0.0.jar
```

### Test Coverage

| Nhóm | Số lượng | Mô tả |
|---|:---:|---|
| Core / Integration | 7 | App startup, API response, security, tenant routing, entity mapping, cost ledger Postgres |
| Lark Integration | 4 | Auth controller, auth service, base service, token provider |
| Domain / Calculation | 36 | Execution timeline, payroll, settlement, profitability, mileage, taxes, detention |
| **Tổng** | **47** | |

## Tài liệu tham khảo

| File | Nội dung |
|---|---|
| [`docs/entity-relationships.md`](docs/entity-relationships.md) | Quan hệ JPA entities, LAZY fetch, schema mapping |
| [`docs/adr/`](docs/adr/) | Architecture Decision Records (tenant, migration) |
| [`docs/cost-ledger-contracts.md`](docs/cost-ledger-contracts.md) | API contracts cho shipment cost ledger |
| [`docs/accessorial-contracts.md`](docs/accessorial-contracts.md) | API contracts cho phụ phí/detention |
| [`docs/profitability-contracts.md`](docs/profitability-contracts.md) | API contracts cho báo cáo lợi nhuận |
| [`docs/reporting-contracts.md`](docs/reporting-contracts.md) | API contracts cho reporting Phase 1 |
| [`plan-convention-v3-implementation-ready.md`](plan-convention-v3-implementation-ready.md) | Spec đầy đủ V3 — conventions, migrations, formulas, test matrix |

## License

Private — Internal use only.
