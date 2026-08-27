# SLICE-004: Principal-bound document upload attribution

- **Status:** VERIFIED (bounded slice) — 2026-08-22
- **Date:** 2026-08-15
- **Requirements:** FR-01, FR-06, FR-11, BR-17, BR-19, API-DOC-01
- **Dependency:** VERIFIED `GET /api/me` and current employee mapping from SLICE-001
- **Deferred requirement:** API-DOC-02 and TC-017 require the separate relation/file-safety policy in BLK-008

## 1. Business outcome

An authenticated tenant employee uploading through the existing generic document endpoint can attribute the document only to themselves. The server derives the uploader from the validated JWT and tenant-local Employee mapping; the required legacy `uploadedById` field is accepted only as a compatibility assertion.

This slice closes uploader impersonation and prevents a caller-controlled Employee UUID from selecting the metadata owner or storage-key prefix. It deliberately does not invent a universal relation-access matrix or MIME/malware policy that the product documents leave unresolved.

## 2. Source hierarchy and evidence

Current Java source, Flyway and executable tests remain the implementation truth under DEC-002. Business documents define the target behavior:

- `project-specification.vi.md` FR-06 requires the Driver to upload POD/BOL for an assigned load and validates file/form input; AC3 denies access to another driver's load.
- FR-11 defines document upload as validation followed by blob persistence and entity linking, with owner/entity/tenant authorization; its MIME-size values remain unconfirmed.
- API-DOC-01 requires relation permission for generic document CRUD. API-DOC-02 describes dedicated assigned-driver POD/BOL endpoints, which do not exist in Spring.
- TC-017 requires content-based rejection of an executable renamed `.jpg`; R-12 calls for scanning, quarantine, signed URLs and limits.
- `frontend-context.md` documents the current multipart contract, including required `uploadedById`, and explicitly records the missing MIME/size/malware/idempotency policy.

The Spring implementation currently proves only the following:

- `SecurityConfiguration` allows every recognized tenant role to POST and GET documents, while DELETE is limited to operations roles.
- `DocumentController.upload` accepts no authenticated principal and forwards client metadata directly to `DocumentService`.
- `DocumentUploadRequest.uploadedById` is required. `DocumentServiceImpl.upload` uses it both as the storage-key prefix and as the Employee lookup for persisted `uploadedBy`.
- The service rejects empty files and simple traversal-style original names, generates a UUID stored name and compensates a metadata-save failure by deleting the newly written blob.
- Declared `Content-Type` is trusted, the full upload is read into memory, and no maximum size, content sniffing, malware/quarantine, idempotency or relation-level authorization is enforced.
- Existing document tests cover one happy upload/download/delete lifecycle and one metadata-save cleanup path. The functional test uses one PostgreSQL database and a broad SuperAdmin path.

## 3. Original defect

Any authenticated tenant role allowed to upload can supply another same-tenant Employee UUID as `uploadedById`. The service then:

1. creates the blob path under the supplied Employee UUID;
2. stores the bytes;
3. resolves that supplied Employee as the uploader; and
4. persists metadata attributed to that Employee.

Tenant routing limits lookups to the active tenant only when tenancy is enabled, but it does not establish which tenant employee is the caller. Therefore, tenant isolation alone does not prevent same-tenant uploader impersonation.

## 4. Frozen actor and compatibility rules

1. `POST /api/documents` remains protected by the current tenant-role matcher; no role matrix changes in this slice.
2. The authoritative uploader is the tenant-local Employee returned by `CurrentUserService.requireCurrentEmployeeId(authentication)`.
3. A valid JWT with no Employee mapping fails closed with `403 ACCESS_DENIED`; no document service, relation lookup, blob write or metadata write occurs.
4. `uploadedById` remains required on the multipart metadata contract for one compatibility phase. It is an assertion, never an identity selector.
5. When `uploadedById` differs from the current Employee, the request returns `403 ACCESS_DENIED` before any document lookup or mutation.
6. The document application service also receives the trusted current Employee ID and independently rejects a mismatched assertion. Its public upload method must not expose a client-selected uploader-only path.
7. The trusted Employee ID supplies both persisted `uploadedBy` and the server-generated blob-key prefix. Other metadata fields cannot override it.
8. All Employee/load/truck lookups continue through the current routed tenant datasource. This is tenant-local resolution, not proof of relation authorization.
9. Existing multipart part names, required metadata fields, response DTO, `201` success status and `ApiResponse` envelope remain unchanged.

## 5. Frozen filename controls

These controls do not depend on an unresolved product/provider choice and are included in SLICE-004:

1. Empty content and blank original filenames remain invalid.
2. A client filename cannot contain a directory component or `..` traversal sequence.
3. C0 control characters (`U+0000` through `U+001F`) and `U+007F` are rejected before blob storage. This includes CR/LF header injection characters.
4. The stored filename remains a server-generated UUID plus the accepted original extension; the original filename never selects a storage path.
5. Actor and filename validation complete before `getBytes`, blob storage, relation lookup or metadata persistence.
6. Error responses must not disclose the configured storage root or an internal absolute path.

These filename checks are not content validation and do not satisfy TC-017.

## 6. Endpoint behavior in this slice

| Endpoint | SLICE-004 behavior |
|---|---|
| `POST /api/documents` | Principal-bound uploader, compatibility assertion, trusted storage prefix and filename controls are implemented. |
| `GET /api/documents` | Unchanged; remains tenant-role and filter based, without a new relation/owner scope. |
| `GET /api/documents/{id}` | Unchanged; no new record-level authorization is claimed. |
| `GET /api/documents/{id}/download` | Unchanged; no new record-level authorization or content policy is claimed. |
| `DELETE /api/documents/{id}` | Unchanged; operations-role gate remains the only current authorization proof. |

## 7. Acceptance criteria

1. A request without a valid Bearer JWT returns `401` and never enters current-user or document application logic.
2. A valid identity without a tenant Employee mapping returns `403 ACCESS_DENIED`; no blob or metadata is created.
3. A legacy `uploadedById` different from the current Employee returns `403 ACCESS_DENIED`; the controller does not call `DocumentService`.
4. A direct application-service call with a trusted Employee ID that differs from `uploadedById` also returns `403` before file inspection, relation lookup, storage or repository calls.
5. A matching request persists the current Employee as `uploadedBy` and uses that Employee ID as the blob-key prefix.
6. Empty files, blank filenames, directory/traversal filenames and filenames containing C0/DEL control characters fail before a storage or repository write.
7. A valid existing multipart request still returns `201` with the same response shape, and its upload/download/delete lifecycle remains functional.
8. If metadata persistence fails after a blob write, the existing compensation behavior removes the blob; this slice does not claim distributed atomicity beyond that tested path.
9. Routes, multipart part names, required `uploadedById`, other metadata fields, role matchers, pagination, response DTOs and shared error/success envelopes remain unchanged.
10. Focused controller tests prove unauthenticated, unmapped, forged and matching identities. Focused service tests prove defense in depth, trusted attribution/path selection and pre-write filename rejection.
11. A PostgreSQL/API functional path proves a forged uploader is rejected and a matching uploader is persisted through the real endpoint. This remains single-database evidence unless a tenant-enabled two-database fixture is added.

## 8. Explicit exclusions

- Relation authorization for Load, Truck, Employee, TripStop, ConditionReport, Accident, DVIR or Maintenance records.
- Record-scoped search/detail/download/delete and privileged administrative bypass semantics.
- Dedicated POD/BOL endpoints and Driver assignment/delivery-context checks.
- MIME allowlist, content sniffing, executable detection, antivirus, quarantine and TC-017 completion.
- Maximum upload size, streaming storage, memory-pressure controls, object storage and signed URLs.
- Mobile idempotency/retry, duplicate detection and concurrent upload/delete behavior.
- Retention/legal hold, hard-versus-soft delete and storage transaction redesign.
- Customer/public tracking identities, public document links and tenant-enabled two-database E2E.

## 9. Open decisions and follow-up gate

BLK-008 must be resolved before describing document handling as safe or completing FR-11/API-DOC-01/API-DOC-02/TC-017. Product Owner, Security and Architecture must approve:

- an actor-by-relation read/upload/download/delete matrix, including Driver assignment and any Owner/SuperAdmin bypass;
- supported owner/relation combinations and behavior for unknown or multiple relations;
- MIME/content allowlist, exact size limits and response status for rejected content;
- malware scanner, quarantine/publication state, object-storage/signed-URL and failure policy;
- idempotency/retry, retention and delete semantics;
- a tenant-enabled two-database authorization fixture.

Until then, the current broad document read/download surface and weak content validation remain an open HIGH security risk under RSK-007.

## 10. Verification plan

- Controller slice tests with Spring Security for `401`, unmapped `403`, mismatched assertion `403` with no service call, and matching principal forwarding.
- Document service tests for direct mismatched assertion, trusted uploader/blob path, filename rejection and existing compensation behavior.
- `ApiFunctionalIT` document flow using an employee-mapped JWT for forged and matching uploader paths.
- Full `./mvnw test`, `./mvnw verify`, configured quality gates, GitNexus change detection, a scoped security diff scan and independent code review.

Passing this plan verifies only principal-bound upload attribution and the listed filename controls. It does not close BLK-008, RSK-003 or RSK-007.

## 11. Verification result

The bounded contract is verified. The exact ADR-004 scope is implemented with three production and three test files; focused tests pass 9/9, the full unit/architecture suite passes 198/198, and `./mvnw verify` passes 28/28 integration tests (21 API functional and 7 Redis) with Checkstyle, Spotless, SpotBugs and packaging clean. The real PostgreSQL multipart path rejects a forged uploader with `403`, persists the matching JWT-mapped Employee, and completes the existing list/download/delete lifecycle.

The sealed scoped security diff report records 0 reportable findings, and independent code review is `LOW — APPROVE`. Coverage remains bounded: functional/security-test requests use one database with tenancy disabled and `jwt()` postprocessors, so live JwtDecoder validation and tenant-enabled two-database isolation are not claimed. Relation authorization, broad read/download policy, MIME/size/content/malware, idempotency and retention remain BLK-008/RSK-007 follow-up.
