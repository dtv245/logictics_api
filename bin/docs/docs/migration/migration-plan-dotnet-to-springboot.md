# .NET → Spring Boot Migration Plan

## 1. Project Overview

**Source:** LogisticsX (.NET 10, DDD + CQRS, Clean Architecture)
**Target:** Java 21 + Spring Boot 3.x
**Database:** PostgreSQL 18 (giữ nguyên)
**Frontend:** Angular 21 (giữ nguyên, chỉ đổi endpoint)
**Real-time:** SignalR → WebSocket/SSE

## 2. Technology Mapping

| .NET                           | Spring Boot / Java                            | Ghi chú                                                         |
| ------------------------------ | --------------------------------------------- | --------------------------------------------------------------- |
| .NET 10 + ASP.NET Core         | Java 21 + Spring Boot 3.x                     | JDK 21 LTS                                                      |
| Entity Framework Core          | Spring Data JPA / Hibernate 6.x               | Map entity → @Entity, repository → JpaRepository                |
| MediatR (CQRS)                 | Axon Framework hoặc tự implement CQRS pattern | Hoặc dùng command pattern đơn giản với @Transactional           |
| FluentValidation               | Jakarta Bean Validation + custom validators   | @Valid, @NotBlank, custom ConstraintValidator                   |
| Duende IdentityServer (OAuth2) | Spring Security + OAuth2 Resource Server      | JWT validation, public key rotation                             |
| Serilog                        | Logback + SLF4J                               | Có thể dùng Logstash encoder                                    |
| SignalR                        | WebSocket (Spring WebSocket) + STOMP          | Hoặc SSE cho các use-case đơn giản hơn                          |
| Hangfire                       | Spring @Scheduled + Quartz Scheduler          | Job scheduling                                                  |
| Mapbox (geocoding)             | Mapbox SDK cho Java                           | Giống nhau                                                      |
| Stripe SDK (.NET)              | Stripe SDK for Java                           | Tương thích                                                     |
| Polly (resilience)             | Spring Cloud Circuit Breaker + Retry          | Resilience4j                                                    |
| QuestPDF                       | iText / Apache PDFBox                         | PDF generation                                                  |
| JsonStringEnumConverter        | Jackson @JsonEnum + @JsonProperty             | Giữ nguyên snake_case                                           |
| Policy-based Authorization     | Spring Security Method Security               | @PreAuthorize                                                   |

## 3. Layer Mapping

### 3.1. Source: .NET Presentation Layer

| .NET File                          | Spring Boot Target                         | Status |
| ---------------------------------- | ------------------------------------------ | ------ |
| `Logistics.API/Controllers/*.cs`   | `controller/` package                      | ☐      |
| `Logistics.API/Program.cs`         | `LogisticsApplication.java`               | ☐      |
| `Logistics.API/Setup.cs`           | `@Configuration`, `@Bean`                 | ☐      |
| `Logistics.API/Middlewares/*.cs`   | `@Component`, `Filter`, `Interceptor`     | ☐      |
| `Logistics.API/Authorization/*.cs` | `SecurityConfig.java`, `@PreAuthorize`    | ☐      |
| `Logistics.API/Converters/*.cs`    | Jackson `@JsonSerialize`, `@JsonDeserialize` | ☐    |
| `Logistics.IdentityServer/*`       | Spring Authorization Server               | ☐      |
| `Logistics.McpServer/*`            | Spring Boot MCP server module             | ☐      |
| `Logistics.TelegramBot/*`          | TelegramBots library (Java)               | ☐      |
| `Logistics.DbMigrator/*`           | Flyway / Liquibase                        | ☐      |

### 3.2. Source: .NET Core Layer

| .NET File                         | Spring Boot Target                         | Status |
| --------------------------------- | ------------------------------------------ | ------ |
| `Logistics.Domain/Entities/**/*`  | `entity/`                                     | ☐    |
| `Logistics.Domain.Primitives/**`  | `entity/` (embedded)                          | ☐    |
| `Logistics.Application/Modules/*` | `service/`, `command/`, `query/` packages | ☐      |
| `Logistics.Application.Abstractions/*` | `repository/`, `service/` interfaces    | ☐    |
| `Logistics.Mappings/*`            | MapStruct / manual mapper                     | ☐    |
| `Shared/Logistics.Shared.Models/*` | `dto/`                                           | ☐    |

### 3.3. Source: .NET Infrastructure Layer

| .NET File                               | Spring Boot Target               | Status |
| --------------------------------------- | -------------------------------- | ------ |
| `Logistics.Infrastructure.Persistence`  | `repository/`, `config/`         | ☐      |
| `Logistics.Infrastructure.Communications` | `websocket/`, `email/`, `push/` | ☐    |
| `Logistics.Infrastructure.AI`           | `ai/`                            | ☐      |
| `Logistics.Infrastructure.Integrations.Eld` | `integration/eld/`          | ☐      |
| `Logistics.Infrastructure.Integrations.LoadBoard` | `integration/loadboard/` | ☐  |
| `Logistics.Infrastructure.Payments`     | `payment/`                       | ☐      |
| `Logistics.Infrastructure.Documents`    | `document/`                      | ☐      |
| `Logistics.Infrastructure.Routing`      | `routing/`                       | ☐      |
| `Logistics.Infrastructure.Storage`      | `storage/`                       | ☐      |

## 4. Multi-Tenancy Mapping

**.NET:**
- `TenantService` resolves tenant per-request via: MCP API key → X-Tenant header → JWT claim
- Master DB + one tenant DB per tenant
- `IMasterUnitOfWork` / `ITenantUnitOfWork`

**Spring Boot:**
- `TenantContext` filter resolving tenant similarly
- `AbstractRoutingDataSource` + `DataSourceBasedMultiTenantConnectionProvider`
- Flyway multi-schema migration

## 5. Background Jobs Mapping

| .NET Hangfire Job                    | Spring Boot Target                      |
| ------------------------------------ | --------------------------------------- |
| `PayrollGenerationJob`               | `@Scheduled` + Quartz                   |
| `EldSyncJob`                         | `@Scheduled`                            |
| `LoadBoardSyncJob`                   | `@Scheduled`                            |
| `MaintenanceReminderJob`             | `@Scheduled`                            |
| `LicenseExpiryReminderJob`           | `@Scheduled`                            |
| `InvitationExpiryJob`                | `@Scheduled`                            |
| `DataExportProcessingJob`            | Quartz job                              |
| `DataDeletionJob`                    | Quartz job                              |
| `DataRetentionJob`                   | Quartz job                              |
| `DataExportExpiryJob`                | Quartz job                              |

## 6. Migration Order (Module-by-Module)

### Phase 1: Foundation (ước tính: 2 tuần)
- [ ] Spring Boot project skeleton + build setup (Gradle/Maven)
- [ ] Multi-tenancy infrastructure
- [ ] Spring Security + JWT authentication
- [ ] Flyway database migrations (giữ nguyên schema cũ)
- [ ] Shared DTOs + core enums
- [ ] Error response format (giống .NET: `ErrorResponse`)

### Phase 2: Identity & Access (ước tính: 2 tuần)
- [ ] Module IdentityAccess: Tenants, Users, Roles, Permissions
- [ ] Customers, Employees, Invitations
- [ ] API Keys, Features, Subscriptions
- [ ] Test: so sánh authentication flow, CRUD users/tenants

### Phase 3: Core Operations (ước tính: 3 tuần)
- [ ] Module Operations: Loads, Trips, Trucks
- [ ] Containers, Terminals, Tracking
- [ ] Time Entries, Maintenance
- [ ] Test: so sánh full CRUD + workflow mỗi entity

### Phase 4: Financials (ước tính: 2 tuần)
- [ ] Module Financial: Invoices, Tax, Payments
- [ ] Stripe Connect, Payroll, Expenses
- [ ] Subscription billing
- [ ] Test: so sánh tính toán invoice, tax, payment flow

### Phase 5: Compliance & Safety (ước tính: 2 tuần)
- [ ] Module Compliance: ELD/HOS, DVIR, Inspections
- [ ] Accidents, Safety events, Privacy/GDPR
- [ ] Test: so sánh rule-set selector, HOS limits calculation

### Phase 6: Integrations (ước tính: 3 tuần)
- [ ] Module Integrations: AI Dispatch, Load Board
- [ ] Messaging, Documents, Webhooks
- [ ] SignalR → WebSocket migration
- [ ] Test: so sánh AI dispatch flow, webhook processing

### Phase 7: Platform & Reporting (ước tính: 1 tuần)
- [ ] Module Platform: Stats, Reports, Blog
- [ ] Contacts, Demo Requests, Notifications
- [ ] Test: so sánh report calculations

### Phase 8: Polish & Go-live (ước tính: 1 tuần)
- [ ] Performance testing
- [ ] Security audit
- [ ] API compatibility testing with frontend
- [ ] Deployment pipeline (Docker, CI/CD)

## 7. API Contract Compatibility (CRITICAL)

**Phải giữ nguyên:**
- Endpoint path: `GET /loads/{id}` → giữ nguyên
- HTTP method: POST/PUT/DELETE/GET → giữ nguyên
- Request/Response body structure → giữ nguyên
- Status codes → giữ nguyên
- Error response format: `{ "error": "message" }` → giữ nguyên
- JSON casing: `snake_case` → giữ nguyên
- Date/time format: ISO 8601 UTC → giữ nguyên
- Pagination format: `PagedResponse<T>` → giữ nguyên

## 8. Database Compatibility (CRITICAL)

**Phải giữ nguyên:**
- Tên bảng, cột, khóa chính, khóa ngoại
- Index definitions
- Enum values (LoadStatus, LoadType, etc.)
- Sequence generators (Number fields)
- JSON columns (TaxBreakdownJson)
- Giá trị dữ liệu hiện có → không migration phá vỡ

## 9. Risks & Mitigation

| Risk                                      | Mitigation                                                   |
| ----------------------------------------- | ------------------------------------------------------------ |
| SignalR → WebSocket behavior khác         | Giữ lại SignalR hub endpoints temporarily, test kỹ           |
| EF Lazy Loading vs Hibernate lazy         | Test kỹ, có thể dùng JOIN FETCH                              |
| Duende IdentityServer complex flow        | Giữ nguyên Duende cho Phase 1, migrate sau                   |
| Stripe SDK behavior khác                  | Viết integration test cho từng Stripe endpoint               |
| Hangfire job timing khác với Quartz       | Test cron expression tương đương                             |
| JSON serialization khác biệt              | Dùng Jackson config để match chính xác output cũ             |
| Multi-tenant routing khác                 | Giữ nguyên X-Tenant header mechanism                         |

## 10. Testing Strategy

- **Unit Test**: Mỗi command/handler/service có test tương đương .NET
- **Integration Test**: Testcontainers với PostgreSQL thật
- **API Contract Test**: REST Assured hoặc WebTestClient
- **Behavior Comparison Test**: Chạy cùng input → so sánh output
- **Frontend Smoke Test**: Angular chạy với Spring Boot backend thay vì .NET

## 11. First Module to Migrate: Loads (Module Operations)

**Lý do chọn Loads làm POC:**
- Business logic đơn giản (CRUD + status transitions)
- Ít external dependencies (không cần Stripe, AI, ELD)
- API trực quan, dễ test
- DTO đã rõ ràng

**Files cần migrate trong Loads module:**

| .NET Source                                      | Spring Boot Target                             |
| ------------------------------------------------ | ---------------------------------------------- |
| `Domain/Entities/Load/Load.cs`                   | `entity/Load.java`                             |
| `Domain.Primitives/Enums/Load/LoadStatus.cs`     | `enums/LoadStatus.java`                        |
| `Domain.Primitives/Enums/Load/LoadType.cs`       | `enums/LoadType.java`                          |
| `Domain.Primitives/Enums/Load/LoadSource.cs`     | `enums/LoadSource.java`                        |
| `Shared.Models/Load/LoadDto.cs`                  | `dto/LoadDto.java`                             |
| `Application/Modules/Operations/Loads/Commands/CreateLoad/CreateLoadCommand.cs` | `command/CreateLoadCommand.java` |
| `Application/Modules/Operations/Loads/Commands/CreateLoad/CreateLoadHandler.cs` | `service/LoadCommandService.java` |
| `Application/Modules/Operations/Loads/Queries/GetLoadById/GetLoadByIdQuery.cs` | `query/GetLoadByIdQuery.java` |
| `Application/Modules/Operations/Loads/Queries/GetLoads/GetLoadsQuery.cs` | `query/GetLoadsQuery.java` |
| `API/Controllers/LoadController.cs`              | `controller/LoadController.java`              |
| `Infrastructure.Persistence/Configurations/Load/LoadConfiguration.cs` | `entity/Load.java` (JPA annotations) |

**Rủi ro:**
- Status transition logic trong handler cần match chính xác
- Pagination behavior (PagedResponse format)
- Lazy loading vs Eager loading khác biệt
- Number auto-generation sequence
