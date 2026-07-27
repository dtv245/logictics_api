package com.company.logicstic.modules.terminal.repository;

import com.company.logicstic.modules.terminal.entity.Terminal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for the terminal aggregate.
 *
 * <p>No {@code @Transactional} here — the transaction boundary is the service. Optional search
 * filters use the {@code :param IS NULL OR …} form so one query serves the whole search screen
 * instead of a Specification tree.
 */
public interface TerminalRepository extends JpaRepository<Terminal, UUID> {

  Optional<Terminal> findByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCase(String code);

  boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

  @Query(
      """
      SELECT t FROM Terminal t
      WHERE (:search IS NULL
             OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(t.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:type IS NULL OR t.type = :type)
        AND (:countryCode IS NULL OR UPPER(t.countryCode) = UPPER(CAST(:countryCode AS string)))
      """)
  Page<Terminal> search(
      @Param("search") String search,
      @Param("type") String type,
      @Param("countryCode") String countryCode,
      Pageable pageable);
}
