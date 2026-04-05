package mtech.swe5006.peerconnect.data.sql;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupChatAttachmentRepository extends JpaRepository<GroupChatAttachment, UUID> {
    Optional<GroupChatAttachment> findByMessageId(UUID messageId);
    List<GroupChatAttachment> findByMessageIdIn(Collection<UUID> messageIds);
    Optional<GroupChatAttachment> findByIdAndGroupId(UUID id, UUID groupId);
}
