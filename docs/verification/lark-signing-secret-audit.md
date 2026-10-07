# BUG-BE-NEW-002 audit, 2026-10-06

Finding: CONFIRMED source default signing key; runtime configuration evidence indicates the same fallback is active. Severity: CRITICAL. No secret value or token is recorded here.

Evidence: `application.yml` and `LarkProperties` both supply a shared fallback. `LarkAuthService` builds its signing/verifying key from this value. Running container `8347964b0323…` has no LARK_JWT_SECRET, uses `/app`, has no mounted configuration and no `/app/.env`. The existing image has no build identity; precise source equivalence remains unverified.

Proposed independent scope: remove both shared defaults; require an explicitly configured signing key with at least 32 UTF-8 bytes when Lark authentication is enabled; reject the known fallback value; keep a disabled-Lark NoDB profile bootable without signing any token; provide deterministic test-only keys and startup/disabled-authentication regressions. Document key provisioning and rotation before secure deployment. No production secret generation or runtime change is proposed in this task.

The user approved including this independent fix on 2026-10-06. Source defaults have been removed; enabled authentication rejects missing/short/shared keys, disabled authentication cannot issue or verify tokens, and a deterministic key exists only in test resources. Runtime remains unchanged. Provisioning a new secret and replacing the affected image are release gates.

Verification: five signing configuration tests cover key rejection, cross-key
verification, disabled token operations and actual property binding. Final full
clean/upgrade runs each passed 551 Java tests with zero skips. The final packaged
JAR rejects enabled-auth startup with an absent key and boots NoDB without a key;
an explicit private fixture key permits authenticated ADMIN build verification.
See `docs/verification/backend-remediation-status.md`. No deployed key rotation
or production startup verification was performed. Runtime fallback activation is
an inference from the observed configuration, not proof of source equivalence or
an exhaustive audit of every possible external property provider.
