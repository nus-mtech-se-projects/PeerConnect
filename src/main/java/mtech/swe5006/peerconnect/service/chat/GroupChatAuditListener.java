package mtech.swe5006.peerconnect.service.chat;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import mtech.swe5006.peerconnect.service.AuditService;

@Component
public class GroupChatAuditListener {

    private final AuditService auditService;

    public GroupChatAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onMessageSent(GroupChatMessageSentEvent event) {
        auditService.record(
            "GROUP_CHAT_MESSAGE_SENT",
            event.senderId(),
            event.senderEmail(),
            "STUDY_GROUP",
            event.groupId(),
            "SUCCESS",
            null,
            null,
            Map.of(
                "chatId", event.chatId().toString(),
                "messageId", event.messageId().toString(),
                "sentAt", event.sentAt().toString(),
                "contentPreview", event.contentPreview()
            )
        );
    }
}
