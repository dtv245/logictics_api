package com.company.logicstic.mapper;

import com.company.logicstic.config.MapperConfiguration;
import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.dto.load.LoadView;
import com.company.logicstic.entity.Load;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link Load} entity and its DTOs.
 */
@Mapper(config = MapperConfiguration.class)
public interface LoadMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "number", ignore = true)
    @Mapping(target = "isInProximity", expression = "java(req.isInProximity() != null ? req.isInProximity() : false)")
    @Mapping(target = "isHazmat", expression = "java(req.isHazmat() != null ? req.isHazmat() : false)")
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "assignedTruck", ignore = true)
    @Mapping(target = "assignedDispatcher", ignore = true)
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
    @Mapping(target = "isInProximity", expression = "java(req.isInProximity() != null ? req.isInProximity() : false)")
    @Mapping(target = "isHazmat", expression = "java(req.isHazmat() != null ? req.isHazmat() : false)")
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "assignedTruck", ignore = true)
    @Mapping(target = "assignedDispatcher", ignore = true)
    @Mapping(target = "dispatchedAt", ignore = true)
    @Mapping(target = "pickedUpAt", ignore = true)
    @Mapping(target = "deliveredAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    void updateEntity(CreateLoadRequest req, @MappingTarget Load load);

    @Mapping(target = "customerId", expression = "java(load.getCustomer() != null ? load.getCustomer().getId() : null)")
    @Mapping(target = "customerName", expression = "java(load.getCustomer() != null ? load.getCustomer().getName() : null)")
    @Mapping(target = "assignedTruckId", expression = "java(load.getAssignedTruck() != null ? load.getAssignedTruck().getId() : null)")
    @Mapping(target = "assignedTruckNumber", expression = "java(load.getAssignedTruck() != null ? load.getAssignedTruck().getNumber() : null)")
    @Mapping(target = "assignedDispatcherId", expression = "java(load.getAssignedDispatcher() != null ? load.getAssignedDispatcher().getId() : null)")
    @Mapping(target = "assignedDispatcherName", expression = "java(load.getAssignedDispatcher() != null ? load.getAssignedDispatcher().getFirstName() + \" \" + load.getAssignedDispatcher().getLastName() : null)")
    LoadView toView(Load load);
}