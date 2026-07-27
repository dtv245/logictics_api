package com.company.logicstic.modules.role.service;

import com.company.logicstic.modules.role.dto.request.CreateRoleRequest;
import com.company.logicstic.modules.role.dto.response.RoleResponse;
import com.company.logicstic.modules.role.entity.TenantRole;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.CrudService;

/** Public API of the role feature — the per-tenant RBAC roles an employee can be assigned. */
public interface RoleService extends CrudService<TenantRole, RoleResponse, CreateRoleRequest> {

  /**
   * Lists roles of the current tenant.
   *
   * @param page 1-based page number
   */
  PagedResponse<RoleResponse> list(int page, int pageSize);
}
