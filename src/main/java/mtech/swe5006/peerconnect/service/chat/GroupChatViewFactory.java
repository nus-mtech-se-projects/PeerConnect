package mtech.swe5006.peerconnect.service.chat;

import org.springframework.stereotype.Component;

import mtech.swe5006.peerconnect.data.sql.GroupChatAttachment;
import mtech.swe5006.peerconnect.data.sql.GroupChat;
import mtech.swe5006.peerconnect.data.sql.GroupChatMessage;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.dto.GroupChatDtos;

@Component
public class GroupChatViewFactory {

    public GroupChatDtos.GroupChatSummaryResponse buildSummary(GroupChat chat,
                                                               StudyGroup group,
                                                               long messageCount,
                                                               String retrievalMode) {
        return new GroupChatDtos.GroupChatSummaryResponse(
            chat.getId().toString(),
            chat.getGroupId().toString(),
            resolveGroupName(group),
            group.getModuleCode(),
            group.getStudyMode(),
            messageCount,
            retrievalMode
        );
    }

    public GroupChatDtos.GroupChatListItem buildListItem(GroupChat chat,
                                                         StudyGroup group,
                                                         long messageCount,
                                                         String retrievalMode) {
        return new GroupChatDtos.GroupChatListItem(
            chat.getId().toString(),
            chat.getGroupId().toString(),
            resolveGroupName(group),
            group.getModuleCode(),
            group.getStudyMode(),
            messageCount,
            retrievalMode
        );
    }

    public GroupChatDtos.GroupChatMessageView buildMessage(GroupChatMessage message,
                                                           User sender,
                                                           GroupChatAttachment attachment) {
        return MessageViewBuilder.create()
            .id(message.getId().toString())
            .chatId(message.getChatId().toString())
            .groupId(message.getGroupId().toString())
            .senderId(sender.getId().toString())
            .senderName(resolveSenderName(sender))
            .senderEmail(sender.getEmail())
            .content(message.getContent())
            .sentAt(message.getSentAt().toString())
            .attachment(toAttachmentView(attachment))
            .build();
    }

    private GroupChatDtos.GroupChatAttachmentView toAttachmentView(GroupChatAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        String contentType = attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream";
        return new GroupChatDtos.GroupChatAttachmentView(
            attachment.getId().toString(),
            attachment.getOriginalFileName(),
            contentType,
            attachment.getFileSize(),
            "/api/group-chats/attachments/" + attachment.getId(),
            contentType.startsWith("image/")
        );
    }

    private String resolveSenderName(User sender) {
        String fullName = (sender.getFirstName() + " " + sender.getLastName()).trim();
        return fullName.isBlank() ? sender.getEmail() : fullName;
    }

    private String resolveGroupName(StudyGroup group) {
        if (group.getName() != null && !group.getName().isBlank()) {
            return group.getName();
        }
        if (group.getTopic() != null && !group.getTopic().isBlank()) {
            return group.getTopic();
        }
        return "Study Group";
    }

    // Builder pattern keeps view-model construction consistent even as fields evolve.
    private static final class MessageViewBuilder {
        private String id;
        private String chatId;
        private String groupId;
        private String senderId;
        private String senderName;
        private String senderEmail;
        private String content;
        private String sentAt;
        private GroupChatDtos.GroupChatAttachmentView attachment;

        private MessageViewBuilder() {}

        static MessageViewBuilder create() {
            return new MessageViewBuilder();
        }

        MessageViewBuilder id(String value) { this.id = value; return this; }
        MessageViewBuilder chatId(String value) { this.chatId = value; return this; }
        MessageViewBuilder groupId(String value) { this.groupId = value; return this; }
        MessageViewBuilder senderId(String value) { this.senderId = value; return this; }
        MessageViewBuilder senderName(String value) { this.senderName = value; return this; }
        MessageViewBuilder senderEmail(String value) { this.senderEmail = value; return this; }
        MessageViewBuilder content(String value) { this.content = value; return this; }
        MessageViewBuilder sentAt(String value) { this.sentAt = value; return this; }
        MessageViewBuilder attachment(GroupChatDtos.GroupChatAttachmentView value) { this.attachment = value; return this; }

        GroupChatDtos.GroupChatMessageView build() {
            return new GroupChatDtos.GroupChatMessageView(
                id,
                chatId,
                groupId,
                senderId,
                senderName,
                senderEmail,
                content,
                sentAt,
                attachment
            );
        }
    }
}
