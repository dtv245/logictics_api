package com.company.logicstic.modules.trip.mapper;

import com.company.logicstic.modules.trip.dto.request.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.response.TripResponse;
import com.company.logicstic.modules.trip.entity.Trip;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapperConfiguration.class)
public interface TripMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "number", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "truck", ignore = true)
  @Mapping(target = "stops", ignore = true)
  @Mapping(target = "dispatchedAt", ignore = true)
  @Mapping(target = "completedAt", ignore = true)
  @Mapping(target = "cancelledAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  Trip toEntity(CreateTripRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "number", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "truck", ignore = true)
  @Mapping(target = "stops", ignore = true)
  @Mapping(target = "dispatchedAt", ignore = true)
  @Mapping(target = "completedAt", ignore = true)
  @Mapping(target = "cancelledAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  void updateEntity(CreateTripRequest req, @MappingTarget Trip trip);

  @Mapping(
      target = "truckId",
      expression = "java(trip.getTruck() != null ? trip.getTruck().getId() : null)")
  @Mapping(
      target = "truckNumber",
      expression = "java(trip.getTruck() != null ? trip.getTruck().getNumber() : null)")
  @Mapping(
      target = "stops",
      expression =
          "java(trip.getStops().stream().map(com.company.logicstic.modules.trip.dto.response.TripStopResponse::from).toList())")
  TripResponse toResponse(Trip trip);
}
