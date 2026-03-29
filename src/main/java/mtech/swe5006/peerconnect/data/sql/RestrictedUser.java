package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "restricted_member")
@IdClass(RestrictedUser.RestrictedUserId.class)
public class RestrictedUser {

    @Id
    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Id
    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public UUID getBlockerId() { return blockerId; }
    public void setBlockerId(UUID blockerId) { this.blockerId = blockerId; }

    public UUID getBlockedId() { return blockedId; }
    public void setBlockedId(UUID blockedId) { this.blockedId = blockedId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Composite primary key class */
    public static class RestrictedUserId implements Serializable {
        private UUID blockerId;
        private UUID blockedId;

        public RestrictedUserId() {}

        public RestrictedUserId(UUID blockerId, UUID blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RestrictedUserId that)) return false;
            return Objects.equals(blockerId, that.blockerId) && Objects.equals(blockedId, that.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
