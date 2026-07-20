package com.company.logicstic.shared;

import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abstract base service providing common CRUD template methods.
 *
 * <p>Subclasses extend this to inherit standard {@code getById}, {@code create}, {@code update},
 * and {@code delete} implementations, reducing boilerplate. The {@code search} method is left for
 * subclasses to define since filter signatures vary across domains.
 *
 * @param <E> JPA entity type
 * @param <V> View DTO type (returned to controllers)
 * @param <R> Create/update request DTO type
 */
@Transactional(readOnly = true)
public abstract class AbstractBaseService<E, V, R> {

  protected final JpaRepository<E, UUID> repository;
  private final Function<E, V> toView;
  private final Function<R, E> toEntity;
  private final BiConsumer<R, E> updateEntity;

  protected AbstractBaseService(
      JpaRepository<E, UUID> repository,
      Function<E, V> toView,
      Function<R, E> toEntity,
      BiConsumer<R, E> updateEntity) {
    this.repository = repository;
    this.toView = toView;
    this.toEntity = toEntity;
    this.updateEntity = updateEntity;
  }

  /** Convenience constructor when the mapper provides all three mapping functions. */
  protected AbstractBaseService(JpaRepository<E, UUID> repository, MapperFacade<E, V, R> mapper) {
    this(repository, mapper::toView, mapper::toEntity, mapper::updateEntity);
  }

  /** Retrieves an entity by ID and maps it to a view. */
  public V getById(UUID id) {
    return repository
        .findById(id)
        .map(toView)
        .orElseThrow(() -> new ResourceNotFoundException(entityName() + " not found: " + id));
  }

  /** Creates a new entity from the request, persists it, and returns the view. */
  @Transactional
  public V create(R request) {
    E entity = toEntity.apply(request);
    beforeCreate(entity, request);
    return toView.apply(repository.save(entity));
  }

  /** Updates an existing entity with data from the request. */
  @Transactional
  public V update(UUID id, R request) {
    E entity =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(entityName() + " not found: " + id));
    beforeMapUpdate(entity, request);
    updateEntity.accept(request, entity);
    beforeUpdate(entity, request);
    return toView.apply(repository.save(entity));
  }

  /** Deletes an entity by ID. */
  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException(entityName() + " not found: " + id);
    }
    repository.deleteById(id);
  }

  // ── Hooks for subclasses ──────────────────────────────────────────

  /** Human-readable entity name used in exception messages (default: simple class name). */
  protected String entityName() {
    // Infer from generic if possible; fallback to "Entity"
    return "Entity";
  }

  /**
   * Hook called before persisting a newly created entity. Override to resolve cross-module FK
   * references (e.g. setCustomer).
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
   * Hook called before persisting an updated entity. Override to re-resolve cross-module FK
   * references.
   */
  protected void beforeUpdate(E entity, R request) {
    // no-op by default
  }

  // ── Helper for consistent pagination ──────────────────────────────

  /** Creates a Spring Data {@link PageRequest} from 1-based page/pageSize and sort parameters. */
  protected static PageRequest pageRequest(
      int page, int pageSize, String orderBy, boolean descending) {
    Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
    return PageRequest.of(page - 1, pageSize, sort);
  }

  /** Wraps a JPA {@code Page} into a {@link PagedResponse}. */
  protected PagedResponse<V> toPagedResponse(org.springframework.data.domain.Page<E> page) {
    return PagedResponse.from(page.map(toView));
  }

  /** Interface for mappers that provide all three mapping operations. */
  public interface MapperFacade<E, V, R> {
    V toView(E entity);

    E toEntity(R request);

    void updateEntity(R request, E entity);
  }
}
