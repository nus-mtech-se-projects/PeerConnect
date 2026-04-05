package mtech.swe5006.peerconnect.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupChatDtos {

    public record GroupChatAttachmentView(
        String id,
        String fileName,
        String contentType,
        long fileSize,
        String downloadUrl,
        boolean image
    ) {}

    public record GroupChatListItem(
        String chatId,
        String groupId,
        String groupName,
        String moduleCode,
        String studyMode,
        long messageCount,
        String retrievalMode
    ) {}

    public record GroupChatSummaryResponse(
        String chatId,
        String groupId,
        String groupName,
        String moduleCode,
        String studyMode,
        long messageCount,
        String retrievalMode
    ) {}

    public record GroupChatMessageView(
        String id,
        String chatId,
        String groupId,
        String senderId,
        String senderName,
        String senderEmail,
        String content,
        String sentAt,
        GroupChatAttachmentView attachment
    ) {}

    public record GroupChatMessagesResponse(
        GroupChatSummaryResponse chat,
        List<GroupChatMessageView> messages
    ) {}

    public record SendGroupChatMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        @Size(max = 2000, message = "Message content must not exceed 2000 characters")
        String content
    ) {}
}
