package com.company.logicstic.modules.employee.mapper;

import com.company.logicstic.modules.employee.dto.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.EmployeeView;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link Employee} entity and its DTOs.
 *
 * <p>The {@code role} FK relation is intentionally ignored during entity mapping — the service
 * resolves it via {@link com.company.logicstic.repository.TenantRoleRepository} and sets it on the
 * entity before saving.
 */
@Mapper(config = MapperConfiguration.class)
public interface EmployeeMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "deviceToken", ignore = true)
  @Mapping(target = "stripeConnectedAccountId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Employee toEntity(CreateEmployeeRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "deviceToken", ignore = true)
  @Mapping(target = "stripeConnectedAccountId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreateEmployeeRequest req, @MappingTarget Employee employee);

  /** Flattens the {@code role} relation into {@code roleId} and {@code roleName}. */
  @Mapping(
      target = "roleId",
      expression = "java(employee.getRole() != null ? employee.getRole().getId() : null)")
  @Mapping(
      target = "roleName",
      expression = "java(employee.getRole() != null ? employee.getRole().getName() : null)")
  EmployeeView toView(Employee employee);
}
