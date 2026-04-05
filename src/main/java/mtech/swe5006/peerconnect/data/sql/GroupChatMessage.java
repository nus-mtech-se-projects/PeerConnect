package mtech.swe5006.peerconnect.data.sql;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_chat_messages", indexes = {
    @Index(name = "IX_group_chat_messages_group_sent", columnList = "group_id, sent_at"),
    @Index(name = "IX_group_chat_messages_chat_sent", columnList = "chat_id, sent_at")
})
public class GroupChatMessage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "content", nullable = false, length = 2000, columnDefinition = "NVARCHAR(2000)")
    private String content;

    @Column(name = "sent_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime sentAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.sentAt == null) this.sentAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChatId() {
        return chatId;
    }

    public void setChatId(UUID chatId) {
        this.chatId = chatId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public void setSenderId(UUID senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
