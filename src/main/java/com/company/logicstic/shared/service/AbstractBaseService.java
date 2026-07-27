package com.company.logicstic.shared.service;

import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable CRUD template shared by the feature service implementations.
 *
 * <p>A {@code XServiceImpl} extends this class <em>and</em> implements its feature's {@code
 * XService} interface: the interface is what callers depend on
 * (docs/docs/development/engineering-conventions.md §2), while this class is how the five identical
 * CRUD operations avoid being copied fifteen times. {@code search} is not here because filter
 * signatures differ per feature.
 *
 * <p>The transaction boundary lives here — {@code readOnly = true} at class level, {@code
 * Transactional} on the writes — so no subclass, controller or repository needs to declare one
 * (docs/docs/development/engineering-conventions.md §7).
 *
 * @param <E> JPA entity type
 * @param <V> response DTO type returned to controllers
 * @param <R> create/update request DTO type
 */
@Transactional(readOnly = true)
public abstract class AbstractBaseService<E, V, R> {

  protected final JpaRepository<E, UUID> repository;
  private final Function<E, V> toResponse;
  private final Function<R, E> toEntity;
  private final BiConsumer<R, E> updateEntity;

  protected AbstractBaseService(
      JpaRepository<E, UUID> repository,
      Function<E, V> toResponse,
      Function<R, E> toEntity,
      BiConsumer<R, E> updateEntity) {
    this.repository = repository;
    this.toResponse = toResponse;
    this.toEntity = toEntity;
    this.updateEntity = updateEntity;
  }

  /** Convenience constructor when the mapper provides all three mapping functions. */
  protected AbstractBaseService(JpaRepository<E, UUID> repository, MapperFacade<E, V, R> mapper) {
    this(repository, mapper::toResponse, mapper::toEntity, mapper::updateEntity);
  }

  /** Retrieves an entity by id and maps it to its response DTO. */
  public V getById(UUID id) {
    return toResponse.apply(getEntityById(id));
  }

  /**
   * Returns the managed entity itself, for another feature that needs it to satisfy an association.
   *
   * @param id identifier of the entity within the current tenant
   * @return the managed entity
   * @throws ResourceNotFoundException if no entity with that id exists in tenant scope
   */
  public E getEntityById(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(entityName() + " not found: " + id));
  }

  /** Creates a new entity from the request, persists it, and returns the response DTO. */
  @Transactional
  public V create(R request) {
    E entity = toEntity.apply(request);
    beforeCreate(entity, request);
    return toResponse.apply(repository.save(entity));
  }

  /** Updates an existing entity with data from the request. */
  @Transactional
  public V update(UUID id, R request) {
    E entity = getEntityById(id);
    beforeMapUpdate(entity, request);
    updateEntity.accept(request, entity);
    beforeUpdate(entity, request);
    return toResponse.apply(repository.save(entity));
  }

  /** Deletes an entity by id. */
  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException(entityName() + " not found: " + id);
    }
    repository.deleteById(id);
  }

  // ── Hooks for subclasses ──────────────────────────────────────────

  /** Human-readable entity name used in exception messages. */
  protected String entityName() {
    return "Entity";
  }

  /**
   * Hook called before persisting a newly created entity. Override to resolve cross-feature
   * associations through the owning feature's service.
   */
  protected void beforeCreate(E entity, R request) {
    // no-op by default
  }

  /**
   * Hook called before the update mapper mutates the managed entity. Override for validations that
   * must compare persisted values with the incoming request.
   */
  protected void beforeMapUpdate(E entity, R request) {
    // no-op by default
  }

  /**
   * Hook called before persisting an updated entity. Override to re-resolve cross-feature
   * associations.
   */
  protected void beforeUpdate(E entity, R request) {
    // no-op by default
  }

  // ── Helpers ───────────────────────────────────────────────────────

  /** Converts 1-based wire pagination to Spring Data's 0-based {@link PageRequest}. */
  protected static PageRequest pageRequest(
      int page, int pageSize, String orderBy, boolean descending) {
    Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
    return PageRequest.of(page - 1, pageSize, sort);
  }

  /** Wraps a JPA {@link Page} into a {@link PagedResponse} of response DTOs. */
  protected PagedResponse<V> toPagedResponse(Page<E> page) {
    return PagedResponse.from(page.map(toResponse));
  }

  /** Contract for mappers that provide all three mapping operations. */
  public interface MapperFacade<E, V, R> {
    V toResponse(E entity);

    E toEntity(R request);

    void updateEntity(R request, E entity);
  }
}
