package com.company.logicstic.modules.messaging.mapper;

import com.company.logicstic.shared.config.MapperConfiguration;
import com.company.logicstic.modules.messaging.dto.MessageView;
import com.company.logicstic.modules.messaging.dto.SendMessageRequest;
import com.company.logicstic.modules.messaging.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between {@link Message} entity and its DTOs.
 * <p>
 * The {@code conversation} and {@code sender} FK relations are ignored during entity mapping
 * — the service resolves them via repositories before saving.
 */
@Mapper(config = MapperConfiguration.class)
public interface MessageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "conversation", ignore = true)
    @Mapping(target = "sender", ignore = true)
    @Mapping(target = "sentAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "readReceipts", ignore = true)
    Message toEntity(SendMessageRequest req);

    @Mapping(target = "conversationId", expression = "java(message.getConversation() != null ? message.getConversation().getId() : null)")
    @Mapping(target = "senderId", expression = "java(message.getSender() != null ? message.getSender().getId() : null)")
    @Mapping(target = "senderName", expression = "java(message.getSender() != null ? message.getSender().getFirstName() + \" \" + message.getSender().getLastName() : null)")
    MessageView toView(Message message);
}
