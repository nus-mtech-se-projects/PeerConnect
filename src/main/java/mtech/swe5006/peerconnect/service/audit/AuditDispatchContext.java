package mtech.swe5006.peerconnect.service.audit;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditDispatchContext(
    String eventType,
    UUID actorUserId,
    String actorEmail,
    String targetType,
    UUID targetId,
    String outcome,
    String requestId,
    String ipAddress,
    LocalDateTime eventTime,
    Map<String, Object> details
) {
}
