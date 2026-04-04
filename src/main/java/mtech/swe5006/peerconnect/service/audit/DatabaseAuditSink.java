package mtech.swe5006.peerconnect.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mtech.swe5006.peerconnect.data.sql.AuditEvent;
import mtech.swe5006.peerconnect.data.sql.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class DatabaseAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuditSink.class);

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseAuditSink(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void publish(AuditDispatchContext context) {
        try {
            AuditEvent event = new AuditEvent();
            event.setEventType(context.eventType());
            event.setActorUserId(context.actorUserId());
            event.setActorEmail(context.actorEmail());
            event.setTargetType(context.targetType());
            event.setTargetId(context.targetId());
            event.setOutcome(context.outcome());
            event.setRequestId(context.requestId());
            event.setIpAddress(context.ipAddress());
            event.setEventTime(context.eventTime());
            event.setDetailsJson(toJson(context));
            auditEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Failed to persist audit event to database: {}", context.eventType(), ex);
        }
    }

    private String toJson(AuditDispatchContext context) {
        if (context.details() == null || context.details().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(context.details());
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit details", ex);
            return null;
        }
    }
}
