package mtech.swe5006.peerconnect.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GroupChatNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(GroupChatNotificationListener.class);

    @EventListener
    public void onMessageSent(GroupChatMessageSentEvent event) {
        // Observer placeholder for future push notifications or unread counters.
        log.debug("[GroupChatNotification] Message {} observed for group {}", event.messageId(), event.groupId());
    }
}
