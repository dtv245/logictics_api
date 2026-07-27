package com.company.logicstic.shared.config;

import org.springframework.data.redis.cache.CacheKeyPrefix;

/**
 * Builds the tenant-scoped Redis key prefix: {@code <app>:<tenant>:<cacheName>:}.
 *
 * <p>This is the single most important class in the cache layer. LogisticsX is
 * <strong>database-per-tenant</strong> ({@code docs/docs/architecture/multi-tenancy.md}), so the
 * same primary key exists in every tenant database and denotes a different row in each. An
 * unprefixed key such as {@code customer::42} would therefore serve company A's customer to company
 * B — a data breach, silently, with no error anywhere.
 *
 * <p>Spring Data recomputes this prefix on <em>every</em> cache operation (see {@code
 * RedisCache#prefixCacheKey}: "allow contextual cache names by computing the key prefix on every
 * call"), which is what makes a request-scoped value usable here.
 *
 * <p>A {@code KeyGenerator} would <em>not</em> work for this: it is bypassed whenever an annotation
 * declares an explicit SpEL {@code key}, and it never runs for {@code @CacheEvict(allEntries =
 * true)}. Prefixing at the configuration level covers reads, writes and evictions alike.
 *
 * <h2>Fail-closed</h2>
 *
 * When multi-tenancy is enabled and no tenant is bound, this throws instead of substituting a
 * default. Writing {@code getTenantId().orElse("default")} would look correct in single-tenant mode
 * and start leaking across tenants the moment {@code TENANCY_ENABLED=true} — with no failure to
 * alert anyone. An exception is the safe direction.
 */
public class TenantAwareCacheKeyPrefix implements CacheKeyPrefix {

  /**
   * Scope used when multi-tenancy is off: there is exactly one database, so one fixed scope is
   * correct. It is a literal rather than the word "default" to make an accidental fallback obvious
   * when reading keys in {@code redis-cli}.
   */
  static final String SINGLE_TENANT_SCOPE = "single";

  private final String applicationPrefix;
  private final boolean multiTenancyEnabled;

  public TenantAwareCacheKeyPrefix(String applicationPrefix, boolean multiTenancyEnabled) {
    this.applicationPrefix = applicationPrefix;
    this.multiTenancyEnabled = multiTenancyEnabled;
  }

  @Override
  public String compute(String cacheName) {
    return applicationPrefix + ":" + tenantScope() + ":" + cacheName + ":";
  }

  /**
   * @throws IllegalStateException when multi-tenancy is on but the request has no tenant bound
   */
  private String tenantScope() {
    return multiTenancyEnabled ? TenantContext.requireTenantId() : SINGLE_TENANT_SCOPE;
  }
}
