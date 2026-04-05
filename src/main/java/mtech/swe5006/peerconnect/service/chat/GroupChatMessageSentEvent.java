package mtech.swe5006.peerconnect.service.chat;

import java.time.LocalDateTime;
import java.util.UUID;

public record GroupChatMessageSentEvent(
    UUID messageId,
    UUID chatId,
    UUID groupId,
    UUID senderId,
    String senderEmail,
    String senderName,
    LocalDateTime sentAt,
    String contentPreview
) {}
