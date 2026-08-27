# ADR-004: Principal-bound document upload attribution

- **Status:** Implemented and verified (bounded slice) — 2026-08-22
- **Date:** 2026-08-15
- **Requirements:** FR-01, FR-06, FR-11, API-DOC-01
- **Delivery task:** SLICE-004
- **Business contract:** `docs/docs/business/slice-004-document-principal-attribution.md`

## Context

The existing `POST /api/documents` role gate authenticates a tenant user but the controller does not receive the principal. `DocumentUploadRequest.uploadedById` is required and the service currently uses that untrusted value for both the persisted uploader and the blob-key prefix. A same-tenant caller can therefore attribute an upload to another Employee.

SLICE-001 already provides `CurrentUserService.requireCurrentEmployeeId(JwtAuthenticationToken)`. SLICE-004 must reuse that boundary, retain the current multipart wire contract for compatible clients and reject control-bearing/traversal filenames without expanding into the unresolved relation/content policy in BLK-008.

## Decision

### Controller trust boundary

`DocumentController` receives `CurrentUserService` through constructor injection. The upload handler also receives Spring Security's authenticated `JwtAuthenticationToken`.

Before invoking document application logic, the controller:

1. calls `requireCurrentEmployeeId(authentication)`;
2. compares the result with required `metadata.uploadedById()`; and
3. throws `ApiException(ErrorCode.ACCESS_DENIED, ...)` on mismatch.

No valid Employee mapping and a forged legacy assertion therefore fail before `DocumentService` is invoked. This mirrors the already-verified messaging compatibility pattern and does not add a new identity mechanism.

### Principal-bound service API

Replace the externally callable two-argument upload operation with:

```java
DocumentResponse upload(
    UUID currentEmployeeId,
    MultipartFile file,
    DocumentUploadRequest request);
```

The implementation independently checks `currentEmployeeId` against `request.uploadedById()` before calling any `MultipartFile`, employee/relation service, storage or repository method. A mismatch throws `403 ACCESS_DENIED`.

There is no retained public `upload(file, request)` overload. This prevents a future caller from accidentally reintroducing client-selected attribution. The existing controller and focused test are the only current callers that must migrate.

### Trusted attribution and write ordering

After the identity assertion passes, `DocumentServiceImpl.upload` performs operations in this order:

1. reject an empty file;
2. validate and normalize the original filename;
3. generate a UUID stored filename and build `blobPath` from `currentEmployeeId`;
4. resolve the current uploader and the existing optional Employee/Load/Truck relations inside the routed tenant datasource;
5. build unsaved metadata with the resolved current uploader;
6. read/store bytes under the generated key;
7. save and flush metadata; and
8. delete the newly stored blob if metadata persistence throws.

Resolving metadata relations before the blob write avoids a transient blob when an existing referenced record is missing. It does not authorize those relations and must not be described as an object-access fix.

`buildDocument` receives the trusted current Employee ID separately from the compatibility request and never reads `request.uploadedById()` for persistence. Both the metadata owner and path prefix therefore share one server-owned source.

### Filename boundary

`safeOriginalName` rejects before any relation or write sink:

- null or blank names;
- `/` or `\` path separators;
- any `..` traversal sequence;
- C0 control code points `U+0000` through `U+001F`; and
- `U+007F`.

The check for controls and separators happens before `Path.of`, so invalid input is converted to the existing `400` validation envelope rather than leaking a platform exception. `Path.getFileName` remains a defense-in-depth basename check.

The stored filename remains a server-generated UUID plus the accepted extension. MIME/content/size enforcement is not inferred from the filename.

### Tenant, role and data behavior

- `SecurityConfiguration` is unchanged: every current tenant role may upload.
- `CurrentUserService` and its employee lookup are unchanged.
- `DocumentUploadRequest.uploadedById` remains `@NotNull`; no multipart/OpenAPI shape changes.
- Employee/Load/Truck services and repositories are unchanged and continue through the routed tenant datasource.
- Document entity, mapper, repository, storage abstraction, Flyway and configuration are unchanged.
- Search/detail/download/delete remain outside the new principal boundary pending BLK-008.

No database migration or index is required. The existing non-null `documents.uploaded_by_id` relationship stores the server-resolved Employee exactly as before.

## Error and compatibility contract

| Condition | Result |
|---|---|
| Missing/invalid JWT | Existing `401 UNAUTHENTICATED`; controller/service not entered |
| JWT has no tenant Employee mapping | `403 ACCESS_DENIED`; no document service/storage/repository access |
| Required `uploadedById` missing | Existing multipart validation `400` behavior |
| Legacy uploader assertion differs from current Employee | `403 ACCESS_DENIED` before document/file access or mutation |
| Empty file or invalid filename | Existing `400` error envelope before storage/repository write |
| Missing existing relation | Existing not-found behavior, now before blob storage |
| Valid upload | Existing `201` and unchanged `DocumentResponse`/`ApiResponse` shape |
| Metadata save failure after blob write | Original failure propagates after best-effort existing blob cleanup |

## Rejected alternatives

### Remove `uploadedById` immediately

Rejected for this slice because the field is currently required and documented for existing clients. It can be deprecated/removed only through a later API compatibility decision.

### Ignore a mismatched legacy assertion

Rejected because silent acceptance hides stale clients and attempted impersonation. Explicit `403` is observable and matches the messaging migration pattern.

### Keep the old public service overload

Rejected because it remains an application-level bypass for future callers. Current production caller search finds only the REST controller, so replacing the method is low risk.

### Resolve current user only in the service

Rejected because the document module should receive a framework-independent trusted Employee ID, while the web adapter owns `JwtAuthenticationToken`. Defense-in-depth equality remains in both layers.

### Add relation authorization or file-content policy now

Rejected because the actor-by-relation matrix, Driver assignment semantics, size limits, MIME allowlist, scanner/quarantine, idempotency and retention are not approved. Implementing them would invent product/security policy and overstate TC-017 coverage.

### Modify global security matchers or schema

Rejected. The defect is actor attribution inside an already authenticated route, not route eligibility or persistence shape.

## Test design

### Controller slice

A new `DocumentControllerTest` uses the real security filter configuration with mocked `CurrentUserService` and `DocumentService`:

- unauthenticated multipart request returns `401` and touches neither service;
- unmapped identity returns `403` and never calls document service;
- forged `uploadedById` returns `403` and never calls document service;
- a matching Employee ID forwards the trusted ID, file and unchanged request and returns `201`.

### Service slice

Extend `DocumentServiceTest`:

- migrate existing lifecycle and save-failure compensation tests to the three-argument API;
- prove a direct mismatched assertion returns `ACCESS_DENIED` with zero file/dependency interactions;
- prove persisted `uploadedBy` and the blob-key prefix use `currentEmployeeId`;
- parameterize directory, backslash, traversal, CR/LF, NUL/other C0 and DEL filename rejection with no storage/repository write;
- retain empty-file and metadata-save cleanup coverage.

### Functional and regression evidence

Update `ApiFunctionalIT.documents` to authenticate the upload as the existing `driver.it@example.com` Employee:

1. submit a forged uploader UUID and assert `403 ACCESS_DENIED`;
2. submit the matching Employee ID and assert `201` plus the returned uploader ID;
3. keep list/download/delete lifecycle assertions.

This is a real Spring/PostgreSQL/blob path in one database with tenancy disabled. It does not satisfy the deferred tenant-enabled two-database proof.

After focused tests, run full unit/architecture and Testcontainers verify suites, configured quality gates, GitNexus change detection, a scoped security diff scan and independent code review.

### Verification outcome

The frozen implementation boundary is complete: exactly three production and three test files changed. Focused controller/service tests pass 9/9; full unit/architecture tests pass 198/198; `./mvnw verify` passes 21 API functional and 7 Redis integration tests; Checkstyle, Spotless, SpotBugs, packaging and `git diff --check` pass. The sealed scoped security diff report contains 0 reportable findings, and independent code review is `LOW — APPROVE`.

The PostgreSQL functional fixture runs with tenancy disabled and security-test `jwt()` postprocessors, so this ADR does not claim live JwtDecoder or tenant-enabled two-database proof. BLK-008 relation/content lifecycle policy and RSK-007 broad document authorization remain explicitly open.

## Frozen implementation boundary

Production files:

1. `modules/document/controller/DocumentController.java`
2. `modules/document/service/DocumentService.java`
3. `modules/document/service/impl/DocumentServiceImpl.java`

Test files:

1. new `modules/document/controller/DocumentControllerTest.java`
2. `modules/document/service/DocumentServiceTest.java`
3. `ApiFunctionalIT.java`

No other production/test file is authorized without a fresh impact check and Tech Lead handback. In particular, `DocumentUploadRequest`, `DocumentResponse`, `Document`, `DocumentMapper`, `DocumentRepository`, `DocumentStorage`, `FileSystemDocumentStorage`, `CurrentUserService`, Employee/Load/Truck services, `SecurityConfiguration`, Flyway and application configuration stay unchanged.

## Impact analysis

GitNexus upstream impact on 2026-08-15, with tests included and confidence at least 0.8:

| Existing target | Risk | Evidence |
|---|---|---|
| `DocumentController` class, constructor and upload handler | LOW | 0 indexed upstream dependents/processes |
| `DocumentService` interface | LOW | 3 direct / 4 total dependents: implementation and controller imports plus focused test transitively |
| `DocumentService.upload` | LOW | 2 direct dependents: controller handler and implementation; upload process only |
| `DocumentServiceImpl` | LOW | 2 direct / 4 total test dependents |
| `DocumentServiceImpl.upload` | LOW | 0 indexed upstream dependents; interface analysis supplies caller evidence |
| `buildDocument` and `safeOriginalName` | LOW | 1 direct caller each; only the upload process is affected |
| Existing `DocumentServiceTest` methods/helpers and `ApiFunctionalIT.documents` | LOW | At most 2 direct focused-test dependents; no production process |

The indexed process chain confirms `DocumentController.upload → DocumentServiceImpl.upload → DocumentStorage.store`. GitNexus's API route map returned no node for `/api/documents`, so route-consumer impact is an index limitation; exact controller/interface caller search and the preserved wire contract compensate for it.

No HIGH or CRITICAL impact target exists. The business security risk remains HIGH under RSK-007, which is why focused negative tests and a security diff scan are mandatory even though the code blast radius is LOW.

## Consequences

- Same-tenant callers can no longer choose another Employee as uploader or storage-key prefix.
- Correct existing clients retain the same multipart contract and receive the same success response.
- Service callers must supply a trusted current Employee ID; there is no legacy bypass overload.
- Relation-level IDOR and unsafe content acceptance remain visible under BLK-008/RSK-007 instead of being hidden by an overbroad completion claim.
- Rollback is limited to the three production files plus focused tests; there is no schema/config rollback.
