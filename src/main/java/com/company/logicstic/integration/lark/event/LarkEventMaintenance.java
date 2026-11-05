package com.company.logicstic.integration.lark.event;

import com.company.logicstic.integration.lark.config.LarkProperties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Dedicated lifecycle; does not enable scheduling or replace other @Async pools. */
@Component
public class LarkEventMaintenance implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(LarkEventMaintenance.class);
  private final LarkEventDispatcher dispatcher;
  private final LarkEventProcessingProperties processing;
  private final LarkProperties lark;
  private ScheduledExecutorService scheduler;

  public LarkEventMaintenance(LarkEventDispatcher dispatcher, LarkEventProcessingProperties processing,
      LarkProperties lark) {
    this.dispatcher = dispatcher;
    this.processing = processing;
    this.lark = lark;
  }

  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    if (scheduler != null || (!StringUtils.hasText(lark.verificationToken()) && !StringUtils.hasText(lark.encryptKey()))) return;
    scheduler = Executors.newScheduledThreadPool(2, runnable -> {
      var thread = new Thread(runnable, "lark-event-maintenance");
      thread.setDaemon(true);
      return thread;
    });
    schedule(dispatcher::retryDueEvents, processing.pollInterval().toMillis());
    schedule(dispatcher::renewLeases, Math.max(1, processing.leaseDuration().toMillis() / 3));
    schedule(dispatcher::cleanup, TimeUnit.MINUTES.toMillis(1));
  }

  private void schedule(Runnable task, long intervalMillis) {
    scheduler.scheduleWithFixedDelay(() -> {
      try { task.run(); }
      catch (RuntimeException unavailable) { log.error("Lark event maintenance unavailable"); }
    }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  @jakarta.annotation.PreDestroy
  @Override public synchronized void close() {
    if (scheduler != null) scheduler.shutdownNow();
  }
}
