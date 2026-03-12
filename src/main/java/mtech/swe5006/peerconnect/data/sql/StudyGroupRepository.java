package mtech.swe5006.peerconnect.data.sql;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, UUID> {
    List<StudyGroup> findByStatus(String status);
    List<StudyGroup> findByStatusInOrderByCreatedAtDesc(List<String> statuses);
    List<StudyGroup> findByCreatedBy(UUID createdBy);
    List<StudyGroup> findByStatusOrderByCreatedAtDesc(String status);
}
