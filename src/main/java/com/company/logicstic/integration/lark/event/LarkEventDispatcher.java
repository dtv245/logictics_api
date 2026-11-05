package com.company.logicstic.integration.lark.event;

import com.company.logicstic.exception.ApiException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LarkEventDispatcher {
  private static final Logger log = LoggerFactory.getLogger(LarkEventDispatcher.class);
  private final LarkEventDeduplicator deduplicator;
  private final LarkEventExecutor executor;
  private final List<LarkEventHandler> handlers;
  private final LarkEventTenantScope tenantScope;
  private final ObjectMapper json;
  private final LarkEventProcessingProperties processing;
  private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
  private final java.util.Map<String, LarkEventDeduplicator.Delivery> active = new ConcurrentHashMap<>();

  @Autowired
  public LarkEventDispatcher(LarkEventDeduplicator deduplicator,
      @Qualifier("larkEventExecutor") LarkEventExecutor executor, List<LarkEventHandler> handlers,
      LarkEventTenantScope tenantScope, ObjectMapper json, LarkEventProcessingProperties processing) {
    this.deduplicator = deduplicator;
    this.executor = executor;
    this.handlers = List.copyOf(handlers);
    this.tenantScope = tenantScope;
    this.json = json;
    this.processing = processing;
  }

  public LarkEventDispatcher(LarkEventDeduplicator deduplicator, LarkEventExecutor executor,
      List<LarkEventHandler> handlers, LarkEventTenantScope tenantScope) {
    this(deduplicator, executor, handlers, tenantScope, JsonMapper.builder().build(), LarkEventProcessingProperties.defaults());
  }

  public void accept(String eventId, String eventType, JsonNode event) {
    boolean created;
    try {
      created = tenantScope.within(() -> deduplicator.enqueue(eventId, eventType, event));
    } catch (ApiException failure) {
      throw failure;
    } catch (RuntimeException persistenceFailure) {
      // Do not expose JDBC parameters or provider payloads via exception logging.
      throw LarkEventSecurity.unavailable();
    }
    // Submission is only a latency optimization. A full/stopped pool never loses
    // a committed event; the maintenance poller will submit it after recovery.
    if (created) submit(eventId);
  }

  public void retryDueEvents() {
    List<String> due = tenantScope.within(deduplicator::dueEvents);
    for (String eventId : due) submit(eventId);
  }

  public void renewLeases() {
    tenantScope.within(() -> {
      for (var delivery : active.values()) deduplicator.renew(delivery);
      return null;
    });
  }

  public void cleanup() { tenantScope.within(deduplicator::cleanup); }

  private void submit(String eventId) {
    if (!scheduled.add(eventId)) return;
    try {
      executor.execute(() -> process(eventId));
    } catch (RejectedExecutionException rejected) {
      scheduled.remove(eventId);
    }
  }

  private void process(String eventId) {
    try {
      tenantScope.within(() -> {
        var claimed = deduplicator.claimDelivery(eventId);
        if (claimed.isEmpty()) return null;
        var delivery = claimed.get();
        active.put(eventId, delivery);
        try {
          if (delivery.attempt() > processing.maxAttempts()) {
            deduplicator.failed(delivery);
            return null;
          }
          JsonNode event = json.readTree(delivery.payloadJson());
          for (var handler : handlers) {
            if (handler.eventType().equals(delivery.eventType())) {
              handler.handle(eventId, event);
            }
          }
          deduplicator.complete(delivery);
        } catch (RuntimeException failure) {
          log.error("Lark event handler failed event_id={}", safeLog(eventId));
          deduplicator.failed(delivery);
        } finally {
          active.remove(eventId, delivery);
        }
        return null;
      });
    } catch (RuntimeException failure) {
      log.error("Lark event dispatch failed event_id={}", safeLog(eventId));
    } finally {
      scheduled.remove(eventId);
    }
  }

  static String safeLog(String value) {
    if (value == null) return "";
    return value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "_").substring(0, Math.min(value.length(), 255));
  }
}
