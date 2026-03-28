package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TutoringEnrollmentRepository extends JpaRepository<TutoringEnrollment, UUID> {
    List<TutoringEnrollment> findByClassId(UUID classId);
    Optional<TutoringEnrollment> findByClassIdAndUserId(UUID classId, UUID userId);
    long countByClassId(UUID classId);

    @Transactional
    void deleteByClassId(UUID classId);

    @Transactional
    void deleteByClassIdAndUserId(UUID classId, UUID userId);
}