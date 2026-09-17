package com.company.logicstic.role;

import com.company.logicstic.shared.persistence.CrudService;
import com.company.logicstic.shared.web.PagedResponse;

/** Public API of the role feature — the per-tenant RBAC roles an employee can be assigned. */
public interface RoleService extends CrudService<TenantRole, RoleResponse, CreateRoleRequest> {

  /**
   * Lists roles of the current tenant.
   *
   * @param page 1-based page number
   */
  PagedResponse<RoleResponse> list(int page, int pageSize);
}
