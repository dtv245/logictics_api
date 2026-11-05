package com.company.logicstic.integration.lark.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LarkEventInboxTest {
  static LarkEventProcessingProperties processing(int attempts, int cleanupBatch) {
    return new LarkEventProcessingProperties(null, null, Duration.ofSeconds(2), Duration.ofMillis(20),
        Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofDays(7), attempts, 16, cleanupBatch);
  }

  @Test void aNewDispatcherRecoversAnAcceptedEventEvenIfTheOriginalProcessNeverRanIt() {
    var source = database();
    var clock = new MutableLarkEventClock();
    var settings = processing(3, 100);
    var inbox = new LarkEventDeduplicator(source, JSON, settings, clock);
    var oldQueue = new ArrayList<Runnable>();
    var newQueue = new ArrayList<Runnable>();
    var handler = mock(LarkEventHandler.class);
    when(handler.eventType()).thenReturn("example.changed");
    var original = dispatcher(inbox, oldQueue, handler, settings);
    original.accept("event-one", "example.changed", JSON.createObjectNode().put("value", "fixture"));
    assertEquals("PENDING", state(source, "event-one"));
    verify(handler, never()).handle(anyString(), any());
    var restarted = dispatcher(new LarkEventDeduplicator(source, JSON, settings, clock), newQueue, handler, settings);
    restarted.retryDueEvents();
    assertEquals(1, newQueue.size());
    newQueue.getFirst().run();
    oldQueue.getFirst().run();
    verify(handler, times(1)).handle(eq("event-one"), any());
    assertEquals("COMPLETE", state(source, "event-one"));
    assertNull(new JdbcTemplate(source).queryForObject("SELECT payload_json FROM lark_event_receipts", String.class));
  }

  @Test void expiredLeaseCanBeRecoveredAndStaleOwnerCannotCompleteOrFailTheNewDelivery() {
    var source = database();
    var clock = new MutableLarkEventClock();
    var settings = processing(3, 100);
    var inbox = new LarkEventDeduplicator(source, JSON, settings, clock);
    inbox.enqueue("event-one", "example.changed", JSON.createObjectNode());
    var old = inbox.claimDelivery("event-one").orElseThrow();
    assertTrue(inbox.claimDelivery("event-one").isEmpty());
    clock.advance(Duration.ofSeconds(2));
    var next = inbox.claimDelivery("event-one").orElseThrow();
    assertEquals(2, next.attempt());
    inbox.complete(old);
    inbox.failed(old);
    assertEquals("PROCESSING", state(source, "event-one"));
    clock.advance(Duration.ofSeconds(1));
    assertTrue(inbox.renew(next));
    clock.advance(Duration.ofSeconds(1));
    assertTrue(inbox.claimDelivery("event-one").isEmpty());
    inbox.complete(next);
    assertEquals("COMPLETE", state(source, "event-one"));
  }

  @Test void retryBackoffIsBoundedAndEventuallyDeadLettersWithoutDeletingPendingWork() {
    var source = database();
    var clock = new MutableLarkEventClock();
    var settings = processing(3, 100);
    var inbox = new LarkEventDeduplicator(source, JSON, settings, clock);
    inbox.enqueue("fails", "example.changed", JSON.createObjectNode());
    inbox.enqueue("pending", "example.changed", JSON.createObjectNode());
    var first = inbox.claimDelivery("fails").orElseThrow();
    inbox.failed(first);
    assertEquals("RETRY", state(source, "fails"));
    assertFalse(inbox.dueEvents().contains("fails"));
    clock.advance(Duration.ofSeconds(1));
    var second = inbox.claimDelivery("fails").orElseThrow();
    inbox.failed(second);
    clock.advance(Duration.ofSeconds(1));
    assertTrue(inbox.claimDelivery("fails").isEmpty());
    clock.advance(Duration.ofSeconds(1));
    inbox.failed(inbox.claimDelivery("fails").orElseThrow());
    assertEquals("DEAD", state(source, "fails"));
    assertFalse(inbox.dueEvents().contains("fails"));
    clock.advance(Duration.ofDays(1));
    assertEquals(0, inbox.cleanup());
    clock.advance(Duration.ofDays(6));
    assertEquals(1, inbox.cleanup());
    assertEquals("PENDING", state(source, "pending"));
    assertTrue(inbox.dueEvents().contains("pending"));
    assertFalse(inbox.enqueue("pending", "example.changed", JSON.createObjectNode().put("overwritten", true)));
  }

  @Test void concurrentNodesCreateOneDurablePayloadAndOnlyOneDeliveryLease() throws Exception {
    var source = database();
    var inbox = new LarkEventDeduplicator(source);
    var other = new LarkEventDeduplicator(source);
    var accepts = new ArrayList<Callable<Boolean>>();
    for (int i = 0; i < 16; i++) {
      var node = i % 2 == 0 ? inbox : other;
      accepts.add(() -> node.enqueue("event-one", "example.changed", JSON.createObjectNode()));
    }
    try (var pool = Executors.newFixedThreadPool(4)) {
      int accepted = 0;
      for (var result : pool.invokeAll(accepts)) if (result.get()) accepted++;
      assertEquals(1, accepted);
      var claims = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 16; i++) claims.add(() -> other.claimDelivery("event-one").isPresent());
      int leased = 0;
      for (var result : pool.invokeAll(claims)) if (result.get()) leased++;
      assertEquals(1, leased);
    }
  }

  @Test void cleanupIsBoundedAndCompletionRetainsOnlyMetadataFor24Hours() {
    var source = database();
    var clock = new MutableLarkEventClock();
    var settings = processing(3, 2);
    var inbox = new LarkEventDeduplicator(source, JSON, settings, clock);
    for (int i = 0; i < 3; i++) {
      String id = "done-" + i;
      inbox.enqueue(id, "example.changed", JSON.createObjectNode());
      inbox.complete(inbox.claimDelivery(id).orElseThrow());
    }
    inbox.enqueue("unfinished", "example.changed", JSON.createObjectNode());
    clock.advance(Duration.ofHours(24));
    assertEquals(2, inbox.cleanup());
    assertEquals(1, inbox.cleanup());
    assertEquals("PENDING", state(source, "unfinished"));
  }

  @Test void aFailedHandlerIsRetriedAfterItsDelayAndThenCompletes() {
    var source = database();
    var clock = new MutableLarkEventClock();
    var settings = processing(3, 100);
    var inbox = new LarkEventDeduplicator(source, JSON, settings, clock);
    var tasks = new ArrayList<Runnable>();
    var handler = mock(LarkEventHandler.class);
    when(handler.eventType()).thenReturn("example.changed");
    doThrow(new IllegalStateException("business data must stay out of logs"))
        .doNothing().when(handler).handle(anyString(), any());
    var dispatcher = dispatcher(inbox, tasks, handler, settings);
    dispatcher.accept("event-one", "example.changed", JSON.createObjectNode());
    tasks.getFirst().run();
    assertEquals("RETRY", state(source, "event-one"));
    dispatcher.retryDueEvents();
    assertEquals(1, tasks.size());
    clock.advance(Duration.ofSeconds(1));
    dispatcher.retryDueEvents();
    assertEquals(2, tasks.size());
    tasks.get(1).run();
    assertEquals("COMPLETE", state(source, "event-one"));
    verify(handler, times(2)).handle(eq("event-one"), any());
  }

  static LarkEventDispatcher dispatcher(LarkEventDeduplicator inbox, List<Runnable> tasks,
      LarkEventHandler handler, LarkEventProcessingProperties settings) {
    var executor = mock(LarkEventExecutor.class);
    doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(executor).execute(any());
    return new LarkEventDispatcher(inbox, executor, List.of(handler), LarkEventControllerTest.scope(), JSON, settings);
  }

  static String state(javax.sql.DataSource source, String id) {
    return new JdbcTemplate(source).queryForObject("SELECT status FROM lark_event_receipts WHERE event_id=?", String.class, id);
  }
}
