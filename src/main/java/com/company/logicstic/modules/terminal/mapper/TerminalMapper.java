package com.company.logicstic.modules.terminal.mapper;

import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link Terminal} and its DTOs. Generated at compile time by MapStruct — never
 * hand-write the implementation and never use {@code BeanUtils.copyProperties}.
 *
 * <p>Server-owned fields ({@code id}, audit columns) are ignored on the write direction so a client
 * can never set them. The {@code type} text column is bridged through {@link
 * com.company.logicstic.modules.terminal.enums.TerminalType} so an invalid value fails in one
 * place.
 */
@Mapper(config = MapperConfiguration.class)
public interface TerminalMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  @Mapping(target = "type", expression = "java(request.type().dbValue())")
  Terminal toEntity(CreateTerminalRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "lastModifiedAt", ignore = true)
  @Mapping(target = "lastModifiedBy", ignore = true)
  @Mapping(target = "type", expression = "java(request.type().dbValue())")
  void updateEntity(CreateTerminalRequest request, @MappingTarget Terminal terminal);

  @Mapping(target = "type", expression = "java(terminal.getTypeEnum())")
  TerminalResponse toResponse(Terminal terminal);
}
