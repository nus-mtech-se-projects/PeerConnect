package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "announcement_archives",
    uniqueConstraints = {
        @UniqueConstraint(name = "UK_announcement_archive_user", columnNames = {"announcement_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_announcement_archives_user", columnList = "user_id"),
        @Index(name = "idx_announcement_archives_announcement", columnList = "announcement_id")
    }
)
public class StudyGroupAnnouncementArchive {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "announcement_id", nullable = false)
    private UUID announcementId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "archived_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime archivedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (archivedAt == null) archivedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(UUID announcementId) { this.announcementId = announcementId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
