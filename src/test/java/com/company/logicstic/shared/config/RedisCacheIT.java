package com.company.logicstic.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.shared.common.CacheNames;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end check of the cache against a real Redis.
 *
 * <p>The one assertion that justifies this test existing is {@link
 * #twoTenantsWithTheSameIdNeverSeeEachOthersData()}: everything else could be verified with a mock,
 * but tenant isolation depends on Spring Data recomputing the key prefix on every operation, which
 * only a real cache round-trip proves.
 *
 * <p>The class disables itself when there is no Docker daemon, so {@code mvn verify} stays green on
 * a machine that simply cannot run containers rather than failing for an environment reason.
 * Testcontainers 2.x dropped its JUnit 5 extension, so the container lifecycle is explicit here.
 */
@EnabledIf("dockerIsAvailable")
@SpringJUnitConfig(RedisCacheIT.CacheTestConfig.class)
@TestPropertySource(
    properties = {
      "app.cache.enabled=true",
      "app.cache.key-prefix=logistics",
      "app.cache.ttl.terminal=24h",
      "app.tenancy.enabled=true"
    })
@DisplayName("Redis cache")
class RedisCacheIT {

  private static final int REDIS_PORT = 6379;

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

  static boolean dockerIsAvailable() {
    return DockerClientFactory.instance().isDockerAvailable();
  }

  @BeforeAll
  static void startRedis() {
    REDIS.start();
  }

  @AfterAll
  static void stopRedis() {
    REDIS.stop();
  }

  @DynamicPropertySource
  static void redisConnection(DynamicPropertyRegistry registry) {
    // Resolved lazily, when the Spring context is first built — which happens after @BeforeAll, so
    // the container is already up and its mapped port is known.
    registry.add("test.redis.host", REDIS::getHost);
    registry.add("test.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
  }

  private final CountingTerminalReader reader;
  private final StringRedisTemplate redis;

  @Autowired
  RedisCacheIT(CountingTerminalReader reader, StringRedisTemplate redis) {
    this.reader = reader;
    this.redis = redis;
  }

  @BeforeEach
  void resetState() {
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    reader.resetCalls();
    TenantContext.clear();
  }

  @Test
  @DisplayName("a second read is served from Redis instead of the database")
  void secondReadHitsTheCache() {
    TenantContext.setTenantId("acme");
    UUID id = UUID.randomUUID();

    TerminalResponse first = reader.getById(id);
    TerminalResponse second = reader.getById(id);

    assertThat(reader.callCount()).isEqualTo(1);
    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("the record round-trips through JSON with its enum and timestamp intact")
  void serialisesTheWholeRecord() {
    TenantContext.setTenantId("acme");
    UUID id = UUID.randomUUID();

    TerminalResponse original = reader.getById(id);
    TerminalResponse fromRedis = reader.getById(id);

    assertThat(fromRedis.type()).isEqualTo(TerminalType.SEA_PORT);
    assertThat(fromRedis.code()).isEqualTo("USNYC");
    assertThat(fromRedis.createdAt()).isEqualTo(original.createdAt());
    assertThat(fromRedis.id()).isEqualTo(id);
  }

  @Test
  @DisplayName("keys are namespaced by application, tenant and cache")
  void keysCarryTheTenant() {
    TenantContext.setTenantId("acme");
    UUID id = UUID.randomUUID();

    reader.getById(id);

    Set<String> keys = redis.keys("*");
    assertThat(keys).containsExactly("logistics:acme:" + CacheNames.TERMINAL + ":" + id);
  }

  @Test
  @DisplayName("two tenants with the same id never see each other's data")
  void twoTenantsWithTheSameIdNeverSeeEachOthersData() {
    UUID sharedId = UUID.randomUUID();

    TenantContext.setTenantId("acme");
    TerminalResponse acme = reader.getById(sharedId);

    TenantContext.setTenantId("swift");
    TerminalResponse swift = reader.getById(sharedId);

    // Same primary key, different tenant database, therefore a different row: the cache must have
    // gone back to the source rather than serving acme's entry.
    assertThat(reader.callCount()).isEqualTo(2);
    assertThat(swift.name()).isNotEqualTo(acme.name());
    assertThat(redis.keys("*"))
        .containsExactlyInAnyOrder(
            "logistics:acme:" + CacheNames.TERMINAL + ":" + sharedId,
            "logistics:swift:" + CacheNames.TERMINAL + ":" + sharedId);
  }

  @Test
  @DisplayName("a write evicts the cache, so the next read goes back to the database")
  void writeEvicts() {
    TenantContext.setTenantId("acme");
    UUID id = UUID.randomUUID();
    reader.getById(id);
    reader.getById(id);
    assertThat(reader.callCount()).isEqualTo(1);

    reader.update(id);
    reader.getById(id);

    assertThat(reader.callCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("evicting one tenant leaves the other tenant's entry alone")
  void evictIsScopedToTheTenant() {
    UUID id = UUID.randomUUID();
    TenantContext.setTenantId("acme");
    reader.getById(id);
    TenantContext.setTenantId("swift");
    reader.getById(id);
    assertThat(reader.callCount()).isEqualTo(2);

    TenantContext.setTenantId("acme");
    reader.update(id);

    TenantContext.setTenantId("swift");
    reader.getById(id);
    assertThat(reader.callCount()).as("swift's entry must survive acme's eviction").isEqualTo(2);

    TenantContext.setTenantId("acme");
    reader.getById(id);
    assertThat(reader.callCount()).as("acme's entry must be gone").isEqualTo(3);
  }

  @Test
  @DisplayName("a cache name that is not configured fails instead of using a default")
  void unknownCacheNameFails() {
    TenantContext.setTenantId("acme");

    // disableCreateOnMissingCache() turns an annotation typo into a loud failure rather than a
    // silently unconfigured cache with the wrong serializer and no TTL.
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> reader.readFromUnknownCache())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does-not-exist");
  }

  @Configuration
  @Import(RedisCacheConfig.class)
  static class CacheTestConfig {

    @Bean
    RedisConnectionFactory redisConnectionFactory(
        @Value("${test.redis.host}") String host, @Value("${test.redis.port}") int port) {
      return new LettuceConnectionFactory(host, port);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
      return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }

    @Bean
    CountingTerminalReader countingTerminalReader() {
      return new CountingTerminalReader();
    }
  }

  /**
   * Stands in for {@code TerminalServiceImpl} with the same annotations, counting how often the
   * "database" was actually reached. The response varies by tenant so a cross-tenant hit is visible
   * in the value, not only in the call count.
   */
  static class CountingTerminalReader {

    private final AtomicInteger calls = new AtomicInteger();

    /**
     * Read through a method, never the field: this bean is wrapped in a CGLIB proxy by the cache
     * interceptor, and the proxy subclass's own copy of the field is never populated.
     */
    public int callCount() {
      return calls.get();
    }

    public void resetCalls() {
      calls.set(0);
    }

    @Cacheable(cacheNames = CacheNames.TERMINAL, key = "#id")
    public TerminalResponse getById(UUID id) {
      calls.incrementAndGet();
      String tenant = TenantContext.requireTenantId();
      return new TerminalResponse(
          id,
          "Port of " + tenant,
          "USNYC",
          "US",
          TerminalType.SEA_PORT,
          null,
          "1 Terminal Way",
          null,
          "Newark",
          "NJ",
          "07114",
          "US",
          OffsetDateTime.parse("2026-01-01T00:00:00Z"),
          null);
    }

    @CacheEvict(cacheNames = CacheNames.TERMINAL, allEntries = true)
    public void update(UUID id) {
      // The write itself is irrelevant here; the eviction is what is under test.
    }

    @Cacheable(cacheNames = "does-not-exist", key = "#root.methodName")
    public String readFromUnknownCache() {
      return "unreachable";
    }
  }
}
