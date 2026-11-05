package com.company.logicstic.integration.lark.event;

import com.company.logicstic.integration.lark.config.LarkProperties;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LarkEventConfigurationTest {
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LarkProperties.class)
  static class Binding {}

  @Test void bindsNewPropertiesWithoutChangingOldConstructorOrRequiringAuthCredentials() {
    var runner = new ApplicationContextRunner().withUserConfiguration(Binding.class)
        .withPropertyValues("app.lark.enabled=false");
    runner.run(context -> {
      assertNull(context.getStartupFailure());
      var properties = context.getBean(LarkProperties.class);
      assertTrue(properties.verificationToken().isEmpty());
      assertTrue(properties.encryptKey().isEmpty());
    });
    runner.withPropertyValues("app.lark.verification-token=" + TOKEN, "app.lark.encrypt-key=" + KEY)
        .run(context -> {
          assertNull(context.getStartupFailure());
          var properties = context.getBean(LarkProperties.class);
          assertTrue(TOKEN.equals(properties.verificationToken()));
          assertTrue(KEY.equals(properties.encryptKey()));
          assertFalse(properties.toString().contains(TOKEN));
          assertFalse(properties.toString().contains(KEY));
        });
  }

  @Test void dedicatedQueueDoesNotBecomeAnExecutorBeanForOtherAsyncServices() {
    new ApplicationContextRunner().withUserConfiguration(LarkEventConfiguration.class)
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertNotNull(context.getBean("larkEventExecutor"));
          assertTrue(context.getBeansOfType(Executor.class).isEmpty());
        });
  }

  @Test void boundedQueueRejectsInsteadOfRunningHandlerOnCaller() throws Exception {
    var release = new java.util.concurrent.CountDownLatch(1);
    var entered = new java.util.concurrent.CountDownLatch(2);
    try (var executor = new LarkEventConfiguration().larkEventExecutor()) {
      executor.initialize();
      Runnable blocking = () -> {
        entered.countDown();
        try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      };
      try {
        for (int i = 0; i < 102; i++) executor.execute(blocking);
        assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
        assertThrows(org.springframework.core.task.TaskRejectedException.class,
            () -> executor.execute(() -> fail("Must not run on the submitting thread")));
      } finally { release.countDown(); }
    }
  }
}
