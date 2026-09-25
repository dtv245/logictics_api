package com.company.logicstic.inspection;

import com.company.logicstic.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link LoadConditionReport} entity and its DTOs.
 *
 * <p>The {@code load} and {@code inspectedBy} FK relations are ignored during entity mapping — the
 * service resolves them via repositories before saving.
 */
@Mapper(config = MapperConfiguration.class)
public interface InspectionMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "load", ignore = true)
  @Mapping(target = "inspectedBy", ignore = true)
  @Mapping(target = "defects", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  LoadConditionReport toEntity(CreateInspectionRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "load", ignore = true)
  @Mapping(target = "inspectedBy", ignore = true)
  @Mapping(target = "defects", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreateInspectionRequest req, @MappingTarget LoadConditionReport report);

  @Mapping(
      target = "loadId",
      expression = "java(report.getLoad() != null ? report.getLoad().getId() : null)")
  @Mapping(
      target = "inspectedById",
      expression = "java(report.getInspectedBy() != null ? report.getInspectedBy().getId() : null)")
  @Mapping(
      target = "inspectedByName",
      expression =
          "java(report.getInspectedBy() != null ? report.getInspectedBy().getFirstName() + \" \" + report.getInspectedBy().getLastName() : null)")
  @Mapping(target = "defects", source = "defects")
  InspectionResponse toResponse(LoadConditionReport report);

  default DefectResponse toResponse(ConditionDefect defect) {
    return DefectResponse.from(defect);
  }
}
