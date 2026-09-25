package com.company.logicstic.customer;

import com.company.logicstic.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Maps between {@link Customer} entity and its DTOs. */
@Mapper(config = MapperConfiguration.class)
public interface CustomerMapper {

  /**
   * Maps all fields from the create request onto a new entity. The {@code isVatExempt} field
   * defaults to {@code false} when null.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(
      target = "isVatExempt",
      expression = "java(req.isVatExempt() != null ? req.isVatExempt() : false)")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Customer toEntity(CreateCustomerRequest req);

  /** Updates an existing entity with fields from the request. */
  @Mapping(target = "id", ignore = true)
  @Mapping(
      target = "isVatExempt",
      expression = "java(req.isVatExempt() != null ? req.isVatExempt() : false)")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreateCustomerRequest req, @MappingTarget Customer customer);

  /** Maps an entity to its view representation. */
  CustomerResponse toResponse(Customer customer);
}
