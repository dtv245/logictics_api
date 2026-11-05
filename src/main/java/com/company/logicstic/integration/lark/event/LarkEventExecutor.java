package com.company.logicstic.integration.lark.event;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Own pool; intentionally not an Executor bean, so existing @Async defaults stay intact. */
public class LarkEventExecutor implements AutoCloseable {
  private final ThreadPoolTaskExecutor executor;

  LarkEventExecutor(ThreadPoolTaskExecutor executor) {
    this.executor = executor;
  }

  public void initialize() { executor.initialize(); }
  public void execute(Runnable task) { executor.execute(task); }
  @Override public void close() { executor.shutdown(); }
}
