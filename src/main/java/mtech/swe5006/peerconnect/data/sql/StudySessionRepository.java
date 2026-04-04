package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    List<StudySession> findByGroupIdOrderByStartsAtAsc(UUID groupId);
}
