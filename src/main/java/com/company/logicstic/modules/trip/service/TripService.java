package com.company.logicstic.modules.trip.service;

import com.company.logicstic.modules.trip.dto.request.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.response.TripResponse;
import com.company.logicstic.modules.trip.entity.Trip;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;
import java.util.UUID;

/**
 * Public API of the trip feature — an ordered chain of pickup and drop-off stops served by one
 * truck.
 *
 * <p>Transitions follow {@code docs/docs/business-spec.md} §2.2: {@code Draft → Dispatched →
 * InTransit → Completed}, cancellable while not yet completed. Cancelling a trip cascades a cancel
 * to the loads of its stops.
 */
public interface TripService extends CrudService<Trip, TripResponse, CreateTripRequest> {

  /**
   * Searches trips with optional free-text, status and truck filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<TripResponse> search(
      String search,
      String status,
      UUID truckId,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);

  /** Moves a trip from {@code Draft} to {@code Dispatched} and stamps {@code dispatchedAt}. */
  TripResponse dispatch(UUID id);

  /** Completes a trip once every drop-off stop has been served. */
  TripResponse complete(UUID id);

  /** Cancels a trip that has not completed, cascading the cancel to its loads. */
  TripResponse cancel(UUID id);
}
