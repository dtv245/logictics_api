package com.company.logicstic.modules.document.mapper;

import com.company.logicstic.modules.document.dto.response.DocumentResponse;
import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between {@link Document} entity and its DTOs.
 *
 * <p>Documents are created via file upload (not a standard DTO create flow), so this mapper only
 * provides {@code toResponse}. The view flattens the {@code uploadedBy}, {@code load}, {@code
 * truck}, and {@code employee} relations.
 */
@Mapper(config = MapperConfiguration.class)
public interface DocumentMapper {

  @Mapping(
      target = "uploadedById",
      expression =
          "java(document.getUploadedBy() != null ? document.getUploadedBy().getId() : null)")
  @Mapping(
      target = "uploadedByName",
      expression =
          "java(document.getUploadedBy() != null ? document.getUploadedBy().getFirstName() + \" \" + document.getUploadedBy().getLastName() : null)")
  @Mapping(
      target = "loadId",
      expression = "java(document.getLoad() != null ? document.getLoad().getId() : null)")
  @Mapping(
      target = "truckId",
      expression = "java(document.getTruck() != null ? document.getTruck().getId() : null)")
  @Mapping(
      target = "employeeId",
      expression = "java(document.getEmployee() != null ? document.getEmployee().getId() : null)")
  DocumentResponse toResponse(Document document);
}
