package mtech.swe5006.peerconnect.data.sql;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, UUID> {
    List<GroupChatMessage> findByGroupIdOrderBySentAtAsc(UUID groupId);
    long countByGroupId(UUID groupId);
}
