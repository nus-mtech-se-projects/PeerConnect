package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.GroupController;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.PeerFeedback;
import mtech.swe5006.peerconnect.data.sql.PeerFeedbackRepository;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.data.sql.StudySessionRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    @Mock
    StudyGroupRepository groupRepository;
    @Mock
    StudyGroupMemberRepository memberRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    StudySessionRepository studySessionRepository;
    @Mock
    PeerFeedbackRepository peerFeedbackRepository;
    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    GroupController controller;

    private User alice;
    private User bob;
    private User charlie;

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

        charlie = new User();
        charlie.setId(UUID.randomUUID());
        charlie.setEmail("charlie@u.nus.edu");
        charlie.setFirstName("Charlie");
        charlie.setLastName("Ng");
    }

    private Authentication authFor(User u) {
        Authentication a = mock(Authentication.class);
        when(a.getName()).thenReturn(u.getEmail());
        return a;
    }

    @Nested
    @DisplayName("POST /api/groups")
    class Create {
        @Test
        void ownerMembershipAdded() {
            Map<String,Object> body = new HashMap<>();
            body.put("name", "Test group");
            body.put("moduleCode", "CS5000");
            body.put("description", "A test group");
            body.put("preferredSchedule", "2026-04-01T18:00:00");
            body.put("meetingLink", "https://zoom.us/test");
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.findByGroupId(any())).thenReturn(Collections.emptyList());
            when(studySessionRepository.findByGroupIdOrderByStartsAtAsc(any())).thenReturn(Collections.emptyList());

            ResponseEntity<?> res = controller.createGroup(authFor(alice), body);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            ArgumentCaptor<StudyGroupMember> cap = ArgumentCaptor.forClass(StudyGroupMember.class);
            verify(memberRepository).save(cap.capture());
            assertThat(cap.getValue().getRole()).isEqualTo("owner");
            assertThat(cap.getValue().getUserId()).isEqualTo(alice.getId());
        }
    }

    @Nested
    @DisplayName("membership flows")
    class Membership {
        StudyGroup group;
        UUID groupId;

        @BeforeEach
        void initGroup() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setMaxMembers((short)2);
            group.setStatus("active");
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(memberRepository.countByGroupId(groupId)).thenReturn(0L);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
        }

        @Test
        void join_and_capacity_enforced() {
            StudyGroupMember existing = new StudyGroupMember();
            existing.setMembershipStatus("approved");
            when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existing));

            ResponseEntity<?> res = controller.joinGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            // already member — controller returns 200 with alreadyJoined:true
            res = controller.joinGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @Test
        void leaveGroup() {
            ResponseEntity<?> res = controller.leaveGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{groupId}/sessions/{sessionId}/feedback")
    class SubmitFeedback {
        private UUID groupId;
        private UUID sessionId;
        private StudyGroup group;
        private StudySession session;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            sessionId = UUID.randomUUID();

            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setStatus("active");
            group.setStudyMode("online");

            session = new StudySession();
            session.setId(sessionId);
            session.setGroupId(groupId);
            session.setTitle("Week 5 Review");
            session.setStartsAt(LocalDateTime.now().plusDays(1));
            session.setCreatedBy(alice.getId());

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            lenient().when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId()))
                .thenReturn(Optional.of(approvedMembership(groupId, alice.getId(), "owner")));
            lenient().when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId()))
                .thenReturn(Optional.of(approvedMembership(groupId, bob.getId(), "member")));
            lenient().when(peerFeedbackRepository.existsBySessionIdAndReviewerIdAndRevieweeId(sessionId, alice.getId(), bob.getId()))
                .thenReturn(false);
        }

        @Test
        void successfulFeedbackSubmissionReturnsSuccess() {
            when(peerFeedbackRepository.save(any(PeerFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, authFor(alice), validFeedbackBody(groupId, sessionId, bob.getId()));

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isInstanceOf(Map.class);
            Map<?, ?> payload = (Map<?, ?>) response.getBody();
            assertThat(payload.get("revieweeId")).isEqualTo(bob.getId().toString());
            assertThat(payload.get("revieweeName")).isEqualTo("Bob Lim");

            ArgumentCaptor<PeerFeedback> captor = ArgumentCaptor.forClass(PeerFeedback.class);
            verify(peerFeedbackRepository).save(captor.capture());
            assertThat(captor.getValue().getReviewerId()).isEqualTo(alice.getId());
            assertThat(captor.getValue().getRevieweeId()).isEqualTo(bob.getId());
            assertThat(captor.getValue().getSessionId()).isEqualTo(sessionId);
        }

        @Test
        void invalidGroupSessionRelationshipIsRejected() {
            UUID otherGroupId = UUID.randomUUID();
            session.setGroupId(otherGroupId);

            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, authFor(alice), validFeedbackBody(groupId, sessionId, bob.getId()));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Session does not belong to the specified group"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void selfReviewIsRejected() {
            Map<String, Object> body = validFeedbackBody(groupId, sessionId, alice.getId());

            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, authFor(alice), body);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "You cannot submit feedback for yourself"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void revieweeNotInSameApprovedGroupIsRejected() {
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId()))
                .thenReturn(Optional.of(pendingMembership(groupId, bob.getId())));

            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, authFor(alice), validFeedbackBody(groupId, sessionId, bob.getId()));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Reviewee must be an approved member of the group"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void duplicateSubmissionIsRejectedWithConflict() {
            when(peerFeedbackRepository.existsBySessionIdAndReviewerIdAndRevieweeId(sessionId, alice.getId(), bob.getId()))
                .thenReturn(true);

            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, authFor(alice), validFeedbackBody(groupId, sessionId, bob.getId()));

            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Feedback has already been submitted for this peer and session"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void invalidRatingValuesAreRejected() {
            Map<String, Object> body = validFeedbackBody(groupId, sessionId, bob.getId());
            body.put("preparedness", 6);

            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, authFor(alice), body);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "preparedness must be between 1 and 5"));
            verify(peerFeedbackRepository, never()).save(any());
        }

        @Test
        void unauthenticatedRequestIsRejected() {
            ResponseEntity<Map<String, Object>> response = controller.submitFeedback(groupId, sessionId, null, validFeedbackBody(groupId, sessionId, bob.getId()));

            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "Authentication required"));
            verify(peerFeedbackRepository, never()).save(any());
        }
    }

    private StudyGroupMember approvedMembership(UUID groupId, UUID userId, String role) {
        StudyGroupMember membership = new StudyGroupMember();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setRole(role);
        membership.setMembershipStatus("approved");
        return membership;
    }

    private StudyGroupMember pendingMembership(UUID groupId, UUID userId) {
        StudyGroupMember membership = new StudyGroupMember();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setRole("member");
        membership.setMembershipStatus("pending");
        return membership;
    }

    private Map<String, Object> validFeedbackBody(UUID groupId, UUID sessionId, UUID revieweeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId.toString());
        body.put("groupId", groupId.toString());
        body.put("revieweeId", revieweeId.toString());
        body.put("overallRating", 4);
        body.put("preparedness", 4);
        body.put("communication", 5);
        body.put("helpfulness", 4);
        body.put("reliability", 3);
        body.put("strengths", "Explains concepts clearly.");
        body.put("improvements", "Could prepare examples earlier.");
        body.put("anonymousToPeer", true);
        return body;
    }
}
