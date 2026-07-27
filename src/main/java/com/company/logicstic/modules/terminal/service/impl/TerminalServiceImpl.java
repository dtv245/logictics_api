package com.company.logicstic.modules.terminal.service.impl;

import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.modules.terminal.mapper.TerminalMapper;
import com.company.logicstic.modules.terminal.repository.TerminalRepository;
import com.company.logicstic.modules.terminal.service.TerminalService;
import com.company.logicstic.shared.common.CacheNames;
import com.company.logicstic.shared.common.Constants;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the terminal feature, and the reference implementation of {@code
 * docs/docs/development/engineering-conventions.md}:
 *
 * <ul>
 *   <li>Constructor injection via {@code @RequiredArgsConstructor}, every field {@code final}.
 *   <li>{@code @Transactional(readOnly = true)} at class level, {@code @Transactional} on writes —
 *       the boundary lives here, never in the controller or the repository.
 *   <li>Failures throw the shared {@code ApiException} subtypes; the service never builds a {@code
 *       ResponseEntity} and never returns {@code null}.
 *   <li>Normalisation lives on the entity ({@link Terminal#normalise()}); the service orchestrates.
 *   <li>1-based wire pagination is converted here, in one place.
 * </ul>
 *
 * <p>Unlike the older services in this codebase, this one implements an interface instead of
 * extending {@code AbstractBaseService}: callers depend on the abstraction, and the CRUD template
 * is explicit enough to read without jumping to a base class.
 *
 * <p>{@code @Profile("!nodb")} matches the existing convention for repository-backed beans so the
 * {@code nodb} context test keeps working. It is a workaround for the missing slice tests, not a
 * pattern to spread — see the refactor plan.
 */
@Slf4j
@Profile("!nodb")
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerminalServiceImpl implements TerminalService {

  private final TerminalRepository terminalRepository;
  private final TerminalMapper terminalMapper;

  @Override
  public PagedResponse<TerminalResponse> search(
      String search,
      TerminalType type,
      String countryCode,
      int page,
      int pageSize,
      String orderBy,
      boolean descending) {
    PageRequest pageable = pageRequest(page, pageSize, orderBy, descending);
    return PagedResponse.from(
        terminalRepository
            .search(
                blankToNull(search),
                type == null ? null : type.dbValue(),
                blankToNull(countryCode),
                pageable)
            .map(terminalMapper::toResponse));
  }

  @Override
  @Cacheable(cacheNames = CacheNames.TERMINAL, key = "#id")
  public TerminalResponse getById(UUID id) {
    return terminalMapper.toResponse(getEntityById(id));
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.TERMINAL, allEntries = true)
  public TerminalResponse create(CreateTerminalRequest request) {
    Terminal terminal = terminalMapper.toEntity(request);
    terminal.normalise();
    requireUniqueCode(terminal.getCode(), null);

    Terminal saved = terminalRepository.save(terminal);
    log.info("Terminal created id={} code={}", saved.getId(), saved.getCode());
    return terminalMapper.toResponse(saved);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.TERMINAL, allEntries = true)
  public TerminalResponse update(UUID id, CreateTerminalRequest request) {
    Terminal terminal = getEntityById(id);
    terminalMapper.updateEntity(request, terminal);
    terminal.normalise();
    requireUniqueCode(terminal.getCode(), id);

    // No explicit save(): the entity is managed in this transaction and flushed on commit.
    log.info("Terminal updated id={} code={}", id, terminal.getCode());
    return terminalMapper.toResponse(terminal);
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = CacheNames.TERMINAL, allEntries = true)
  public void delete(UUID id) {
    Terminal terminal = getEntityById(id);
    try {
      terminalRepository.delete(terminal);
      terminalRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      // Loads and containers reference terminals with ON DELETE RESTRICT / SET NULL. Translate the
      // driver error so the client gets an actionable message instead of a constraint name.
      throw new ConflictException(
          "TERMINAL_IN_USE",
          "Terminal '" + terminal.getCode() + "' is referenced by loads or containers");
    }
    log.info("Terminal deleted id={} code={}", id, terminal.getCode());
  }

  @Override
  public Terminal getEntityById(UUID id) {
    return terminalRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + id));
  }

  /**
   * Rejects a duplicate UN/LOCODE with a 409 before hitting the database. The unique index remains
   * the source of truth — a concurrent insert surfaces as {@link DataIntegrityViolationException},
   * which the global handler also maps to 409.
   *
   * @param excludedId id to ignore during an update, {@code null} on create
   */
  private void requireUniqueCode(String code, UUID excludedId) {
    boolean duplicate =
        excludedId == null
            ? terminalRepository.existsByCodeIgnoreCase(code)
            : terminalRepository.existsByCodeIgnoreCaseAndIdNot(code, excludedId);
    if (duplicate) {
      throw new ConflictException("Terminal with code '" + code + "' already exists");
    }
  }

  /** Converts 1-based wire pagination to Spring Data's 0-based {@link PageRequest}. */
  private static PageRequest pageRequest(
      int page, int pageSize, String orderBy, boolean descending) {
    String sortField =
        orderBy == null || orderBy.isBlank() ? Constants.DEFAULT_SORT_FIELD_NAME : orderBy;
    Sort sort = descending ? Sort.by(sortField).descending() : Sort.by(sortField).ascending();
    return PageRequest.of(page - 1, pageSize, sort);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
