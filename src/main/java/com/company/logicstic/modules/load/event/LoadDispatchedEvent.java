package com.company.logicstic.modules.load.event;

import java.util.UUID;

/**
 * Raised when a load moves from {@code Draft} to {@code Dispatched}.
 *
 * <p>The event exists to keep the dependency graph acyclic. {@code finance} already depends on
 * {@code load} (an invoice references the load it bills), so {@code load} calling {@code
 * InvoiceService} directly would create the package cycle that
 * docs/docs/development/engineering-conventions.md §2 forbids and that {@code
 * ArchitectureTest.SERVICES_MUST_BE_FREE_OF_CYCLES} fails on. Publishing an event inverts the
 * direction: {@code load} announces what happened, and whichever feature cares subscribes.
 *
 * @param loadId identifier of the dispatched load within the current tenant
 */
public record LoadDispatchedEvent(UUID loadId) {}
