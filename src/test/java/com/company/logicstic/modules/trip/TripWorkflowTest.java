package com.company.logicstic.modules.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.notification.event.TenantNotificationEvent;
import com.company.logicstic.modules.trip.dto.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.TripStopRequest;
import com.company.logicstic.modules.trip.entity.Trip;
import com.company.logicstic.modules.trip.mapper.TripMapper;
import com.company.logicstic.modules.trip.repository.TripRepository;
import com.company.logicstic.modules.trip.service.TripService;
import com.company.logicstic.shared.exception.InvalidStateTransitionException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TripWorkflowTest {

  @Test
  void managesStopsAndLifecycleWithoutCrudStatusBypass() {
    UUID tripId = UUID.randomUUID();
    UUID loadId = UUID.randomUUID();
    Load load = new Load();
    load.setId(loadId);
    AtomicReference<Trip> stored = new AtomicReference<>();
    AtomicReference<TenantNotificationEvent> published = new AtomicReference<>();
    TripRepository trips =
        proxy(
            TripRepository.class,
            (method, args) -> {
              if (method.equals("save")) {
                Trip trip = (Trip) args[0];
                trip.setId(tripId);
                stored.set(trip);
                return trip;
              }
              if (method.equals("findById")) {
                return Optional.of(stored.get());
              }
              throw new AssertionError("Unexpected TripRepository call: " + method);
            });
    LoadRepository loads =
        proxy(
            LoadRepository.class,
            (method, args) -> {
              if (method.equals("findById")) {
                return Optional.of(load);
              }
              throw new AssertionError("Unexpected LoadRepository call: " + method);
            });
    TripService service =
        new TripService(
            trips,
            unused(TruckRepository.class),
            loads,
            Mappers.getMapper(TripMapper.class),
            event -> published.set((TenantNotificationEvent) event));

    var created = service.create(request("draft", loadId));
    assertThat(created.status()).isEqualTo("draft");
    assertThat(created.stops()).hasSize(1);
    assertThat(stored.get().getStops().getFirst().getTrip()).isSameAs(stored.get());

    assertThatThrownBy(() -> service.update(tripId, request("completed", loadId)))
        .isInstanceOf(InvalidStateTransitionException.class);
    assertThat(stored.get().getStatus()).isEqualTo("draft");

    var dispatched = service.dispatch(tripId);
    assertThat(dispatched.status()).isEqualTo("dispatched");
    assertThat(dispatched.dispatchedAt()).isNotNull();
    assertThat(published.get().sourceId()).isEqualTo(tripId);
    assertThat(published.get().message()).contains("dispatched");

    var completed = service.complete(tripId);
    assertThat(completed.status()).isEqualTo("completed");
    assertThat(completed.completedAt()).isNotNull();
    assertThat(published.get().message()).contains("completed");
  }

  private CreateTripRequest request(String status, UUID loadId) {
    var stop =
        new TripStopRequest(
            "pickup",
            0,
            loadId,
            "1 Main St",
            null,
            "Hanoi",
            "HN",
            "10000",
            "VN",
            21.0,
            105.0);
    return new CreateTripRequest("Trip", 10.0, status, null, List.of(stop));
  }

  private static <T> T unused(Class<T> type) {
    return proxy(
        type,
        (method, args) -> {
          throw new AssertionError("Unexpected " + type.getSimpleName() + " call: " + method);
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, RepositoryCall call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> call.invoke(method.getName(), args));
  }

  @FunctionalInterface
  private interface RepositoryCall {
    Object invoke(String method, Object[] args);
  }
}
