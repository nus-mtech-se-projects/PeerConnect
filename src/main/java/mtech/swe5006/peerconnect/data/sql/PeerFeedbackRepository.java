package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PeerFeedbackRepository extends JpaRepository<PeerFeedback, UUID> {
    boolean existsBySessionIdAndReviewerIdAndRevieweeId(UUID sessionId, UUID reviewerId, UUID revieweeId);

    @Transactional
    void deleteByGroupId(UUID groupId);

    @Transactional
    void deleteBySessionId(UUID sessionId);
}
