package com.company.logicstic.ai.event;

import com.company.logicstic.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class LoadEventDebouncer {

    private final AiProperties properties;
    private final ApplicationEventPublisher publisher;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<UUID, DebounceTask> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> loadLocks = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public LoadEventDebouncer(AiProperties properties, ApplicationEventPublisher publisher) {
        this(properties, publisher, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-event-debouncer");
            t.setDaemon(true);
            return t;
        }));
    }

    LoadEventDebouncer(AiProperties properties, ApplicationEventPublisher publisher, ScheduledExecutorService scheduler) {
        this.properties = properties;
        this.publisher = publisher;
        this.scheduler = scheduler;
    }

    public void onEvent(UUID loadId, String tenantId, String source, Set<String> fields) {
        if (loadId == null) return;
        Object lock = loadLocks.computeIfAbsent(loadId, k -> new Object());
        synchronized (lock) {
            activeTasks.compute(loadId, (id, existingTask) -> {
                Set<String> mergedFields = new HashSet<>(fields != null ? fields : Set.of());
                if (existingTask != null) {
                    existingTask.future().cancel(false);
                    mergedFields.addAll(existingTask.fields());
                }
                long delay = properties.debounceSeconds();
                ScheduledFuture<?> future = scheduler.schedule(
                    () -> dispatch(id, tenantId, source, mergedFields),
                    delay,
                    TimeUnit.SECONDS
                );
                return new DebounceTask(tenantId, source, mergedFields, future);
            });
        }
    }

    public void flush(UUID loadId) {
        if (loadId == null) return;
        Object lock = loadLocks.computeIfAbsent(loadId, k -> new Object());
        DebounceTask task;
        synchronized (lock) {
            task = activeTasks.remove(loadId);
        }
        if (task != null) {
            task.future().cancel(false);
            dispatch(loadId, task.tenantId(), task.source(), task.fields());
        }
    }

    private void dispatch(UUID loadId, String tenantId, String source, Set<String> fields) {
        try {
            activeTasks.remove(loadId);
            LoadChangedInternalEvent event = new LoadChangedInternalEvent(
                loadId, tenantId, source, fields, Instant.now()
            );
            log.info("Dispatching debounced LoadChangedInternalEvent loadId={} tenantId={} source={} fieldsCount={}",
                loadId, tenantId, source, fields.size());
            publisher.publishEvent(event);
        } catch (Exception e) {
            log.error("Failed to dispatch LoadChangedInternalEvent for loadId={}", loadId, e);
        }
    }

    private record DebounceTask(String tenantId, String source, Set<String> fields, ScheduledFuture<?> future) {}
}
