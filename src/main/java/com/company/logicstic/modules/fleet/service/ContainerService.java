package com.company.logicstic.modules.fleet.service;

import com.company.logicstic.modules.fleet.entity.Container;
import java.util.UUID;

/**
 * Public API of the intermodal container feature.
 *
 * <p>Only the lookup is exposed today because the container lifecycle (ISO 6346 state machine,
 * {@code docs/docs/business-spec.md} §2.4) has no endpoints yet. It exists so that {@code load} can
 * attach a container without injecting {@code ContainerRepository}, which
 * docs/docs/development/engineering-conventions.md §2 forbids — the not-found message and the
 * tenant scoping stay with the owning feature.
 */
public interface ContainerService {

  /**
   * Returns the managed container for use as an association target.
   *
   * @throws com.company.logicstic.shared.exception.ResourceNotFoundException when no container with
   *     that id exists in the current tenant
   */
  Container getEntityById(UUID id);
}
