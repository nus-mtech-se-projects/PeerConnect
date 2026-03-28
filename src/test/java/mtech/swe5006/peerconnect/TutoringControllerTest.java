package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.TutoringController;
import mtech.swe5006.peerconnect.data.sql.*;
import mtech.swe5006.peerconnect.service.audit.TutoringAuditFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TutoringControllerTest {

    @Mock
    private TutoringClassRepository tutoringClassRepository;
    @Mock
    private TutoringEnrollmentRepository tutoringEnrollmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PeerFeedbackRepository peerFeedbackRepository;
    @Mock
    private TutoringAuditFacade tutoringAuditFacade;

    @InjectMocks
    private TutoringController controller;

    private User alice;
    private User bob;

    @BeforeEach
    void setup() {
        alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail("alice@u.nus.edu");
        alice.setFirstName("Alice");
        alice.setLastName("Tan");

        bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setEmail("bob@u.nus.edu");
        bob.setFirstName("Bob");
        bob.setLastName("Lim");
    }

    private Authentication authFor(User u) {
        Authentication a = mock(Authentication.class);
        when(a.getName()).thenReturn(u.getEmail());
        return a;
    }

    @Nested
    @DisplayName("POST /api/tutoring/classes/{id}/feedback")
    class SubmitFeedback {
        private UUID classId;
        private TutoringClass tutoringClass;
        private TutoringEnrollment enrollment;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();
            
            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setCreatedBy(bob.getId()); // Bob is the tutor

            enrollment = new TutoringEnrollment();
            enrollment.setClassId(classId);
            enrollment.setUserId(alice.getId()); // Alice is a student

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
            lenient().when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.of(enrollment));
            lenient().when(peerFeedbackRepository.existsBySessionIdAndReviewerIdAndRevieweeId(classId, alice.getId(), bob.getId()))
                .thenReturn(false);
        }

        @Test
        void successfulFeedbackSubmissionReturnsSuccess() {
            when(peerFeedbackRepository.save(any(PeerFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ResponseEntity<?> response = controller.submitFeedback(classId, authFor(alice), validFeedbackBody(bob.getId()));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isInstanceOf(Map.class);
            Map<?, ?> payload = (Map<?, ?>) response.getBody();
            assertThat(payload.get("revieweeId")).isEqualTo(bob.getId().toString());

            ArgumentCaptor<PeerFeedback> captor = ArgumentCaptor.forClass(PeerFeedback.class);
            verify(peerFeedbackRepository).save(captor.capture());
            assertThat(captor.getValue().getReviewerId()).isEqualTo(alice.getId());
            assertThat(captor.getValue().getRevieweeId()).isEqualTo(bob.getId());
            verify(tutoringAuditFacade).feedbackSubmitted(alice, classId, bob.getId(), true);
        }

        @Test
        void selfReviewIsRejected() {
            Map<String, Object> body = validFeedbackBody(alice.getId());
            ResponseEntity<?> response = controller.submitFeedback(classId, authFor(alice), body);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "You cannot submit feedback for yourself"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void duplicateSubmissionIsRejectedWithConflict() {
            when(peerFeedbackRepository.existsBySessionIdAndReviewerIdAndRevieweeId(classId, alice.getId(), bob.getId()))
                .thenReturn(true);

            ResponseEntity<?> response = controller.submitFeedback(classId, authFor(alice), validFeedbackBody(bob.getId()));

            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Feedback has already been submitted for this peer and session"));
            verify(peerFeedbackRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("GET /api/tutoring/classes")
    class GetAllClasses {
        @Test
        void returnsListOfClasses() {
            TutoringClass tc = new TutoringClass();
            tc.setId(UUID.randomUUID());
            tc.setTitle("Java Basics");
            tc.setCreatedBy(bob.getId());

            when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            when(tutoringClassRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tc));
            when(tutoringEnrollmentRepository.countByClassId(tc.getId())).thenReturn(2L);
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(tc.getId(), alice.getId()))
                .thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getAllClasses(authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat((List<?>) res.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /api/tutoring/classes")
    class CreateClass {
        @BeforeEach
        void init() {
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(tutoringClassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(tutoringEnrollmentRepository.countByClassId(any())).thenReturn(0L);
            lenient().when(tutoringEnrollmentRepository.findByClassIdAndUserId(any(), any()))
                .thenReturn(Optional.empty());
        }

        @Test
        void tutorCanCreateClass() {
            ResponseEntity<?> res = controller.createClass(authFor(alice), validCreateBody());
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("title")).isEqualTo("Advanced Java");
            verify(tutoringClassRepository).save(any());
            verify(tutoringAuditFacade).classCreated(eq(alice), argThat(createdClass ->
                "CS5000".equals(createdClass.getModuleCode())
                    && "online".equals(createdClass.getMode())
                    && Short.valueOf((short) 5).equals(createdClass.getMaxStudents())
            ));
        }

        @Test
        void missingTitleReturns400() {
            Map<String, Object> body = validCreateBody();
            body.remove("title");
            ResponseEntity<?> res = controller.createClass(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Class title is required");
        }

        @Test
        void missingModuleCodeReturns400() {
            Map<String, Object> body = validCreateBody();
            body.remove("moduleCode");
            ResponseEntity<?> res = controller.createClass(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.createClass(unknownAuth, validCreateBody());
            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }

        private Map<String, Object> validCreateBody() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Advanced Java");
            body.put("moduleCode", "CS5000");
            body.put("schedule", "Fridays 6pm");
            body.put("mode", "online");
            body.put("meetingLink", "https://zoom.us/java");
            body.put("maxStudents", 5);
            return body;
        }
    }

    @Nested
    @DisplayName("DELETE /api/tutoring/classes/{id}")
    class DeleteClass {
        UUID classId;
        TutoringClass tutoringClass;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();
            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setCreatedBy(alice.getId());

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
        }

        @Test
        void creatorCanDeleteClass() {
            ResponseEntity<?> res = controller.deleteClass(classId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("deleted")).isEqualTo(true);
            verify(tutoringClassRepository).delete(tutoringClass);
            verify(tutoringEnrollmentRepository).deleteByClassId(classId);
            verify(peerFeedbackRepository).deleteByPeerTutorGroupId(classId);
            verify(tutoringAuditFacade).classDeleted(alice, classId);
        }

        @Test
        void nonCreatorCannotDeleteClassReturns403() {
            ResponseEntity<?> res = controller.deleteClass(classId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
            verify(tutoringClassRepository, never()).delete(any());
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.deleteClass(unknownId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /api/tutoring/classes/{id}/enroll")
    class Enroll {
        UUID classId;
        TutoringClass tutoringClass;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();
            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setCreatedBy(bob.getId()); // bob is tutor
            tutoringClass.setMaxStudents((short) 5);

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
        }

        @Test
        void studentCanEnroll() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(2L);

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("enrolled")).isEqualTo(true);
            verify(tutoringEnrollmentRepository).save(any(TutoringEnrollment.class));
            verify(tutoringAuditFacade).classEnrolled(alice, classId, 3L);
        }

        @Test
        void alreadyEnrolledReturnsAlreadyEnrolled() {
            TutoringEnrollment existing = new TutoringEnrollment();
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.of(existing));

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("alreadyEnrolled")).isEqualTo(true);
            verify(tutoringEnrollmentRepository, never()).save(any());
        }

        @Test
        void tutorCannotEnrollInOwnClass() {
            ResponseEntity<?> res = controller.enroll(classId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Tutor cannot enroll in their own class");
        }

        @Test
        void fullClassReturns400() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(5L); // equals maxStudents

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Tutoring class is full");
        }
    }

    @Nested
    @DisplayName("GET /api/tutoring/classes/{id}/feedback")
    class GetClassFeedback {
        UUID classId;
        TutoringClass tutoringClass;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();
            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setCreatedBy(alice.getId()); // alice is tutor

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
        }

        @Test
        void tutorCanViewFeedback() {
            when(peerFeedbackRepository.findByPeerTutorGroupIdOrderByCreatedAtDesc(classId))
                .thenReturn(Collections.emptyList());
            ResponseEntity<?> res = controller.getClassFeedback(classId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat((List<?>) res.getBody()).isEmpty();
            verify(tutoringAuditFacade).feedbackViewed(alice, classId, 0);
        }

        @Test
        void nonTutorCannotViewFeedbackReturns403() {
            ResponseEntity<?> res = controller.getClassFeedback(classId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("POST /api/tutoring/classes/{id}/feedback - rating validation")
    class FeedbackRatingValidation {
        UUID classId;
        TutoringClass tutoringClass;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();
            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setCreatedBy(bob.getId());

            TutoringEnrollment enrollment = new TutoringEnrollment();
            enrollment.setClassId(classId);
            enrollment.setUserId(alice.getId());

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
            lenient().when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.of(enrollment));
            lenient().when(peerFeedbackRepository
                .existsBySessionIdAndReviewerIdAndRevieweeId(classId, alice.getId(), bob.getId()))
                .thenReturn(false);
        }

        @Test
        void ratingAbove5IsRejected() {
            Map<String, Object> body = validFeedbackBody(bob.getId());
            body.put("overallRating", 6);
            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void ratingBelowOneIsRejected() {
            Map<String, Object> body = validFeedbackBody(bob.getId());
            body.put("communication", 0);
            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void missingRatingIsRejected() {
            Map<String, Object> body = validFeedbackBody(bob.getId());
            body.remove("helpfulness");
            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            verify(peerFeedbackRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("POST /api/tutoring/classes/{id}/leave")
    class LeaveClass {
        private UUID classId;
        private TutoringClass tutoringClass;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();

            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setCreatedBy(bob.getId()); // Bob is the tutor

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
        }

        @Test
        void enrolledStudentLeavesSuccessfully() {
            TutoringEnrollment enrollment = new TutoringEnrollment();
            enrollment.setClassId(classId);
            enrollment.setUserId(alice.getId());

            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.of(enrollment));
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(3L);

            ResponseEntity<?> response = controller.leave(classId, authFor(alice));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<?, ?> payload = (Map<?, ?>) response.getBody();
            assertThat(payload.get("enrolled")).isEqualTo(false);
            assertThat(payload.get("enrolledCount")).isEqualTo(2L);
            verify(tutoringEnrollmentRepository).deleteByClassIdAndUserId(classId, alice.getId());
            verify(tutoringAuditFacade).classLeft(alice, classId, 2L);
        }

        @Test
        void notEnrolledStudentLeaveReturnsAlreadyLeft() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.leave(classId, authFor(alice));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo(Map.of("alreadyLeft", true));
            verify(tutoringEnrollmentRepository, never()).deleteByClassIdAndUserId(any(), any());
        }

        @Test
        void tutorCannotLeaveOwnClass() {
            when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));

            ResponseEntity<?> response = controller.leave(classId, authFor(bob));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Tutor cannot leave their own class"));
            verify(tutoringEnrollmentRepository, never()).deleteByClassIdAndUserId(any(), any());
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.leave(unknownId, authFor(alice));

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Tutoring class not found"));
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.leave(classId, unknownAuth);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "User not found"));
        }
    }

    private Map<String, Object> validFeedbackBody(UUID revieweeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("revieweeId", revieweeId.toString());
        body.put("overallRating", 4);
        body.put("preparedness", 4);
        body.put("communication", 5);
        body.put("helpfulness", 4);
        body.put("reliability", 3);
        body.put("strengths", "Explains concepts clearly.");
        body.put("anonymousToPeer", true);
        return body;
    }
}
