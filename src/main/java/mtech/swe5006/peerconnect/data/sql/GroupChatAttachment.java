package mtech.swe5006.peerconnect.data.sql;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "group_chat_attachments", indexes = {
    @Index(name = "IX_group_chat_attachments_chat", columnList = "chat_id"),
    @Index(name = "IX_group_chat_attachments_group", columnList = "group_id"),
    @Index(name = "IX_group_chat_attachments_message", columnList = "message_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UK_group_chat_attachments_message", columnNames = {"message_id"})
})
public class GroupChatAttachment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "original_file_name", nullable = false, length = 260)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Lob
    @Column(name = "file_data", nullable = false)
    private byte[] fileData;

    @Column(name = "uploaded_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime uploadedAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.uploadedAt == null) this.uploadedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
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

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
