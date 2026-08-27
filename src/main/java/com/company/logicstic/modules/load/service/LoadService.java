package com.company.logicstic.modules.load.service;

import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;
import java.util.UUID;

/**
 * Public API of the load feature — the shipment aggregate at the centre of the domain.
 *
 * <p>The four transition methods mirror the state machine in {@code docs/docs/business-spec.md}
 * §2.1: {@code Draft → Dispatched → PickedUp → Delivered}, with {@code Cancelled} reachable from
 * any non-terminal state. The legality of a transition is decided by the entity itself
 * (docs/docs/development/engineering-conventions.md §10); the service loads, calls the entity
 * method and saves.
 */
public interface LoadService extends CrudService<Load, LoadResponse, CreateLoadRequest> {

  /**
   * Searches loads with optional free-text, status and assignment filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<LoadResponse> search(
      String search,
      String status,
      UUID customerId,
      UUID truckId,
      UUID dispatcherId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);

  /**
   * Moves a load from {@code Draft} to {@code Dispatched} and stamps {@code dispatchedAt}.
   *
   * @throws com.company.logicstic.shared.exception.InvalidStateTransitionException when the load is
   *     not in {@code Draft}
   */
  LoadResponse dispatch(UUID id);

  /** Moves an authorized load from {@code Dispatched} to {@code PickedUp}. */
  LoadResponse pickUp(UUID id, UUID actorEmployeeId);

  /** Moves an authorized load from {@code PickedUp} to {@code Delivered}. */
  LoadResponse deliver(UUID id, UUID actorEmployeeId);

  /** Cancels a non-terminal load and stamps {@code cancelledAt}. */
  LoadResponse cancel(UUID id);
}
