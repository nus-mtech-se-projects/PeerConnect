package mtech.swe5006.peerconnect.service.chat;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;
import mtech.swe5006.peerconnect.data.sql.User;

@Component
public class EventPublishingMessageDispatchStrategy implements GroupChatMessageDispatchStrategy {

    private final ApplicationEventPublisher eventPublisher;

    public EventPublishingMessageDispatchStrategy(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void dispatch(GroupChatMessage message, User sender) {
        String senderName = (sender.getFirstName() + " " + sender.getLastName()).trim();
        String normalizedName = senderName.isBlank() ? sender.getEmail() : senderName;
        String preview = message.getContent().length() > 64
            ? message.getContent().substring(0, 64)
            : message.getContent();

        eventPublisher.publishEvent(new GroupChatMessageSentEvent(
            message.getId(),
            message.getChatId(),
            message.getGroupId(),
            sender.getId(),
            sender.getEmail(),
            normalizedName,
            message.getSentAt(),
            preview
        ));
    }
}
