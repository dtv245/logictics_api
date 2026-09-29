# Project Overview

## Architecture

- **Framework**: Spring Boot 4.1.0 (SB4), Java 21
- **Persistence**: Spring Data JPA (Hibernate), Flyway for migrations, PostgreSQL
- **Security**: Spring Security OAuth2 Resource Server, JWT (jjwt 0.12.3)
- **API Documentation**: SpringDoc OpenAPI (springdoc-openapi-starter-webmvc-ui 2.8.9)
- **Mapping**: MapStruct 1.5.5.Final (declared but NOT actually used)
- **Utilities**: Lombok, HikariCP
- **Multi-tenancy**: Custom ThreadLocal-based tenant context with dynamic DataSource routing

## Package Structure

```
com.company.logicstic/
├── LogicsticApplication.java
├── config/              # Multi-tenancy, JPA auditing, OpenAPI, Mapper config
├── controller/          # REST controllers (13 controllers)
├── dto/                 # Request/Response records (flat + domain sub-packages)
├── entity/              # JPA entities (45+ entities)
├── exception/           # Global exception handler + custom exceptions
├── mapper/              # EMPTY - MapStruct mappers not created
├── repository/          # JPA repositories (12 repos)
└── service/             # Business logic services (15 services)
```

## Business Domains

- **Customer Management**: Customer CRUD
- **Employee/Driver Management**: Employee CRUD with role assignment
- **Truck Management**: Truck CRUD
- **Load Management**: Load CRUD with complex address/location fields
- **Trip Management**: Trip CRUD with truck assignments
- **Invoice Management**: Invoice CRUD with line items, customer/employee/load relations
- **Payment Management**: Payment CRUD with invoice relations
- **Document Management**: Document CRUD with polymorphic relations (load/truck/employee)
- **Notification Management**: Notification read/mark-read
- **Message/Conversation**: Messaging between employees
- **Inspection**: Load condition reports
- **Role Management**: TenantRole CRUD

---

# Problems Found

## Critical

### 1. MapStruct Not Used (Mapper layer empty)
- `MapperConfiguration.java` exists, `mapstruct-processor` is in pom.xml, but no actual mapper interfaces exist
- All DTO<->Entity mapping is done manually via static `from()` methods on records
- All `applyFields()` methods in services manually copy fields — this is what MapStruct should automate
- **Impact**: ~300 lines of manual mapping code that is error-prone and violates DRY

### 2. Massive Code Duplication (DRY Violation)
Every service has the identical pattern:
- `search()` — creates Sort, creates PageRequest, calls repo.search, wraps in PagedResponse.from()
- `getById()` — findById + map + orElseThrow
- `create()` — new Entity(), applyFields(), repository.save()
- `update()` — findById, applyFields(), repository.save()
- `delete()` — existsById check, deleteById

This pattern repeats in **12+ services** with zero abstraction.

### 3. Service Layer Data Mapping Leakage
- Services directly access repository.findById() for foreign key resolution (e.g., `customerRepository.findById(req.customerId())`)
- This couples services to multiple repositories, violating Single Responsibility
- Foreign key resolution should be in a dedicated layer or handled by the repository

### 4. Missing Tests
- Zero test classes exist in `src/test`
- No unit tests, no integration tests, no controller tests

### 5. Missing JavaDoc
- Zero JavaDoc comments on any public class or method
- No business rule documentation

## Medium

### 6. N+1 Query Risk
- `getById()` = single findById which may trigger lazy loading if the entity has lazy relationships
- No `JOIN FETCH` or `EntityGraph` used on any repository method
- The `search()` queries only select the root entity — related entities will be lazy-loaded

### 7. Sort Injection Risk
- `Sort.by(orderBy)` takes user-provided field names directly from request parameters
- This allows potential sort-by-arbitrary-column injection (though Spring Data validates against unknown properties)

### 8. Mixed Case Column Names in Base Entity
- `BaseAuditableEntity` uses quoted column names: `"CreatedAt"`, `"CreatedBy"`, `"LastModifiedAt"`, `"LastModifiedBy"`
- Mixed case with quotes is non-standard and forces case-sensitive queries

### 9. `applyFields()` Methods Are Long Methods
- `LoadService.applyFields()`: 55 lines
- `InvoiceService.applyFields()`: 36 lines
- `PaymentService.applyFields()`: 23 lines
- These should be replaced by MapStruct mappers

### 10. Repository + DTO Package Boundary Violation
- `ResponseMeta` class is in the `repository` package but is a DTO used by the API response layer
- Should be in the `dto` package

### 11. Missing Input Validation for Search Parameters
- `page`, `pageSize`, `orderBy` are not validated
- Negative page numbers, excessive page sizes could cause issues

### 12. Missing Logging in Services
- No SLF4J logging in any service class
- Only `GlobalExceptionHandler` has logging

## Minor

### 13. Driver-Specific Logic Leakage
- `EmployeeService.searchDrivers()` has a TODO comment and delegates to `search()` with null roleId
- No actual driver filtering implemented

### 14. ConversationService Missing Update/Delete
- `ConversationService` only has `create()`, `getById()`, and `listByParticipant()`
- No update or delete methods — possible incomplete API

### 15. NotificationService Missing Filter Parameters
- `NotificationService.list()` has no search/filter parameters
- Returns ALL notifications paginated — could become a performance issue

### 16. Magic Strings for Page Defaults
- `defaultValue = "1"` for page, `defaultValue = "20"` for pageSize repeated across all controllers
- Should be constants

### 17. `@AllArgsConstructor` on Entities
- Entities have `@AllArgsConstructor` from Lombok which creates a constructor with all fields
- This is dangerous as field reordering breaks the constructor silently

### 18. Useless `@AllArgsConstructor` on `BaseAuditableEntity`
- Abstract class with `@AllArgsConstructor` — constructors on abstract classes should not be used

---

# Refactoring Strategy

## WHY

The current codebase has high cyclomatic redundancy, zero test coverage, no documentation, and significant code duplication. While functionally complete, it lacks the quality attributes expected of production enterprise software: maintainability, testability, and scalability.

## HOW

Phase the refactoring to minimize risk:

1. **Common Infrastructure** — Extract shared patterns, add constants, fix package boundary violations
2. **Mapper Layer** — Implement MapStruct interfaces for all DTO<->Entity mappings
3. **Service Layer** — Create abstract base service to eliminate CRUD duplication
4. **Repository Layer** — Add JOIN FETCH/EntityGraph to prevent N+1
5. **Controller Layer** — Extract common controller patterns, add validation
6. **Documentation** — Add JavaDoc to all public APIs
7. **Testing** — Add unit tests, integration tests, controller tests
8. **Cleanup** — Remove dead code, fix naming, add logging

## EXPECTED BENEFITS

- **~40% reduction in code volume** by eliminating duplication
- **100% MapStruct coverage** replacing manual mapping
- **Test coverage > 80%** for business logic
- **N+1 query elimination**
- **Full JavaDoc coverage** for all public APIs
- **Improved maintainability** through consistent patterns

---

# Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Breaking API compatibility | **High** | Keep all API signatures identical; add fields only |
| Service base class change regression | **Medium** | Comprehensive test suite before/after |
| MapStruct mapping errors | **Medium** | Verify all mappers with integration tests |
| Entity changes affecting DB schema | **Low** | No schema changes — mapping only |
| Removing dead code | **Low** | Verify no callers exist |

---

# Refactoring Order

1. **Common Infrastructure** — Constants, ResponseMeta relocation, page defaults
2. **MapStruct Mappers** — Create all mapper interfaces, eliminate `from()` and `applyFields()`
3. **Abstract Base Service** — Extract CRUD template to reduce duplication by ~60%
4. **Repository N+1 Fixes** — Add JOIN FETCH / EntityGraph annotations
5. **Controller Cleanup** — Constant page defaults, validation for search params
6. **Documentation** — JavaDoc for all public classes and methods
7. **Logging** — Add SLF4J logging to all services
8. **Testing** — Unit tests (Service layer), Integration tests (Repository layer), Controller tests (MockMvc)
9. **Final Cleanup** — Remove unused imports, fix minor issues, verify compatibility