package com.company.logicstic.shared.common;

/**
 * Names of the Redis caches, shared by the {@code @Cacheable}/{@code @CacheEvict} annotations in
 * the feature services and by the cache configuration that assigns each one a TTL and a serializer.
 *
 * <p>They are constants rather than inline strings because the two sides must agree exactly: the
 * cache manager is built with {@code disableCreateOnMissingCache()}, so a typo in an annotation is
 * a startup-time failure instead of a silently unconfigured cache falling back to JDK serialization
 * — which would fail anyway, since no DTO implements {@code Serializable}.
 *
 * <p>Only reference data is cached. Operational aggregates (load, trip, message, notification,
 * invoice, conversation) are deliberately absent — see the cache configuration for why.
 */
public final class CacheNames {

  /** UN/LOCODE directory. Near-immutable. */
  public static final String TERMINAL = "terminal";

  /** Per-tenant RBAC roles. */
  public static final String ROLE = "role";

  /** Shipper master data. */
  public static final String CUSTOMER = "customer";

  /** Employees and drivers. Their names are denormalised into five other features' DTOs. */
  public static final String EMPLOYEE = "employee";

  /** Fleet roster. {@code TruckResponse} carries driver names copied from employee. */
  public static final String TRUCK = "truck";

  private CacheNames() {}
}
