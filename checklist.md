# Refactoring Checklist

**Scope: Phases 1-5 only** (Infrastructure, Mappers, Base Service, N+1 Fixes, Controller Cleanup)
**Skipped for now:** Documentation (6), Logging (7), Testing (8), Performance/Security (9), Final Quality Gate (10)

## Phase 1 — Common Infrastructure
- [x] Create plan.md with full project analysis
- [x] Create checklist.md with actionable tasks
- [x] Move `ResponseMeta` from `repository` to `dto` package + fix imports
- [x] Extract page constants (`DEFAULT_PAGE`, `DEFAULT_PAGE_SIZE`) into a shared constant class
- [x] Remove `@AllArgsConstructor` from `BaseAuditableEntity` (abstract class should not have it)
- [x] Remove `@AllArgsConstructor` from all entities (dangerous with field reordering)
- [x] Add input validation for search parameters (page, pageSize bounds)
- [x] Fix mixed-case column names in `BaseAuditableEntity` to use snake_case without quotes (requires Flyway migration) — columns: created_at, created_by, last_modified_at, last_modified_by; Flyway V2 already present

## Phase 2 — MapStruct Mappers
- [x] Create `CustomerMapper` interface
- [x] Create `InvoiceMapper` interface
- [x] Create `LoadMapper` interface
- [x] Create `TripMapper` interface
- [x] Create `PaymentMapper` interface
- [x] Create `EmployeeMapper` interface
- [x] Create `DocumentMapper` interface
- [x] Create `NotificationMapper` interface
- [x] Create `InspectionMapper` interface
- [x] Create `ConversationMapper` interface
- [x] Create `MessageMapper` interface
- [x] Create `RoleMapper` interface
- [x] Create `TruckMapper` interface
- [x] Remove all `static from()` methods from DTO records (replaced by mappers)
- [x] Remove all `applyFields()` methods from services

## Phase 3 — Abstract Base Service
- [x] Create `AbstractBaseService<Entity, View, CreateReq, ID>` with CRUD template methods
- [x] Create `BaseSearchSpecification` for reusable search logic
- [x] Refactor `CustomerService` to extend base service
- [x] Refactor `InvoiceService` to extend base service
- [x] Refactor `LoadService` to extend base service
- [x] Refactor `TripService` to extend base service
- [x] Refactor `PaymentService` to extend base service
- [x] Refactor `EmployeeService` to extend base service
- [x] Refactor `DocumentService` to extend base service
- [x] Refactor `NotificationService` to extend base service
- [x] Refactor `InspectionService` to extend base service
- [x] Refactor `ConversationService` to extend base service
- [x] Refactor `MessageService` to extend base service
- [x] Refactor `TruckService` to extend base service
- [x] Refactor `RoleService` to extend base service

## Phase 4 — Repository N+1 Fixes
- [x] Add `@EntityGraph(attributePaths = ...)` or `JOIN FETCH` to `CustomerRepository.search()`
- [x] Add `@EntityGraph` to `InvoiceRepository.search()`
- [x] Add `@EntityGraph` to `LoadRepository.search()`
- [x] Add `@EntityGraph` to `TripRepository.search()`
- [x] Add `@EntityGraph` to `PaymentRepository.search()`
- [x] Add `@EntityGraph` to `EmployeeRepository.search()`
- [x] Add `@EntityGraph` to `DocumentRepository.search()`
- [x] Add `@EntityGraph` to `LoadConditionReportRepository.search()`
- [x] Audit all `findById()` calls for lazy-load risks

## Phase 5 — Controller Cleanup
- [x] Replace magic numbers with constants in all controllers
- [x] Add validation for `@RequestParam` page/pageSize bounds
- [x] Add `@Validated` support for search parameter validation
- [x] Verify all API endpoints produce consistent response structures