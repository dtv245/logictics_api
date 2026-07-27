package com.company.logicstic.modules.finance.mapper;

import com.company.logicstic.modules.finance.dto.request.CreateInvoiceRequest;
import com.company.logicstic.modules.finance.dto.response.InvoiceResponse;
import com.company.logicstic.modules.finance.entity.Invoice;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link Invoice} entity and its DTOs.
 *
 * <p>The view mapping flattens related entities (customer, employee, load) into their ID and name
 * fields for the API response.
 */
@Mapper(config = MapperConfiguration.class)
public interface InvoiceMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "number", ignore = true)
  @Mapping(
      target = "taxBehavior",
      expression = "java(req.taxBehavior() != null ? req.taxBehavior() : \"exclusive\")")
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "employee", ignore = true)
  @Mapping(target = "load", ignore = true)
  @Mapping(target = "sentAt", ignore = true)
  @Mapping(target = "sentToEmail", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Invoice toEntity(CreateInvoiceRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "number", ignore = true)
  @Mapping(
      target = "taxBehavior",
      expression = "java(req.taxBehavior() != null ? req.taxBehavior() : \"exclusive\")")
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "employee", ignore = true)
  @Mapping(target = "load", ignore = true)
  @Mapping(target = "sentAt", ignore = true)
  @Mapping(target = "sentToEmail", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreateInvoiceRequest req, @MappingTarget Invoice invoice);

  /**
   * Maps an invoice entity to its view. Note: customer/employee/load relations must be resolved
   * before calling this, or use a custom expression to handle null-safety.
   */
  @Mapping(
      target = "loadId",
      expression = "java(invoice.getLoad() != null ? invoice.getLoad().getId() : null)")
  @Mapping(
      target = "customerId",
      expression = "java(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null)")
  @Mapping(
      target = "customerName",
      expression = "java(invoice.getCustomer() != null ? invoice.getCustomer().getName() : null)")
  @Mapping(
      target = "employeeId",
      expression = "java(invoice.getEmployee() != null ? invoice.getEmployee().getId() : null)")
  @Mapping(
      target = "employeeName",
      expression =
          "java(invoice.getEmployee() != null ? invoice.getEmployee().getFirstName() + \" \" + invoice.getEmployee().getLastName() : null)")
  InvoiceResponse toResponse(Invoice invoice);
}
