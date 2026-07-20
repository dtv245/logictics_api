package com.company.logicstic.modules.fleet.mapper;

import com.company.logicstic.modules.fleet.dto.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.TruckView;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link Truck} entity and its DTOs.
 *
 * <p>Driver FK relations ({@code mainDriver}, {@code secondaryDriver}) are ignored during entity
 * mapping — the service resolves them from {@link
 * com.company.logicstic.repository.EmployeeRepository}.
 *
 * <p>Note: {@link Truck} does NOT extend {@link com.company.logicstic.entity.BaseAuditableEntity},
 * so no audit-field ignore annotations are needed.
 */
@Mapper(config = MapperConfiguration.class)
public interface TruckMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "mainDriver", ignore = true)
  @Mapping(target = "secondaryDriver", ignore = true)
  @Mapping(target = "currentLocationLatitude", ignore = true)
  @Mapping(target = "currentLocationLongitude", ignore = true)
  @Mapping(target = "currentAddressLine1", ignore = true)
  @Mapping(target = "currentAddressLine2", ignore = true)
  @Mapping(target = "currentAddressCity", ignore = true)
  @Mapping(target = "currentAddressState", ignore = true)
  @Mapping(target = "currentAddressZipCode", ignore = true)
  @Mapping(target = "currentAddressCountry", ignore = true)
  @Mapping(target = "adrEquipmentAdrCertExpiresAt", ignore = true)
  @Mapping(
      target = "isHazmatPlacarded",
      expression = "java(req.isHazmatPlacarded() != null ? req.isHazmatPlacarded() : false)")
  @Mapping(
      target = "adrEquipmentIsAdrCertified",
      expression =
          "java(req.adrEquipmentIsAdrCertified() != null ? req.adrEquipmentIsAdrCertified() : false)")
  @Mapping(
      target = "adrEquipmentAllowedClasses",
      expression =
          "java(req.adrEquipmentAllowedClasses() != null ? req.adrEquipmentAllowedClasses() : \"\")")
  Truck toEntity(CreateTruckRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "mainDriver", ignore = true)
  @Mapping(target = "secondaryDriver", ignore = true)
  @Mapping(target = "currentLocationLatitude", ignore = true)
  @Mapping(target = "currentLocationLongitude", ignore = true)
  @Mapping(target = "currentAddressLine1", ignore = true)
  @Mapping(target = "currentAddressLine2", ignore = true)
  @Mapping(target = "currentAddressCity", ignore = true)
  @Mapping(target = "currentAddressState", ignore = true)
  @Mapping(target = "currentAddressZipCode", ignore = true)
  @Mapping(target = "currentAddressCountry", ignore = true)
  @Mapping(target = "adrEquipmentAdrCertExpiresAt", ignore = true)
  @Mapping(
      target = "isHazmatPlacarded",
      expression = "java(req.isHazmatPlacarded() != null ? req.isHazmatPlacarded() : false)")
  @Mapping(
      target = "adrEquipmentIsAdrCertified",
      expression =
          "java(req.adrEquipmentIsAdrCertified() != null ? req.adrEquipmentIsAdrCertified() : false)")
  @Mapping(
      target = "adrEquipmentAllowedClasses",
      expression =
          "java(req.adrEquipmentAllowedClasses() != null ? req.adrEquipmentAllowedClasses() : \"\")")
  void updateEntity(CreateTruckRequest req, @MappingTarget Truck truck);

  @Mapping(
      target = "mainDriverId",
      expression = "java(truck.getMainDriver() != null ? truck.getMainDriver().getId() : null)")
  @Mapping(
      target = "mainDriverName",
      expression =
          "java(truck.getMainDriver() != null ? truck.getMainDriver().getFirstName() + \" \" + truck.getMainDriver().getLastName() : null)")
  @Mapping(
      target = "secondaryDriverId",
      expression =
          "java(truck.getSecondaryDriver() != null ? truck.getSecondaryDriver().getId() : null)")
  @Mapping(
      target = "secondaryDriverName",
      expression =
          "java(truck.getSecondaryDriver() != null ? truck.getSecondaryDriver().getFirstName() + \" \" + truck.getSecondaryDriver().getLastName() : null)")
  TruckView toView(Truck truck);
}
