package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface PeerFeedbackRepository extends JpaRepository<PeerFeedback, UUID> {
    boolean existsBySessionIdAndReviewerIdAndRevieweeId(UUID sessionId, UUID reviewerId, UUID revieweeId);

    List<PeerFeedback> findByPeerTutorGroupIdOrderByCreatedAtDesc(UUID peerTutorGroupId);

    @Transactional
    void deleteByPeerTutorGroupId(UUID peerTutorGroupId);
}
