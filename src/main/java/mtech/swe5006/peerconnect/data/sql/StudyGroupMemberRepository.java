package mtech.swe5006.peerconnect.data.sql;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyGroupMemberRepository extends JpaRepository<StudyGroupMember, UUID> {
    List<StudyGroupMember> findByGroupId(UUID groupId);
    List<StudyGroupMember> findByGroupIdAndMembershipStatus(UUID groupId, String membershipStatus);
    Optional<StudyGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
    long countByGroupIdAndMembershipStatus(UUID groupId, String membershipStatus);
    long countByGroupId(UUID groupId);
    void deleteByGroupId(UUID groupId);
    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}
