package mtech.swe5006.peerconnect.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnnouncementResponse(
    UUID id,
    UUID groupId,
    String title,
    String content,
    UUID createdBy,
    LocalDateTime createdAt,
    String authorEmail,
    String authorName,
    String groupName,
    String moduleCode
) {
}
