package com.company.logicstic.mapper;

import com.company.logicstic.config.MapperConfiguration;
import com.company.logicstic.dto.trip.CreateTripRequest;
import com.company.logicstic.dto.trip.TripView;
import com.company.logicstic.entity.Trip;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapperConfiguration.class)
public interface TripMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "number", ignore = true)
    @Mapping(target = "truck", ignore = true)
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
    @Mapping(target = "truck", ignore = true)
    @Mapping(target = "dispatchedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updateEntity(CreateTripRequest req, @MappingTarget Trip trip);

    @Mapping(target = "truckId", expression = "java(trip.getTruck() != null ? trip.getTruck().getId() : null)")
    @Mapping(target = "truckNumber", expression = "java(trip.getTruck() != null ? trip.getTruck().getNumber() : null)")
    TripView toView(Trip trip);
}