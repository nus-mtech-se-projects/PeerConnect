package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface StudyGroupAnnouncementArchiveRepository extends JpaRepository<StudyGroupAnnouncementArchive, UUID> {
    boolean existsByAnnouncementIdAndUserId(UUID announcementId, UUID userId);
    List<StudyGroupAnnouncementArchive> findByUserId(UUID userId);

    @Transactional
    void deleteByAnnouncementId(UUID announcementId);

    /**
     * Remove the per-user archive flag so the announcement becomes visible again
     * in the user's active feed. Returns the number of rows removed (0 if the
     * announcement wasn't archived for this user, so callers can treat that
     * case as a no-op rather than an error).
     */
    @Transactional
    long deleteByAnnouncementIdAndUserId(UUID announcementId, UUID userId);
}
