package com.company.logicstic.integration.lark.event;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LarkEventProcessingProperties.class)
public class LarkEventConfiguration {
  @Bean(name = "larkEventExecutor", initMethod = "initialize", destroyMethod = "close")
  public LarkEventExecutor larkEventExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("lark-event-");
    // Never run a slow handler on the HTTP thread when the queue is full.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return new LarkEventExecutor(executor);
  }
}
