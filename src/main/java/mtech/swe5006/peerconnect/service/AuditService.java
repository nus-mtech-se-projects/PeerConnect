package mtech.swe5006.peerconnect.service;

import mtech.swe5006.peerconnect.service.audit.AuditDispatchContext;
import mtech.swe5006.peerconnect.service.audit.AuditSink;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final List<AuditSink> auditSinks;

    public AuditService(List<AuditSink> auditSinks) {
        this.auditSinks = auditSinks;
    }

    public void record(String eventType,
                       UUID actorUserId,
                       String actorEmail,
                       String targetType,
                       UUID targetId,
                       String outcome,
                       String requestId,
                       String ipAddress,
                       Map<String, Object> details) {

        AuditDispatchContext context = new AuditDispatchContext(
            eventType,
            actorUserId,
            actorEmail,
            targetType,
            targetId,
            outcome,
            requestId,
            ipAddress,
            LocalDateTime.now(),
            details
        );

        for (AuditSink auditSink : auditSinks) {
            if (auditSink.supports(context)) {
                auditSink.publish(context);
            }
        }
    }
}
