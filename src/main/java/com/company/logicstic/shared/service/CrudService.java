package com.company.logicstic.shared.service;

import java.util.UUID;

/**
 * The CRUD half of a feature's public service contract
 * (docs/docs/development/engineering-conventions.md §2).
 *
 * <p>Every feature exposes an interface — never a concrete class — so that callers depend on the
 * abstraction and cross-feature access has exactly one sanctioned door. This interface carries the
 * five operations that are identical in every feature; each feature's own {@code XService} extends
 * it and adds what is specific to that feature (searches, state transitions, reports).
 *
 * <p>A read-only or upload-only feature (documents, notifications) deliberately does <em>not</em>
 * extend this interface: declaring {@code create}/{@code update} it cannot honour would be a lie in
 * the type system. Those features declare only the operations they actually implement.
 *
 * @param <E> JPA entity type owned by the feature
 * @param <V> response DTO returned to controllers
 * @param <R> create/update request DTO
 */
public interface CrudService<E, V, R> {

  V getById(UUID id);

  /**
   * Returns the managed entity so another feature can use it as an association target.
   *
   * <p>This is the narrow lookup docs/docs/development/engineering-conventions.md §2 requires: when
   * feature A needs an entity of feature B it calls this instead of injecting {@code BRepository},
   * so B keeps ownership of its "not found" message, its tenant scoping and its fetch strategy.
   *
   * @throws com.company.logicstic.shared.exception.ResourceNotFoundException when no entity with
   *     that id exists in the current tenant
   */
  E getEntityById(UUID id);

  V create(R request);

  V update(UUID id, R request);

  void delete(UUID id);
}
