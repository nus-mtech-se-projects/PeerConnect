package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudyGroupAnnouncementRepository extends JpaRepository<StudyGroupAnnouncement, UUID> {
    List<StudyGroupAnnouncement> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
    List<StudyGroupAnnouncement> findByGroupIdInOrderByCreatedAtDesc(List<UUID> groupIds);
}
