package com.company.logicstic.shared.config;

import com.company.logicstic.modules.customer.dto.response.CustomerResponse;
import com.company.logicstic.modules.employee.dto.response.EmployeeResponse;
import com.company.logicstic.modules.fleet.dto.response.TruckResponse;
import com.company.logicstic.modules.role.dto.response.RoleResponse;
import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.shared.common.CacheNames;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed Spring cache for the read-mostly reference data.
 *
 * <p>Off unless {@code app.cache.enabled=true}, the same switch style every tenancy bean uses
 * (docs/docs/development/engineering-conventions.md §11). With the flag off no {@code CacheManager}
 * exists and {@code @EnableCaching} never runs, so the {@code @Cacheable} annotations on the
 * services are inert — the application, and every existing test, behaves exactly as before.
 *
 * <h2>What is cached, and what deliberately is not</h2>
 *
 * Only the five reference features in {@link CacheNames}, and only their {@code getById} methods.
 * Three exclusions are load-bearing:
 *
 * <ul>
 *   <li><strong>{@code getEntityById} is never cached.</strong> It returns a managed JPA entity: no
 *       entity implements {@code Serializable}, its associations are {@code LAZY} so serialising
 *       would drag in Hibernate proxies, a deserialised copy would be detached when assigned as a
 *       {@code @ManyToOne} target, and the method doubles as the existence check whose contract is
 *       to throw {@code ResourceNotFoundException} — serving a deleted row from cache would turn a
 *       clean 404 into a foreign-key violation at flush time.
 *   <li><strong>Operational aggregates are not cached.</strong> Load and trip are the
 *       hottest-mutating entities; invoice is mutated straight through {@code InvoiceRepository} by
 *       {@code LoadDispatchedInvoiceListener}; conversation is mutated by {@code
 *       MessageServiceImpl} on every message sent; notification is updated by a bulk JPQL
 *       statement. None of those writes pass through the owning service, so no annotation could
 *       evict them.
 *   <li><strong>Paginated searches are not cached.</strong> They take six to nine parameters, so
 *       the key space explodes while the hit rate stays low, and every create or delete would have
 *       to clear the whole cache anyway.
 * </ul>
 *
 * <h2>Serialization</h2>
 *
 * Each cache holds exactly one DTO record type, so it gets its own type-bound {@link
 * JacksonJsonRedisSerializer}. The generic alternative requires polymorphic default typing, whose
 * stock validator allows every subtype — that is a deserialization-gadget surface this application
 * has no reason to open, and it writes a redundant {@code @class} property into every entry. Null
 * values are not cached, since a type-bound serializer cannot represent Spring's {@code NullValue}
 * marker.
 */
@Slf4j
@Configuration
@EnableCaching
@EnableConfigurationProperties({CacheProperties.class, TenancyProperties.class})
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true")
public class RedisCacheConfig implements CachingConfigurer {

  /**
   * Cache name to the single DTO type it stores.
   *
   * <p>Keeping this in the composition root follows the precedent of {@code SecurityConfiguration},
   * which likewise enumerates every feature's paths and roles in one reviewable place.
   */
  private static final Map<String, Class<?>> CACHED_TYPES =
      Map.of(
          CacheNames.TERMINAL, TerminalResponse.class,
          CacheNames.ROLE, RoleResponse.class,
          CacheNames.CUSTOMER, CustomerResponse.class,
          CacheNames.EMPLOYEE, EmployeeResponse.class,
          CacheNames.TRUCK, TruckResponse.class);

  private final CacheProperties cacheProperties;
  private final TenancyProperties tenancyProperties;
  private final ObjectMapper objectMapper;

  public RedisCacheConfig(
      CacheProperties cacheProperties,
      TenancyProperties tenancyProperties,
      ObjectMapper objectMapper) {
    this.cacheProperties = cacheProperties;
    this.tenancyProperties = tenancyProperties;
    this.objectMapper = objectMapper;
  }

  @Bean
  public CacheKeyPrefix tenantAwareCacheKeyPrefix() {
    return new TenantAwareCacheKeyPrefix(
        cacheProperties.getKeyPrefix(), tenancyProperties.isEnabled());
  }

  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory, CacheKeyPrefix keyPrefix) {

    Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
    CACHED_TYPES.forEach(
        (cacheName, type) ->
            perCache.put(cacheName, cacheConfiguration(cacheName, type, keyPrefix)));

    return RedisCacheManager.builder(cacheWriter(connectionFactory))
        .withInitialCacheConfigurations(perCache)
        // A cache name that is not in CACHED_TYPES is a typo, not a request for a default cache.
        // Failing loudly at first use beats creating one with the wrong serializer.
        .disableCreateOnMissingCache()
        // TerminalServiceImpl.update relies on dirty checking and never calls save(); cache advice
        // and transaction advice share the default order, so without this an evict could run before
        // the commit and let a concurrent read repopulate the pre-commit value.
        .transactionAware()
        .build();
  }

  /**
   * Builds a cache writer whose writes and evictions are visible immediately.
   *
   * <p>Spring Data Redis 4 defaults to <em>asynchronous</em> cache writes whenever the connection
   * factory is reactive — and Lettuce, the default driver, is. The documented consequence is that
   * "several Cache operations can be performed asynchronously or deferred", so a
   * {@code @CacheEvict} returns before Redis has actually dropped the key and a read moments later
   * still sees the old value. An integration test caught exactly that: an evicted entry was still
   * being served.
   *
   * <p>This whole design leans on eviction being correct — reference data is invalidated on write,
   * with the TTL only as a backstop — so fire-and-forget invalidation is not a trade this cache can
   * make. Immediate writes block on the reactive driver, which costs nothing here: the caller is a
   * servlet thread that is about to block on JDBC anyway.
   */
  private static RedisCacheWriter cacheWriter(RedisConnectionFactory connectionFactory) {
    return RedisCacheWriter.create(connectionFactory, config -> config.immediateWrites(true));
  }

  /**
   * Degrades to the database when Redis misbehaves, instead of failing the request.
   *
   * <p>The default handler rethrows, which would turn a Redis outage — or a single unparseable
   * entry left over from a DTO change — into a 500 on every read of otherwise healthy reference
   * data. A cache is an optimisation; losing it must cost latency, not availability.
   *
   * <p>A failed <em>evict</em> is the one case that is not merely slower: the stale entry survives
   * until its TTL. That is why every TTL in this configuration is finite, and why the failure is
   * logged at {@code warn} with the cache and key so it can be found.
   */
  @Override
  public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
      @Override
      public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn(
            "Cache read failed, falling back to the database cache={} key={}",
            cache.getName(),
            key,
            exception);
      }

      @Override
      public void handleCachePutError(
          RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache write failed cache={} key={}", cache.getName(), key, exception);
      }

      @Override
      public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn(
            "Cache evict failed — entry stays stale until its TTL expires cache={} key={}",
            cache.getName(),
            key,
            exception);
      }

      @Override
      public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn(
            "Cache clear failed — entries stay stale until their TTL expires cache={}",
            cache.getName(),
            exception);
      }
    };
  }

  private <T> RedisCacheConfiguration cacheConfiguration(
      String cacheName, Class<T> type, CacheKeyPrefix keyPrefix) {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(cacheProperties.ttlFor(cacheName))
        .computePrefixWith(keyPrefix)
        .serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
        .serializeValuesWith(
            SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, type)))
        .disableCachingNullValues();
  }
}
