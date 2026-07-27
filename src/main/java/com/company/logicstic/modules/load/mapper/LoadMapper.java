package com.company.logicstic.modules.load.mapper;

import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Maps between {@link Load} entity and its DTOs. */
@Mapper(config = MapperConfiguration.class)
public interface LoadMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "number", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(
      target = "isInProximity",
      expression = "java(req.isInProximity() != null ? req.isInProximity() : false)")
  @Mapping(
      target = "isHazmat",
      expression = "java(req.isHazmat() != null ? req.isHazmat() : false)")
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "assignedTruck", ignore = true)
  @Mapping(target = "assignedDispatcher", ignore = true)
  @Mapping(target = "container", ignore = true)
  @Mapping(target = "originTerminal", ignore = true)
  @Mapping(target = "destinationTerminal", ignore = true)
  @Mapping(target = "dispatchedAt", ignore = true)
  @Mapping(target = "pickedUpAt", ignore = true)
  @Mapping(target = "deliveredAt", ignore = true)
  @Mapping(target = "cancelledAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Load toEntity(CreateLoadRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "number", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(
      target = "isInProximity",
      expression = "java(req.isInProximity() != null ? req.isInProximity() : false)")
  @Mapping(
      target = "isHazmat",
      expression = "java(req.isHazmat() != null ? req.isHazmat() : false)")
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "assignedTruck", ignore = true)
  @Mapping(target = "assignedDispatcher", ignore = true)
  @Mapping(target = "container", ignore = true)
  @Mapping(target = "originTerminal", ignore = true)
  @Mapping(target = "destinationTerminal", ignore = true)
  @Mapping(target = "dispatchedAt", ignore = true)
  @Mapping(target = "pickedUpAt", ignore = true)
  @Mapping(target = "deliveredAt", ignore = true)
  @Mapping(target = "cancelledAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreateLoadRequest req, @MappingTarget Load load);

  @Mapping(
      target = "customerId",
      expression = "java(load.getCustomer() != null ? load.getCustomer().getId() : null)")
  @Mapping(
      target = "customerName",
      expression = "java(load.getCustomer() != null ? load.getCustomer().getName() : null)")
  @Mapping(
      target = "assignedTruckId",
      expression = "java(load.getAssignedTruck() != null ? load.getAssignedTruck().getId() : null)")
  @Mapping(
      target = "assignedTruckNumber",
      expression =
          "java(load.getAssignedTruck() != null ? load.getAssignedTruck().getNumber() : null)")
  @Mapping(
      target = "assignedDispatcherId",
      expression =
          "java(load.getAssignedDispatcher() != null ? load.getAssignedDispatcher().getId() : null)")
  @Mapping(
      target = "assignedDispatcherName",
      expression =
          "java(load.getAssignedDispatcher() != null ? load.getAssignedDispatcher().getFirstName() + \" \" + load.getAssignedDispatcher().getLastName() : null)")
  @Mapping(
      target = "containerId",
      expression = "java(load.getContainer() != null ? load.getContainer().getId() : null)")
  @Mapping(
      target = "originTerminalId",
      expression =
          "java(load.getOriginTerminal() != null ? load.getOriginTerminal().getId() : null)")
  @Mapping(
      target = "destinationTerminalId",
      expression =
          "java(load.getDestinationTerminal() != null ? load.getDestinationTerminal().getId() : null)")
  LoadResponse toResponse(Load load);
}
