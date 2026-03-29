package mtech.swe5006.peerconnect.data.sql;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RestrictedUserRepository extends JpaRepository<RestrictedUser, RestrictedUser.RestrictedUserId> {

    List<RestrictedUser> findByBlockerId(UUID blockerId);

    List<RestrictedUser> findByBlockedId(UUID blockedId);

    Optional<RestrictedUser> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
