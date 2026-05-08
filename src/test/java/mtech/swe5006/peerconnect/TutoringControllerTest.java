package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.TutoringController;
import mtech.swe5006.peerconnect.data.sql.*;
import mtech.swe5006.peerconnect.service.EmailService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
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
    private JdbcTemplate jdbcTemplate;
    @Mock
    private EmailService emailService;
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

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getAllClasses(unknownAuth);

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "User not found"));
            verify(tutoringClassRepository, never()).findAllByOrderByCreatedAtDesc();
        }

        @Test
        void returnsPayloadWhenTutorLookupAndEnrollmentRepositoryFail() {
            TutoringClass tc = new TutoringClass();
            tc.setId(UUID.randomUUID());
            tc.setTitle("Java Basics");
            tc.setCreatedBy(alice.getId());

            when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            when(tutoringClassRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(tc));
            when(userRepository.findById(alice.getId())).thenReturn(Optional.empty());
            when(tutoringEnrollmentRepository.countByClassId(tc.getId())).thenThrow(new RuntimeException("count unavailable"));
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(tc.getId(), alice.getId()))
                .thenThrow(new RuntimeException("lookup unavailable"));

            ResponseEntity<?> res = controller.getAllClasses(authFor(alice));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            Map<?, ?> payload = (Map<?, ?>) ((List<?>) res.getBody()).get(0);
            assertThat(payload.get("isTutor")).isEqualTo(true);
            assertThat(payload.get("enrolled")).isEqualTo(false);
            assertThat(payload.get("enrolledCount")).isEqualTo(0L);
            assertThat(payload.get("tutorName")).isNull();
            assertThat(payload.get("tutorId")).isNull();
            assertThat(payload.get("tutorEmail")).isNull();
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
            verify(emailService).sendTutoringClassCreated(
                eq("alice@u.nus.edu"),
                eq("Alice Tan"),
                eq("Advanced Java"),
                eq("CS5000"),
                isNull(),
                eq("Fridays 6pm"),
                eq("online"),
                isNull(),
                eq("https://zoom.us/java")
            );
        }

        @Test
        void missingTitleReturns400() {
            Map<String, Object> body = validCreateBody();
            body.remove("title");
            ResponseEntity<?> res = controller.createClass(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Class title is required");
            verify(emailService, never()).sendTutoringClassCreated(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void missingModuleCodeReturns400() {
            Map<String, Object> body = validCreateBody();
            body.remove("moduleCode");
            ResponseEntity<?> res = controller.createClass(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void missingScheduleReturns400() {
            Map<String, Object> body = validCreateBody();
            body.remove("schedule");

            ResponseEntity<?> res = controller.createClass(authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Schedule is required");
        }

        @Test
        void invalidModeReturns400() {
            Map<String, Object> body = validCreateBody();
            body.put("mode", "virtual");

            ResponseEntity<?> res = controller.createClass(authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Mode must be one of: online, in-person, hybrid");
        }

        @Test
        void missingLocationForInPersonReturns400() {
            Map<String, Object> body = validCreateBody();
            body.put("mode", "in-person");
            body.remove("meetingLink");

            ResponseEntity<?> res = controller.createClass(authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Location is required for in-person/hybrid classes");
        }

        @Test
        void maxStudentsBelowOneReturns400() {
            Map<String, Object> body = validCreateBody();
            body.put("maxStudents", 0);

            ResponseEntity<?> res = controller.createClass(authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Max students must be at least 1");
        }

        @Test
        void inPersonClassCanBeCreatedWithoutMeetingLink() {
            Map<String, Object> body = validCreateBody();
            body.put("mode", "in-person");
            body.put("location", "COM1-0208");
            body.remove("meetingLink");

            ResponseEntity<?> res = controller.createClass(authFor(alice), body);

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            Map<?, ?> payload = (Map<?, ?>) res.getBody();
            assertThat(payload.get("mode")).isEqualTo("in-person");
            assertThat(payload.get("location")).isEqualTo("COM1-0208");
            assertThat(payload.get("meetingLink")).isNull();
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.createClass(unknownAuth, validCreateBody());
            assertThat(res.getStatusCode().value()).isEqualTo(404);
            verify(emailService, never()).sendTutoringClassCreated(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void jpaSaveFailureFallsBackToJdbc() {
            when(tutoringClassRepository.save(any())).thenThrow(new RuntimeException("jpa down"));

            ResponseEntity<?> res = controller.createClass(authFor(alice), validCreateBody());

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(jdbcTemplate).update(any(String.class), any(Object[].class));
            verify(tutoringAuditFacade).classCreated(eq(alice), any(TutoringClass.class));
        }

        @Test
        void jdbcFallbackFailureReturns500() {
            when(tutoringClassRepository.save(any())).thenThrow(new RuntimeException("jpa down"));
            when(jdbcTemplate.update(any(String.class), any(Object[].class)))
                .thenThrow(new RuntimeException("jdbc down"));

            ResponseEntity<?> res = controller.createClass(authFor(alice), validCreateBody());

            assertThat(res.getStatusCode().value()).isEqualTo(500);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Failed to save tutoring class: jdbc down");
            verify(tutoringAuditFacade, never()).classCreated(any(), any());
        }

        @Test
        void emailFailureStillReturnsCreatedClass() {
            doThrow(new RuntimeException("mail down")).when(emailService)
                .sendTutoringClassCreated(any(), any(), any(), any(), any(), any(), any(), any(), any());

            ResponseEntity<?> res = controller.createClass(authFor(alice), validCreateBody());

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(tutoringAuditFacade).classCreated(eq(alice), any(TutoringClass.class));
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
    @DisplayName("PUT /api/tutoring/classes/{id}")
    class UpdateClass {
        private UUID classId;
        private TutoringClass tutoringClass;

        @BeforeEach
        void init() {
            classId = UUID.randomUUID();
            tutoringClass = new TutoringClass();
            tutoringClass.setId(classId);
            tutoringClass.setTitle("Advanced Java");
            tutoringClass.setModuleCode("CS5000");
            tutoringClass.setTopic("Concurrency");
            tutoringClass.setDescription("Weekly peer tutoring");
            tutoringClass.setSchedule("Fridays 6pm");
            tutoringClass.setMode("online");
            tutoringClass.setMeetingLink("https://zoom.us/java");
            tutoringClass.setMaxStudents((short) 5);
            tutoringClass.setCreatedBy(alice.getId());

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(tutoringClassRepository.findById(classId)).thenReturn(Optional.of(tutoringClass));
            lenient().when(tutoringClassRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(0L);
            lenient().when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());
            lenient().when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        }

        @Test
        void creatorCanUpdateClass() {
            TutoringEnrollment enrollment = new TutoringEnrollment();
            enrollment.setClassId(classId);
            enrollment.setUserId(bob.getId());
            when(tutoringEnrollmentRepository.findByClassId(classId)).thenReturn(List.of(enrollment));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

            Map<String, Object> body = new HashMap<>();
            body.put("title", "Advanced Java II");
            body.put("mode", "hybrid");
            body.put("location", "COM1-0208");
            body.put("meetingLink", "https://zoom.us/java-2");
            body.put("maxStudents", 8);

            ResponseEntity<?> res = controller.updateClass(classId, authFor(alice), body);

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            Map<?, ?> payload = (Map<?, ?>) res.getBody();
            assertThat(payload.get("title")).isEqualTo("Advanced Java II");
            assertThat(payload.get("mode")).isEqualTo("hybrid");
            assertThat(payload.get("location")).isEqualTo("COM1-0208");
            assertThat(payload.get("meetingLink")).isEqualTo("https://zoom.us/java-2");
            assertThat(payload.get("maxStudents")).isEqualTo((short) 8);
            verify(tutoringClassRepository).save(tutoringClass);
            verify(emailService).sendTutoringClassUpdated(
                aryEq(new String[]{"bob@u.nus.edu"}),
                eq("Advanced Java II"),
                eq("CS5000"),
                eq("Concurrency"),
                eq("Fridays 6pm"),
                eq("Alice Tan"),
                eq("alice@u.nus.edu"),
                eq("hybrid"),
                eq("COM1-0208"),
                eq("https://zoom.us/java-2"),
                eq("Weekly peer tutoring")
            );
        }

        @Test
        void updateFallsBackToTutorWhenNoStudents() {
            when(tutoringEnrollmentRepository.findByClassId(classId)).thenReturn(List.of());

            ResponseEntity<?> res = controller.updateClass(classId, authFor(alice), Map.of("title", "Advanced Java II"));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(emailService).sendTutoringClassUpdated(
                aryEq(new String[]{"alice@u.nus.edu"}),
                eq("Advanced Java II"),
                eq("CS5000"),
                eq("Concurrency"),
                eq("Fridays 6pm"),
                eq("Alice Tan"),
                eq("alice@u.nus.edu"),
                eq("online"),
                isNull(),
                eq("https://zoom.us/java"),
                eq("Weekly peer tutoring")
            );
        }

        @Test
        void nonCreatorCannotUpdateClass() {
            ResponseEntity<?> res = controller.updateClass(classId, authFor(bob), Map.of("title", "Hack"));

            assertThat(res.getStatusCode().value()).isEqualTo(403);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Not authorized to update this tutoring class");
            verify(tutoringClassRepository, never()).save(any());
            verify(emailService, never()).sendTutoringClassUpdated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void missingMeetingLinkForOnlineReturns400() {
            Map<String, Object> body = new HashMap<>();
            body.put("meetingLink", "");

            ResponseEntity<?> res = controller.updateClass(classId, authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Meeting link is required for online/hybrid classes");
            verify(tutoringClassRepository, never()).save(any());
            verify(emailService, never()).sendTutoringClassUpdated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.updateClass(unknownId, authFor(alice), Map.of("title", "New"));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Tutoring class not found");
            verify(tutoringClassRepository, never()).save(any());
            verify(emailService, never()).sendTutoringClassUpdated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.updateClass(classId, unknownAuth, Map.of("title", "New"));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "User not found"));
            verify(tutoringClassRepository, never()).save(any());
        }

        @Test
        void emailFailureStillReturnsUpdatedClass() {
            when(tutoringEnrollmentRepository.findByClassId(classId)).thenReturn(List.of());
            doThrow(new RuntimeException("mail down")).when(emailService)
                .sendTutoringClassUpdated(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

            ResponseEntity<?> res = controller.updateClass(classId, authFor(alice), Map.of("title", "Advanced Java II"));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("title")).isEqualTo("Advanced Java II");
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
            TutoringEnrollment enrollment = new TutoringEnrollment();
            enrollment.setClassId(classId);
            enrollment.setUserId(bob.getId());

            when(tutoringEnrollmentRepository.findByClassId(classId)).thenReturn(List.of(enrollment));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

            ResponseEntity<?> res = controller.deleteClass(classId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("deleted")).isEqualTo(true);
            verify(tutoringClassRepository).delete(tutoringClass);
            verify(tutoringEnrollmentRepository).deleteByClassId(classId);
            verify(peerFeedbackRepository).deleteByPeerTutorGroupId(classId);
            verify(tutoringAuditFacade).classDeleted(alice, classId);
            verify(emailService).sendTutoringClassDeleted(
                aryEq(new String[]{"bob@u.nus.edu"}),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("Alice Tan"),
                eq("alice@u.nus.edu"),
                isNull(),
                isNull(),
                isNull()
            );
        }

        @Test
        void deleteFallsBackToTutorWhenNoStudents() {
            when(tutoringEnrollmentRepository.findByClassId(classId)).thenReturn(List.of());

            ResponseEntity<?> res = controller.deleteClass(classId, authFor(alice));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(emailService).sendTutoringClassDeleted(
                aryEq(new String[]{"alice@u.nus.edu"}),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("Alice Tan"),
                eq("alice@u.nus.edu"),
                isNull(),
                isNull(),
                isNull()
            );
        }

        @Test
        void nonCreatorCannotDeleteClassReturns403() {
            ResponseEntity<?> res = controller.deleteClass(classId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
            verify(tutoringClassRepository, never()).delete(any());
            verify(emailService, never()).sendTutoringClassDeleted(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.deleteClass(unknownId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(404);
            verify(emailService, never()).sendTutoringClassDeleted(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.deleteClass(classId, unknownAuth);

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "User not found"));
            verify(tutoringClassRepository, never()).delete(any());
        }

        @Test
        void deleteContinuesWhenEnrollmentDeleteAndEmailFail() {
            when(tutoringEnrollmentRepository.findByClassId(classId)).thenThrow(new RuntimeException("list unavailable"));
            doThrow(new RuntimeException("delete unavailable")).when(tutoringEnrollmentRepository).deleteByClassId(classId);
            doThrow(new RuntimeException("mail down")).when(emailService)
                .sendTutoringClassDeleted(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

            ResponseEntity<?> res = controller.deleteClass(classId, authFor(alice));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("deleted")).isEqualTo(true);
            verify(tutoringClassRepository).delete(tutoringClass);
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
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("enrolled")).isEqualTo(true);
            verify(tutoringEnrollmentRepository).save(any(TutoringEnrollment.class));
            verify(tutoringAuditFacade).classEnrolled(alice, classId, 3L);
            verify(emailService).sendTutoringEnrollmentConfirmed(
                eq("alice@u.nus.edu"),
                eq("Alice Tan"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("Bob Lim"),
                eq("bob@u.nus.edu"),
                isNull(),
                isNull(),
                isNull()
            );
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
            verify(emailService, never()).sendTutoringEnrollmentConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void tutorCannotEnrollInOwnClass() {
            ResponseEntity<?> res = controller.enroll(classId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Tutor cannot enroll in their own class");
            verify(emailService, never()).sendTutoringEnrollmentConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void fullClassReturns400() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(5L); // equals maxStudents

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Tutoring class is full");
            verify(emailService, never()).sendTutoringEnrollmentConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.enroll(classId, unknownAuth);

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "User not found"));
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.enroll(unknownId, authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Tutoring class not found"));
        }

        @Test
        void nullMaxStudentsAllowsEnrollmentWhenCountIsHigh() {
            tutoringClass.setMaxStudents(null);
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(99L);
            when(userRepository.findById(bob.getId())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("enrolledCount")).isEqualTo(100L);
            verify(emailService).sendTutoringEnrollmentConfirmed(
                eq("alice@u.nus.edu"),
                eq("Alice Tan"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
            );
        }

        @Test
        void enrollmentStillSucceedsWhenSaveAndEmailFail() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenReturn(1L);
            doThrow(new RuntimeException("save unavailable")).when(tutoringEnrollmentRepository).save(any(TutoringEnrollment.class));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            doThrow(new RuntimeException("mail down")).when(emailService)
                .sendTutoringEnrollmentConfirmed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

            ResponseEntity<?> res = controller.enroll(classId, authFor(alice));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("enrolled")).isEqualTo(true);
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

        @Test
        void authenticationRequiredReturns401() {
            ResponseEntity<?> res = controller.getClassFeedback(classId, null);

            assertThat(res.getStatusCode().value()).isEqualTo(401);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Authentication required"));
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getClassFeedback(unknownId, authFor(alice));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Tutoring class not found"));
        }

        @Test
        void mapsFeedbackWithNullIdentifiersAndMissingUsers() {
            PeerFeedback feedback = new PeerFeedback();
            feedback.setOverallRating((short) 4);
            feedback.setPreparedness((short) 3);
            feedback.setCommunication((short) 5);
            feedback.setHelpfulness((short) 4);
            feedback.setReliability((short) 4);
            feedback.setAnonymousToPeer(null);

            when(peerFeedbackRepository.findByPeerTutorGroupIdOrderByCreatedAtDesc(classId))
                .thenReturn(List.of(feedback));

            ResponseEntity<?> res = controller.getClassFeedback(classId, authFor(alice));

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            Map<?, ?> row = (Map<?, ?>) ((List<?>) res.getBody()).get(0);
            assertThat(row.get("id")).isNull();
            assertThat(row.get("peerTutorGroupId")).isNull();
            assertThat(row.get("sessionId")).isNull();
            assertThat(row.get("revieweeId")).isNull();
            assertThat(row.get("revieweeName")).isNull();
            assertThat(row.get("reviewerName")).isNull();
            assertThat(row.get("reviewerEmail")).isNull();
            assertThat(row.get("anonymousToPeer")).isEqualTo(false);
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

        @Test
        void authenticationRequiredReturns401() {
            ResponseEntity<?> res = controller.submitFeedback(classId, null, validFeedbackBody(bob.getId()));

            assertThat(res.getStatusCode().value()).isEqualTo(401);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Authentication required"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.submitFeedback(unknownId, authFor(alice), validFeedbackBody(bob.getId()));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Tutoring class not found"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void notEnrolledReviewerReturns403() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), validFeedbackBody(bob.getId()));

            assertThat(res.getStatusCode().value()).isEqualTo(403);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Only enrolled students can submit feedback"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void missingRevieweeIdReturns400() {
            Map<String, Object> body = validFeedbackBody(bob.getId());
            body.remove("revieweeId");

            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "revieweeId is required"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void invalidRevieweeIdReturns400() {
            Map<String, Object> body = validFeedbackBody(bob.getId());
            body.put("revieweeId", "not-a-uuid");

            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), body);

            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Invalid revieweeId format"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void unknownRevieweeReturns404() {
            UUID unknownReviewee = UUID.randomUUID();
            when(userRepository.findById(unknownReviewee)).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.submitFeedback(classId, authFor(alice), validFeedbackBody(unknownReviewee));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
            assertThat(res.getBody()).isEqualTo(Map.of("error", "Reviewee not found"));
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
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

            ResponseEntity<?> response = controller.leave(classId, authFor(alice));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            Map<?, ?> payload = (Map<?, ?>) response.getBody();
            assertThat(payload.get("enrolled")).isEqualTo(false);
            assertThat(payload.get("enrolledCount")).isEqualTo(2L);
            verify(tutoringEnrollmentRepository).deleteByClassIdAndUserId(classId, alice.getId());
            verify(tutoringAuditFacade).classLeft(alice, classId, 2L);
            verify(emailService).sendTutoringStudentLeft(
                eq("bob@u.nus.edu"),
                eq("alice@u.nus.edu"),
                eq("Alice Tan"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
            );
        }

        @Test
        void notEnrolledStudentLeaveReturnsAlreadyLeft() {
            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.leave(classId, authFor(alice));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo(Map.of("alreadyLeft", true));
            verify(tutoringEnrollmentRepository, never()).deleteByClassIdAndUserId(any(), any());
            verify(emailService, never()).sendTutoringStudentLeft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void tutorCannotLeaveOwnClass() {
            when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));

            ResponseEntity<?> response = controller.leave(classId, authFor(bob));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Tutor cannot leave their own class"));
            verify(tutoringEnrollmentRepository, never()).deleteByClassIdAndUserId(any(), any());
            verify(emailService, never()).sendTutoringStudentLeft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void classNotFoundReturns404() {
            UUID unknownId = UUID.randomUUID();
            when(tutoringClassRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.leave(unknownId, authFor(alice));

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Tutoring class not found"));
            verify(emailService, never()).sendTutoringStudentLeft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void userNotFoundReturns404() {
            Authentication unknownAuth = mock(Authentication.class);
            when(unknownAuth.getName()).thenReturn("nobody@u.nus.edu");
            when(userRepository.findByEmail("nobody@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.leave(classId, unknownAuth);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "User not found"));
            verify(emailService, never()).sendTutoringStudentLeft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void leaveStillSucceedsWhenDeleteAndEmailFail() {
            TutoringEnrollment enrollment = new TutoringEnrollment();
            enrollment.setClassId(classId);
            enrollment.setUserId(alice.getId());

            when(tutoringEnrollmentRepository.findByClassIdAndUserId(classId, alice.getId()))
                .thenReturn(Optional.of(enrollment));
            when(tutoringEnrollmentRepository.countByClassId(classId)).thenThrow(new RuntimeException("count unavailable"));
            doThrow(new RuntimeException("delete unavailable")).when(tutoringEnrollmentRepository)
                .deleteByClassIdAndUserId(classId, alice.getId());
            when(userRepository.findById(bob.getId())).thenReturn(Optional.empty());
            doThrow(new RuntimeException("mail down")).when(emailService)
                .sendTutoringStudentLeft(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

            ResponseEntity<?> response = controller.leave(classId, authFor(alice));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) response.getBody()).get("enrolledCount")).isEqualTo(0L);
            verify(tutoringAuditFacade).classLeft(alice, classId, 0L);
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
