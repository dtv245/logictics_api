package com.company.logicstic.modules.messaging.mapper;

import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.dto.response.ConversationResponse;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.shared.config.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link Conversation} entity and its DTOs.
 *
 * <p>The {@code load} FK is ignored during entity mapping — the service resolves it. The {@code
 * participants} and {@code messages} collections are managed separately.
 */
@Mapper(config = MapperConfiguration.class)
public interface ConversationMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "load", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "lastMessageAt", ignore = true)
  @Mapping(target = "messages", ignore = true)
  @Mapping(target = "participants", ignore = true)
  Conversation toEntity(CreateConversationRequest req);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "load", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "lastMessageAt", ignore = true)
  @Mapping(target = "messages", ignore = true)
  @Mapping(target = "participants", ignore = true)
  void updateEntity(CreateConversationRequest req, @MappingTarget Conversation conversation);

  @Mapping(
      target = "loadId",
      expression = "java(conversation.getLoad() != null ? conversation.getLoad().getId() : null)")
  @Mapping(
      target = "participantIds",
      expression =
          "java(conversation.getParticipants().stream().map(participant -> participant.getEmployee().getId()).toList())")
  ConversationResponse toResponse(Conversation conversation);
}
