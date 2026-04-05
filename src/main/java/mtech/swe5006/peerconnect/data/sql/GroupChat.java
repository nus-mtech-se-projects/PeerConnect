package mtech.swe5006.peerconnect.data.sql;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "group_chats",
    uniqueConstraints = @UniqueConstraint(name = "UK_group_chats_group_id", columnNames = {"group_id"}),
    indexes = {
        @Index(name = "IX_group_chats_group_id", columnList = "group_id")
    }
)
public class GroupChat {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
