package com.company.logicstic.modules.terminal.service;

import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;

/**
 * Public API of the terminal feature — the UN/LOCODE directory of sea ports, rail terminals, inland
 * depots, air cargo facilities and border crossings.
 *
 * <p>This interface is the only thing other features may depend on. They must not inject {@link
 * com.company.logicstic.modules.terminal.repository.TerminalRepository} or mutate {@link Terminal}
 * directly — see {@code docs/docs/development/engineering-conventions.md} §2. A load referencing an
 * origin or destination terminal obtains the association through {@code getEntityById}, so the
 * not-found error, the tenant scoping and the fetch strategy stay in one place.
 */
public interface TerminalService
    extends CrudService<Terminal, TerminalResponse, CreateTerminalRequest> {

  /**
   * Searches terminals with optional free-text, type and country filters.
   *
   * @param page 1-based page number
   */
  PagedResponse<TerminalResponse> search(
      String search,
      TerminalType type,
      String countryCode,
      int page,
      int pageSize,
      String orderBy,
      boolean descending);
}
