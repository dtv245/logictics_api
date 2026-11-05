package com.company.logicstic.integration.lark.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LarkEventControllerTest {
  static LarkEventTenantScope scope() {
    return new LarkEventTenantScope(properties(TOKEN, ""), false,
        new StaticListableBeanFactory().getBeanProvider(com.company.logicstic.service.TenantDataSourceService.class));
  }

  private MockMvc mvc(LarkEventDispatcher dispatcher, String token) {
    return MockMvcBuilders.standaloneSetup(new LarkEventController(
        new LarkEventSecurity(properties(token, ""), JSON), dispatcher)).build();
  }

  @Test void verificationReturnsOnlyExactChallengeAndDoesNotDispatch() throws Exception {
    var dispatcher = mock(LarkEventDispatcher.class);
    mvc(dispatcher, TOKEN).perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
        .content(challenge(TOKEN))).andExpect(status().isOk())
        .andExpect(content().json("{\"challenge\":\"challenge-test-đúng\"}"));
    verifyNoInteractions(dispatcher);
  }

  @Test void rejectsIncorrectTokenAndEmptyConfigurationOverHttp() throws Exception {
    var dispatcher = mock(LarkEventDispatcher.class);
    mvc(dispatcher, TOKEN).perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
        .content(challenge("incorrect"))).andExpect(status().isUnauthorized());
    mvc(dispatcher, "").perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
        .content(challenge(TOKEN))).andExpect(status().isServiceUnavailable());
    verifyNoInteractions(dispatcher);
  }

  @Test void invalidEnvelopeDoesNotReachDeduplication() throws Exception {
    var dispatcher = mock(LarkEventDispatcher.class);
    var payload = (tools.jackson.databind.node.ObjectNode) JSON.readTree(event("event-one", TOKEN));
    payload.put("schema", "1.0");
    mvc(dispatcher, TOKEN).perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
        .content(payload.toString())).andExpect(status().isBadRequest());
    verifyNoInteractions(dispatcher);
  }

  @Test void duplicateEventOnlyEnqueuesAndHandlesOnceAndAckDoesNotExecuteHandler() throws Exception {
    var tasks = new ArrayList<Runnable>();
    var executor = mock(LarkEventExecutor.class);
    doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(executor).execute(any());
    var handler = mock(LarkEventHandler.class);
    when(handler.eventType()).thenReturn(LarkBaseRecordChangedHandler.EVENT_TYPE);
    var dispatcher = new LarkEventDispatcher(new LarkEventDeduplicator(database()), executor, List.of(handler), scope());
    var mvc = mvc(dispatcher, TOKEN);
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
          .content(event("same-event", TOKEN))).andExpect(status().isOk())
          .andExpect(content().json("{\"msg\":\"ok\"}"));
    }
    assertEquals(1, tasks.size());
    verify(handler, never()).handle(anyString(), any());
    tasks.getFirst().run();
    verify(handler, times(1)).handle(eq("same-event"), any());
  }

  @Test void fullQueueAcknowledgesDurableAcceptanceAndWorkerRecoversIt() throws Exception {
    var executor = mock(LarkEventExecutor.class);
    doThrow(new RejectedExecutionException()).when(executor).execute(any());
    var source = database();
    var dispatcher = new LarkEventDispatcher(new LarkEventDeduplicator(source), executor, List.of(), scope());
    var mvc = mvc(dispatcher, TOKEN);
    mvc.perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
        .content(event("retry-event", TOKEN))).andExpect(status().isOk());
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
    assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM lark_event_receipts WHERE event_id='retry-event'", String.class));
    var tasks = new ArrayList<Runnable>();
    var recoveredExecutor = mock(LarkEventExecutor.class);
    doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(recoveredExecutor).execute(any());
    var recovered = new LarkEventDispatcher(new LarkEventDeduplicator(source), recoveredExecutor, List.of(), scope());
    recovered.retryDueEvents();
    assertEquals(1, tasks.size());
    tasks.getFirst().run();
    assertEquals("COMPLETE", jdbc.queryForObject("SELECT status FROM lark_event_receipts WHERE event_id='retry-event'", String.class));
    mvc.perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
        .content(event("retry-event", TOKEN))).andExpect(status().isOk());
    verify(executor, times(1)).execute(any());
  }

  @Test void realWorkerCanBlockWhileHttpReturns200() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var finished = new CountDownLatch(1);
    LarkEventHandler handler = new LarkEventHandler() {
      public String eventType() { return LarkBaseRecordChangedHandler.EVENT_TYPE; }
      public void handle(String id, tools.jackson.databind.JsonNode event) {
        entered.countDown();
        try { release.await(10, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { finished.countDown(); }
      }
    };
    try (var executor = new LarkEventConfiguration().larkEventExecutor();
        var http = Executors.newSingleThreadExecutor()) {
      executor.initialize();
      var dispatcher = new LarkEventDispatcher(new LarkEventDeduplicator(database()), executor, List.of(handler), scope());
      var mvc = mvc(dispatcher, TOKEN);
      try {
        var response = http.submit(() -> mvc.perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON)
            .content(event("slow-event", TOKEN))).andReturn().getResponse().getStatus());
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertEquals(200, response.get(3, TimeUnit.SECONDS));
        assertEquals(1, finished.getCount());
      } finally { release.countDown(); }
      assertTrue(finished.await(3, TimeUnit.SECONDS));
    }
  }

  @Test void handlerFailureIsContainedAndDoesNotReprocessDuplicate() throws Exception {
    var tasks = new ArrayList<Runnable>();
    var executor = mock(LarkEventExecutor.class);
    doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(executor).execute(any());
    var handler = mock(LarkEventHandler.class);
    when(handler.eventType()).thenReturn(LarkBaseRecordChangedHandler.EVENT_TYPE);
    doThrow(new IllegalStateException("sensitive business payload must not be logged"))
        .when(handler).handle(anyString(), any());
    var dispatcher = new LarkEventDispatcher(new LarkEventDeduplicator(database()), executor, List.of(handler), scope());
    dispatcher.accept("failed-event", LarkBaseRecordChangedHandler.EVENT_TYPE, JSON.createObjectNode());
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LarkEventDispatcher.class);
    var captured = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    captured.start();
    logger.addAppender(captured);
    try {
      assertDoesNotThrow(() -> tasks.getFirst().run());
      assertEquals(1, captured.list.size());
      assertEquals("Lark event handler failed event_id=failed-event", captured.list.getFirst().getFormattedMessage());
      assertNull(captured.list.getFirst().getThrowableProxy());
    } finally {
      logger.detachAppender(captured);
      captured.stop();
    }
    dispatcher.accept("failed-event", LarkBaseRecordChangedHandler.EVENT_TYPE, JSON.createObjectNode());
    assertEquals(1, tasks.size());
  }
}
