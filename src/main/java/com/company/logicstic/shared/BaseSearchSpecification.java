package com.company.logicstic.shared;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * A reusable JPA {@link Specification} factory for common search/filter patterns.
 *
 * <p>Provides static helper methods to build {@code WHERE} clauses with optional, null-safe
 * predicates — reducing repetitive {@code @Query} definitions in repositories and keeping filters
 * consistent across the application.
 */
public final class BaseSearchSpecification {

  private BaseSearchSpecification() {
    // prevent instantiation
  }

  /**
   * Creates a "search" predicate that matches multiple fields via {@code LIKE %value%}.
   *
   * @param <T> entity type
   * @param search the search term (ignored if null or blank)
   * @param fields the entity attribute paths to search against
   * @return an optional {@link Predicate} wrapped in a {@link Specification}
   */
  @SafeVarargs
  public static <T> Specification<T> searchIn(
      String search, java.util.function.Function<Root<T>, String>... fields) {
    return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      if (search == null || search.isBlank()) return null;

      List<Predicate> predicates = new ArrayList<>();
      String pattern = "%" + search.toLowerCase() + "%";
      for (var field : fields) {
        predicates.add(cb.like(cb.lower(cb.literal(field.apply(root))), pattern));
      }
      return cb.or(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Creates an equality predicate for an optional parameter.
   *
   * @param <T> entity type
   * @param field the entity attribute path
   * @param value the value to match (ignored if null)
   * @return an optional {@link Predicate} wrapped in a {@link Specification}
   */
  public static <T> Specification<T> equalsIfPresent(String field, Object value) {
    return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      if (value == null) return null;
      return cb.equal(root.get(field), value);
    };
  }

  /**
   * Creates an equality predicate on a nested association path (e.g. {@code customer.id}).
   *
   * @param <T> entity type
   * @param path dot-separated path to the target attribute
   * @param value the value to match (ignored if null)
   * @return an optional {@link Predicate} wrapped in a {@link Specification}
   */
  public static <T> Specification<T> joinEqualsIfPresent(String path, Object value) {
    return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      if (value == null) return null;
      String[] parts = path.split("\\.");
      jakarta.persistence.criteria.Path<Object> p = root.get(parts[0]);
      for (int i = 1; i < parts.length; i++) {
        p = p.get(parts[i]);
      }
      return cb.equal(p, value);
    };
  }

  /** Combines multiple specifications with AND logic, ignoring nulls. */
  @SafeVarargs
  public static <T> Specification<T> and(Specification<T>... specs) {
    return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      for (var spec : specs) {
        Predicate p = spec.toPredicate(root, query, cb);
        if (p != null) predicates.add(p);
      }
      return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
