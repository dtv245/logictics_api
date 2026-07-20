package com.company.logicstic.shared.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration for all mappers. This ensures consistent settings across all
 * mappers in the application.
 */
@MapperConfig(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    unmappedSourcePolicy = ReportingPolicy.WARN,
    uses = MapperUtils.class)
public interface MapperConfiguration {}
