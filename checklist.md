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
- [ ] Add input validation for search parameters (page, pageSize bounds)
- [ ] Fix mixed-case column names in `BaseAuditableEntity` to use snake_case without quotes (requires Flyway migration)

## Phase 2 — MapStruct Mappers
- [ ] Create `CustomerMapper` interface
- [ ] Create `InvoiceMapper` interface
- [ ] Create `LoadMapper` interface
- [ ] Create `TripMapper` interface
- [ ] Create `PaymentMapper` interface
- [ ] Create `EmployeeMapper` interface
- [ ] Create `DocumentMapper` interface
- [ ] Create `NotificationMapper` interface
- [ ] Create `InspectionMapper` interface
- [ ] Create `ConversationMapper` interface
- [ ] Create `MessageMapper` interface
- [ ] Create `RoleMapper` interface
- [ ] Create `TruckMapper` interface
- [ ] Remove all `static from()` methods from DTO records (replaced by mappers)
- [ ] Remove all `applyFields()` methods from services

## Phase 3 — Abstract Base Service
- [ ] Create `AbstractBaseService<Entity, View, CreateReq, ID>` with CRUD template methods
- [ ] Create `BaseSearchSpecification` for reusable search logic
- [ ] Refactor `CustomerService` to extend base service
- [ ] Refactor `InvoiceService` to extend base service
- [ ] Refactor `LoadService` to extend base service
- [ ] Refactor `TripService` to extend base service
- [ ] Refactor `PaymentService` to extend base service
- [ ] Refactor `EmployeeService` to extend base service
- [ ] Refactor `DocumentService` to extend base service
- [ ] Refactor `NotificationService` to extend base service
- [ ] Refactor `InspectionService` to extend base service
- [ ] Refactor `ConversationService` to extend base service
- [ ] Refactor `MessageService` to extend base service
- [ ] Refactor `TruckService` to extend base service
- [ ] Refactor `RoleService` to extend base service

## Phase 4 — Repository N+1 Fixes
- [ ] Add `@EntityGraph(attributePaths = ...)` or `JOIN FETCH` to `CustomerRepository.search()`
- [ ] Add `@EntityGraph` to `InvoiceRepository.search()`
- [ ] Add `@EntityGraph` to `LoadRepository.search()`
- [ ] Add `@EntityGraph` to `TripRepository.search()`
- [ ] Add `@EntityGraph` to `PaymentRepository.search()`
- [ ] Add `@EntityGraph` to `EmployeeRepository.search()`
- [ ] Add `@EntityGraph` to `DocumentRepository.search()`
- [ ] Audit all `findById()` calls for lazy-load risks

## Phase 5 — Controller Cleanup
- [ ] Replace magic numbers with constants in all controllers
- [ ] Add validation for `@RequestParam` page/pageSize bounds
- [ ] Add `@Validated` support for search parameter validation
- [ ] Verify all API endpoints produce consistent response structures