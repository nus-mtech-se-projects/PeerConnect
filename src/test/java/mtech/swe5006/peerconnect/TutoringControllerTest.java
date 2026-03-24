package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.TutoringController;
import mtech.swe5006.peerconnect.data.sql.*;
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