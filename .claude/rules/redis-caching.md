---
description: "Redis caching rules for multi-tenant logistics platform, key prefix formatting, TTL definitions, and cache eviction."
globs: "src/main/java/com/company/logicstic/cache/**,src/main/resources/*.yml"
alwaysApply: true
---

# Redis Caching & Invalidation Guidelines

## Core Principles
1. **Multi-Tenant Key Prefix**: Cache keys MUST always follow the structure:
   `logistics:{tenant}:{cache}:{id}`
   This is enforced by `TenantAwareCacheKeyPrefix` to guarantee complete tenant cache isolation.
2. **Explicit Entity TTLs**: Every cache must define an explicit Time-To-Live (TTL) suited for its update frequency:
   - `terminal`: 24h (prod) / 30m (dev) / 2m (local)
   - `role`: 6h (prod) / 15m (dev) / 2m (local)
   - `customer`: 1h (prod) / 10m (dev) / 1m (local)
   - `employee`: 30m (prod) / 5m (dev) / 1m (local)
   - `truck`: 30m (prod) / 5m (dev) / 1m (local)
3. **Write-Time Eviction**: Any mutating operation (create, update, delete, state change) in a service MUST explicitly evict the affected cache entries using `@CacheEvict` or `CacheManager`.
4. **No Sensitive / Unencrypted Data**: Never cache unencrypted passwords, secrets, credit card numbers, or sensitive PII.
5. **Fail-Open Resilience**: Cache errors (e.g. Redis timeout or downtime) must be handled by `CacheErrorHandler` and gracefully fall back to the primary PostgreSQL database without disrupting user requests.
