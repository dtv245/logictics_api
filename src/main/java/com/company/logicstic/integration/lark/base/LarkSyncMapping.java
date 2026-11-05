package com.company.logicstic.integration.lark.base;

import com.company.logicstic.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LarkSyncMapping {
  public record Field(String key, String source, int type, boolean required, String owner,
      String fieldEnv, String proposal, String converter, List<String> options) {}
  public record Domain(String tableEnv, List<Field> fields) {}
  public record Catalog(int version, Map<String, Domain> domains) {}
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final Environment env;
  private final Catalog catalog;

  public LarkSyncMapping(Environment env) {
    this.env = env;
    try (var stream = new ClassPathResource("lark-base-mapping.json").getInputStream()) {
      catalog = JSON.readValue(stream, Catalog.class);
    } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load Lark mapping"); }
  }
  public Domain domain(String name) {
    var domain = catalog.domains().get(name);
    if (domain == null) throw error("LARK_SYNC_DOMAIN_INVALID");
    return domain;
  }
  public String setting(String name) { return env.getProperty(name, "").trim(); }
  public static boolean placeholder(String value) {
    if (value == null || value.isBlank()) return true;
    String lower = value.toLowerCase(Locale.ROOT);
    return lower.contains("xxxx") || lower.contains("your-") || lower.contains("your_")
        || lower.contains("placeholder") || lower.startsWith("${") || lower.contains("changeme");
  }
  public String configured(String key) {
    String value = setting(key);
    if (placeholder(value)) throw error("LARK_BASE_CONFIG_REQUIRED:" + key);
    return value;
  }
  public String table(String domain) { return configured(domain(domain).tableEnv()); }
  public void validate(String domain) {
    if (!"sandbox".equals(setting("LARK_BASE_ENVIRONMENT"))
        || !"true".equalsIgnoreCase(setting("LARK_BASE_ENABLED"))) throw error("LARK_BASE_SANDBOX_REQUIRED");
    for (String key : List.of("LARK_APP_ID","LARK_APP_SECRET","LARK_BASE_APP_TOKEN")) configured(key);
    table(domain);
    var names = new HashSet<String>();
    for (var f : domain(domain).fields()) {
      String name = configured(f.fieldEnv());
      if (!names.add(name)) throw error("LARK_BASE_DUPLICATE_FIELD");
      if (!"TMS".equals(f.owner())) throw error("LARK_BASE_OWNER_UNSUPPORTED");
    }
    String zone = setting("LARK_BASE_DELIVERY_TIME_ZONE");
    if (!zone.isEmpty()) try { ZoneId.of(zone); } catch (java.time.DateTimeException bad) {
      throw error("LARK_BASE_TIME_ZONE_INVALID");
    }
  }
  public String name(Field field) { return configured(field.fieldEnv()); }
  public String identityField(String domain) {
    return name(domain(domain).fields().stream().filter(f -> "id".equals(f.key())).findFirst().orElseThrow());
  }
  public String fingerprint(String domain) {
    var settings = new TreeMap<String,Object>();
    settings.put("catalog", domain(domain));
    for (var f : domain(domain).fields()) settings.put(f.key(), name(f));
    settings.put("zone", setting("LARK_BASE_DELIVERY_TIME_ZONE"));
    return hash(settings);
  }
  public Map<String,Object> project(String domain, Object entity, boolean update, boolean deleted) {
    Map<String,Object> result = new TreeMap<>();
    for (var f : domain(domain).fields()) {
      Object value;
      if (f.source().equals("@lifecycle")) value = deleted ? "DELETED" : "PRESENT";
      else if (f.source().equals("@deliveryZone")) value = setting("LARK_BASE_DELIVERY_TIME_ZONE");
      else if (f.source().equals("@self")) value = entity;
      else try { value = PropertyAccessorFactory.forBeanPropertyAccess(entity).getPropertyValue(f.source()); }
      catch (org.springframework.beans.NullValueInNestedPathException missing) { value = null; }
      if (value != null) value = switch(f.converter()) {
        case "TEXT" -> value.toString();
        case "FULL_NAME" -> {
          var bean = PropertyAccessorFactory.forBeanPropertyAccess(value);
          yield (Objects.toString(bean.getPropertyValue("firstName"), "") + " "
              + Objects.toString(bean.getPropertyValue("lastName"), "")).trim();
        }
        case "UTC_MILLIS" -> ((OffsetDateTime)value).toInstant().toEpochMilli();
        default -> throw error("LARK_BASE_CONVERTER_INVALID");
      };
      if (value instanceof String text && text.isBlank()) value = null;
      if (value == null && f.required()) throw error("LARK_BASE_REQUIRED_FIELD:" + f.key());
      if (value != null || update) result.put(name(f), value == null && f.type() == 1 ? "" : value);
    }
    return result;
  }
  public Map<String,Object> normalize(String domain, Map<String,Object> values) {
    var result = new TreeMap<String,Object>();
    for (var f : domain(domain).fields()) {
      String name = name(f);
      Object v = values.get(name);
      if (f.type() == 1 && v instanceof List<?> list) {
        v = list.stream().map(x -> x instanceof Map<?,?> m ? Objects.toString(m.get("text"), "") : Objects.toString(x,""))
            .collect(java.util.stream.Collectors.joining());
      }
      if (v instanceof Number n) v = new java.math.BigDecimal(n.toString()).stripTrailingZeros();
      if (v != null && !(v instanceof String && ((String)v).isBlank())) result.put(name, v);
    }
    return result;
  }
  public boolean unknown(String domain, Map<String,Object> values) {
    Set<String> known = new HashSet<>();
    for (var f : domain(domain).fields()) known.add(name(f));
    return values.entrySet().stream().anyMatch(e -> !known.contains(e.getKey()) && e.getValue() != null
        && !e.getValue().toString().isBlank());
  }
  public static String json(Object value) { return JSON.writeValueAsString(value); }
  @SuppressWarnings("unchecked")
  public static Map<String,Object> read(String value) { return JSON.readValue(value, Map.class); }
  public static String hash(Object value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(json(value).getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.GeneralSecurityException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
  }
  public static ApiException error(String code) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, code);
  }
}

