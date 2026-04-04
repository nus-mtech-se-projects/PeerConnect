package mtech.swe5006.peerconnect.service.audit;

import mtech.swe5006.peerconnect.data.sql.TutoringClass;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.service.AuditService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class TutoringAuditFacade {

    private static final String TARGET_TYPE = "TUTORING_CLASS";
    private static final String SUCCESS = "SUCCESS";

    private final AuditService auditService;

    public TutoringAuditFacade(AuditService auditService) {
        this.auditService = auditService;
    }

    public void classCreated(User actor, TutoringClass tutoringClass) {
        record(
            "TUTORING_CLASS_CREATED",
            actor,
            tutoringClass.getId(),
            Map.of(
                "moduleCode", tutoringClass.getModuleCode(),
                "mode", tutoringClass.getMode(),
                "maxStudents", tutoringClass.getMaxStudents()
            )
        );
    }

    public void classDeleted(User actor, UUID classId) {
        record("TUTORING_CLASS_DELETED", actor, classId, Map.of());
    }

    public void classEnrolled(User actor, UUID classId, long enrolledCount) {
        record("TUTORING_CLASS_ENROLLED", actor, classId, Map.of("enrolledCount", enrolledCount));
    }

    public void classLeft(User actor, UUID classId, long enrolledCount) {
        record("TUTORING_CLASS_LEFT", actor, classId, Map.of("enrolledCount", enrolledCount));
    }

    public void feedbackSubmitted(User actor, UUID classId, UUID revieweeId, boolean anonymousToPeer) {
        record(
            "FEEDBACK_SUBMITTED",
            actor,
            classId,
            Map.of(
                "revieweeId", revieweeId.toString(),
                "anonymousToPeer", anonymousToPeer
            )
        );
    }

    public void feedbackViewed(User actor, UUID classId, int feedbackCount) {
        record("TUTORING_FEEDBACK_VIEWED", actor, classId, Map.of("feedbackCount", feedbackCount));
    }

    private void record(String eventType, User actor, UUID classId, Map<String, Object> details) {
        auditService.record(
            eventType,
            actor.getId(),
            actor.getEmail(),
            TARGET_TYPE,
            classId,
            SUCCESS,
            null,
            null,
            details
        );
    }
}
