package mtech.swe5006.peerconnect.data.sql;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "password_resets")
public class PasswordResetToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, length = 128)
    private String token;

    @Column(name = "expiry", nullable = false, columnDefinition = "datetime2")  
    private LocalDateTime expiry;

    @Column(name = "used_at", columnDefinition = "datetime2")
    private LocalDateTime usedAt;
}
