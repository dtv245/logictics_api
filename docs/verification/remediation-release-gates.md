# Backend remediation release gates

The backend changes implement the confirmed BE-DEC-001…006 decisions, the
user-approved signing-key extension and the subsequently approved Customer
default-search / SETTLED reporting follow-ups. Local source, PostgreSQL and
executable verification does not certify the existing deployed application.

## Client contracts

The adjacent client `/home/vumoi/logictics-app` was inspected read-only. Its
interceptor attaches bearer tokens; inspected core forms/types/providers do not
carry Payment `idempotencyKey` or update `expectedVersion`. Client readiness is
unverified. No frontend files were changed.

- Protected business calls require a valid token. Handle 401 as authentication
  required and 403 as denied authorization. Configure explicit allowed origins
  before a browser rollout.
- Payment creation requires an invoice, a stable idempotency key for one user
  intent and PENDING status. Retain the same key across retries. Generic PUT only
  edits pending metadata; DELETE returns `PAYMENT_DELETE_FORBIDDEN` 409. Cancel
  preserves the row and audit; successful terminal payments require a separate
  future reversal workflow.
- Load, Trip and Truck responses expose `version`. Their update DTOs require
  `expectedVersion`; a stale 409 requires rereading and resolving the conflict.
  Do not replace the client's version with a fresh server version automatically.
- Messaging employee selectors must identify the caller; omitted/self sender
  values remain supported, spoofed senders are denied. Only ADMIN creates tenant
  chats; private membership has no implicit ADMIN bypass.
- Validation retains `errors[]` and `meta.requestId`. Transport input errors are
  400, financial business failures 422, state/idempotency/version conflicts 409.

Lark login resolves the server-configured active tenant and its employee/role,
then signs that same tenant. Bound/principal tenant conflicts are denied before
provider calls. Cached tenant pools still require active registry membership.
The adjacent client's callback already uses `authentication: "none"`; this does
not remove the server's responsibility to reject conflicting supplied tokens.

## Artifact and rollout

The current workspace is dirty and remains at historical HEAD `0c795f8`.
Packaged local builds record a source-file hash, unique build ID and dirty marker.
They do not claim equivalence with a reviewed clean commit.

Before release, review the intended changes without staging unrelated files,
provision an explicit strong `LARK_JWT_SECRET`, confirm client contracts, and
build an immutable image from a reviewed clean commit. The signing secret must
be unique to the deployment and absent from logs or verification documents.

Apply the additive schema through the approved application-managed boundaries:
single-tenant Boot Flyway on its explicit datasource; multitenant migration on
active tenant datasources; no default Boot migration on an unbound router;
registry migrations remain separate; NoDB disables persistence. Stop a failed
tenant batch, retain migrated/pending identities, and roll forward without
repairing or rewriting applied migrations.

Verify JAR hash, packaged metadata, OCI revision/version/build labels, running
image ID, authenticated ADMIN build response, normalized OpenAPI and each tenant
Flyway history. `scripts/verify_remediation_runtime.py --release` rejects a dirty
source, missing image identity, metadata mismatch or missing required contracts.
Release-artifact mode also requires an explicit canonical OpenAPI digest. Both
modes report `deployment_verified: false`: artifact identity alone does not
verify tenant schemas, clients or the complete rollout gate.

This task has not restarted or rebuilt the existing `logistics-api` container. Its
baseline anonymous Payment response was 200 and its packaged migrations ended
at V35. That security exposure and runtime drift remain open until an authorized
secure rollout is verified. Do not return to that permissive image as rollback.
Keep additive history guards; pin a verified compatible secure image or pause
affected writes if a rollout fails.

## Approved review follow-ups

The user answered “Include both fixes” on 2026-10-07 for the concrete changes in
[backend-follow-up-proposals.md](backend-follow-up-proposals.md). Customer search
now explicitly types its nullable name/email search parameters. The paid/open
balance reports include case-insensitive SETTLED alongside the existing terminal
success statuses. PENDING reserves funds without counting as paid; no payment
history, economic sign, reporting formula or migration is rewritten. Regression
includes actual PostgreSQL HTTP, two physical tenants, valid invoice lines and
populated V37→V38 legacy payment fixtures.

Latest backend verification: [status](backend-remediation-status.md) and
[machine evidence](backend-remediation-followup-evidence-20261007.json): 563 Java
including 214 PostgreSQL tests in each clean/populated-clone run, zero skips or
failures, plus 26 Python tests and local packaged executable PASS. This does not
close the deployed release gate.

## Findings outside the approved fixes

| Finding | Evidence | Required follow-up |
|---|---|---|
| BUG-BE-NEW-003: opaque Payment tenant UUID | Existing source generates a random legacy `payments.tenant_id`; physical routing and tenant-local keys are verified. Read-only working inventory contained no Payment rows, so it cannot establish provenance/consumer semantics. | Audit upstream meaning and consumers before production certification. No historical UUID mapping or data rewrite was inferred. |
| Shared audit principal length | Existing audit columns allow 50 characters. A longer valid fixture email caused persistence rejection. | Separate audit-identity/schema design; shorter regression fixture identities preserve the existing convention and do not establish support for long identities. |

BUG-BE-NEW-001 is covered by the zero-skip PostgreSQL CI/runner gates. The
user-approved BUG-BE-NEW-002 source fix and startup tests are verified; the
existing runtime key configuration remains unchanged. Candidate-002 static
analysis remains deferred: plugin removal is not proof its findings disappeared.
Business Redis/Spring Cache is inactive; Lark application-token caching exists.
No tenant business-cache certification is claimed.

## Verification commands

```bash
python3 scripts/run_isolated_remediation.py
python3 -m unittest discover -s scripts/tests
python3 scripts/verify_backend_regression.py --container <labelled-codex-server> --port <loopback-port> --upgrade-from <codex-populated-database>
python3 scripts/verify_disposable_artifact.py --manifest <artifact-manifest.json> --container <labelled-codex-server>
```

Both runners retain disposable databases/logs for diagnostics and reject working
database fallbacks. Runtime read-only verification receives the ADMIN token from
`TASK_RUNTIME_TOKEN`, never a command-line argument. Failsafe, Checkstyle,
SpotBugs and ArchUnit remain NOT CURRENTLY CONFIGURED. The GitHub workflow was
added; remote CI execution is not claimed by local results.
