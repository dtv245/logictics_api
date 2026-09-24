---
description: "OAuth2 Resource Server & JWT security standards, claims validation, and sensitive data protection."
globs: "src/main/java/com/company/logicstic/security/**,src/main/java/com/company/logicstic/identity/**"
alwaysApply: true
---

# OAuth2 Resource Server & JWT Security Guidelines

## Core Principles
1. **Strict Token Verification**: OAuth2 Resource Server must validate JWT signatures, issuer (`iss`), audience (`aud`), and expiration (`exp`) against the configured Identity Provider JWKS endpoint.
2. **Tenant Claim Verification**: Incoming JWT tokens must contain valid tenant claims, and the `TenantJwtClaimFilter` must ensure the authenticated principal has permission to access the requested tenant context.
3. **No Sensitive Logging**: NEVER log raw JWT access tokens, refresh tokens, Authorization headers, or passwords.
4. **Security Filter Chain Whitelist**:
   - Protected: All business domain endpoints (`/api/**`) require valid authentication and appropriate role claims.
   - Public: Only health checks (`/actuator/health`), OpenAPI docs (`/swagger-ui/**`, `/v3/api-docs/**`), and local dev-auth endpoints (when explicitly enabled) may be public (`permitAll`).
5. **Principal-Bound Operations**: Sensitive domain operations (e.g. status confirmations, messaging, payments) must bind the actor to the authenticated employee ID extracted from the security principal rather than trusting client-supplied ID parameters.
