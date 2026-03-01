package mtech.swe5006.peerconnect.data.sql;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Find an unused, non-expired token for a given user + token code. */
    Optional<PasswordResetToken> findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
            UUID userId, String token, LocalDateTime now);

    /** Find all unused tokens for a user (to invalidate on login). */
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);
}
