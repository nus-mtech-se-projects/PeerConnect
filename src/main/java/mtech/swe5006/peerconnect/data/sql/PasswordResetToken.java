package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "password_resets")
public class PasswordResetToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, length = 128)
    private String token;

    @Column(name = "expiry", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime expiry;

    @Column(name = "created_at", columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "used_at", columnDefinition = "datetime2")
    private LocalDateTime usedAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
