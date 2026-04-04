package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.data.sql.TutoringClass;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.service.AuditService;
import mtech.swe5006.peerconnect.service.audit.TutoringAuditFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TutoringAuditFacadeTest {

    @Mock
    private AuditService auditService;

    private TutoringAuditFacade tutoringAuditFacade;
    private User actor;
    private UUID classId;

    @BeforeEach
    void setup() {
        tutoringAuditFacade = new TutoringAuditFacade(auditService);

        actor = new User();
        actor.setId(UUID.randomUUID());
        actor.setEmail("alice@u.nus.edu");

        classId = UUID.randomUUID();
    }

    @Test
    void classCreatedPublishesExpectedAuditEvent() {
        TutoringClass tutoringClass = new TutoringClass();
        tutoringClass.setId(classId);
        tutoringClass.setModuleCode("CS5000");
        tutoringClass.setMode("online");
        tutoringClass.setMaxStudents((short) 5);

        tutoringAuditFacade.classCreated(actor, tutoringClass);

        verify(auditService).record(
            eq("TUTORING_CLASS_CREATED"),
            eq(actor.getId()),
            eq(actor.getEmail()),
            eq("TUTORING_CLASS"),
            eq(classId),
            eq("SUCCESS"),
            isNull(),
            isNull(),
            eq(Map.of(
                "moduleCode", "CS5000",
                "mode", "online",
                "maxStudents", (short) 5
            ))
        );
    }

    @Test
    void feedbackSubmittedPublishesExpectedAuditEvent() {
        UUID revieweeId = UUID.randomUUID();

        tutoringAuditFacade.feedbackSubmitted(actor, classId, revieweeId, true);

        verify(auditService).record(
            eq("FEEDBACK_SUBMITTED"),
            eq(actor.getId()),
            eq(actor.getEmail()),
            eq("TUTORING_CLASS"),
            eq(classId),
            eq("SUCCESS"),
            isNull(),
            isNull(),
            eq(Map.of(
                "revieweeId", revieweeId.toString(),
                "anonymousToPeer", true
            ))
        );
    }

    @Test
    void feedbackViewedPublishesExpectedAuditEvent() {
        tutoringAuditFacade.feedbackViewed(actor, classId, 4);

        verify(auditService).record(
            eq("TUTORING_FEEDBACK_VIEWED"),
            eq(actor.getId()),
            eq(actor.getEmail()),
            eq("TUTORING_CLASS"),
            eq(classId),
            eq("SUCCESS"),
            isNull(),
            isNull(),
            argThat(details -> Integer.valueOf(4).equals(details.get("feedbackCount")))
        );
    }

    @Test
    void classDeletedPublishesExpectedAuditEvent() {
        tutoringAuditFacade.classDeleted(actor, classId);

        verify(auditService).record(
            eq("TUTORING_CLASS_DELETED"),
            eq(actor.getId()),
            eq(actor.getEmail()),
            eq("TUTORING_CLASS"),
            eq(classId),
            eq("SUCCESS"),
            isNull(),
            isNull(),
            eq(Map.of())
        );
    }

    @Test
    void classEnrolledPublishesExpectedAuditEvent() {
        tutoringAuditFacade.classEnrolled(actor, classId, 3L);

        verify(auditService).record(
            eq("TUTORING_CLASS_ENROLLED"),
            eq(actor.getId()),
            eq(actor.getEmail()),
            eq("TUTORING_CLASS"),
            eq(classId),
            eq("SUCCESS"),
            isNull(),
            isNull(),
            eq(Map.of("enrolledCount", 3L))
        );
    }

    @Test
    void classLeftPublishesExpectedAuditEvent() {
        tutoringAuditFacade.classLeft(actor, classId, 2L);

        verify(auditService).record(
            eq("TUTORING_CLASS_LEFT"),
            eq(actor.getId()),
            eq(actor.getEmail()),
            eq("TUTORING_CLASS"),
            eq(classId),
            eq("SUCCESS"),
            isNull(),
            isNull(),
            eq(Map.of("enrolledCount", 2L))
        );
    }
}
