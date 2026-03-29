package mtech.swe5006.peerconnect.data.sql;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface StudyGroupMemberRepository extends JpaRepository<StudyGroupMember, UUID> {
    List<StudyGroupMember> findByGroupId(UUID groupId);
    List<StudyGroupMember> findByGroupIdIn(Collection<UUID> groupIds);
    List<StudyGroupMember> findByGroupIdAndMembershipStatus(UUID groupId, String membershipStatus);
    Optional<StudyGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
    long countByGroupIdAndMembershipStatus(UUID groupId, String membershipStatus);
    long countByGroupId(UUID groupId);
    @Transactional
    void deleteByGroupId(UUID groupId);
    @Transactional
    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);
}
