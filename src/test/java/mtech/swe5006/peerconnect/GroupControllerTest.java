package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.GroupController;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.data.sql.StudySessionRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.AuditService;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    JdbcTemplate jdbcTemplate;
    @Mock
    AuditService auditService;

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
            ArgumentCaptor<StudyGroup> groupCaptor = ArgumentCaptor.forClass(StudyGroup.class);
            verify(groupRepository).save(groupCaptor.capture());
            assertThat(groupCaptor.getValue().getCourseId()).isNull();
            verify(auditService).record(
                eq("STUDY_GROUP_CREATED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                isNull(),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details ->
                    Boolean.FALSE.equals(details.get("approvalRequired"))
                        && "online".equals(details.get("studyMode")))
            );
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
            verify(auditService).record(
                eq("GROUP_MEMBER_ADDED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                eq(groupId),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details -> "approved".equals(details.get("membershipStatus")))
            );

            // already member — controller returns 200 with alreadyJoined:true
            res = controller.joinGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @Test
        void leaveGroup() {
            StudyGroupMember membership = new StudyGroupMember();
            membership.setRole("member");
            membership.setMembershipStatus("approved");
            when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId()))
                .thenReturn(Optional.of(membership));
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(0L);

            ResponseEntity<?> res = controller.leaveGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(auditService).record(
                eq("GROUP_MEMBER_LEFT"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                eq(groupId),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                eq(Map.of())
            );
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/join - guards")
    class JoinGroupGuards {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(bob.getId());
            group.setMaxMembers((short) 2);
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId()))
                .thenReturn(Optional.empty());
        }

        @Test
        void joinDissolvedGroupReturns400() {
            group.setStatus("dissolved");
            ResponseEntity<?> res = controller.joinGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Group is dissolved");
        }

        @Test
        void joinFullGroupReturns400() {
            group.setStatus("active");
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(2L);
            ResponseEntity<?> res = controller.joinGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Group is full");
        }

        @Test
        void joinWithApprovalRequiredSetsPendingStatus() {
            group.setStatus("active");
            group.setApprovalRequired(true);
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(0L);

            controller.joinGroup(groupId, authFor(alice));

            ArgumentCaptor<StudyGroupMember> cap = ArgumentCaptor.forClass(StudyGroupMember.class);
            verify(memberRepository).save(cap.capture());
            assertThat(cap.getValue().getMembershipStatus()).isEqualTo("pending");
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/leave - guards")
    class LeaveGroupGuards {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        }

        @Test
        void ownerCannotLeaveOwnGroup() {
            StudyGroupMember ownerMembership = new StudyGroupMember();
            ownerMembership.setRole("owner");
            ownerMembership.setMembershipStatus("approved");
            when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId()))
                .thenReturn(Optional.of(ownerMembership));

            ResponseEntity<?> res = controller.leaveGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Group owner cannot leave the group");
        }
    }

    @Nested
    @DisplayName("PUT /api/groups/{id}")
    class UpdateGroup {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setStudyMode("online");
            group.setStatus("active");
            group.setMaxMembers((short) 10);
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(memberRepository.findByGroupId(groupId)).thenReturn(Collections.emptyList());
            lenient().when(studySessionRepository.findByGroupIdOrderByStartsAtAsc(groupId)).thenReturn(Collections.emptyList());
            lenient().when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(1L);
        }

        @Test
        void adminCanUpdateGroup() {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "Updated Name");
            body.put("moduleCode", "CS9000");
            body.put("description", "Updated desc");
            body.put("preferredSchedule", "2026-05-01T10:00:00");
            body.put("meetingLink", "https://zoom.us/updated");

            ResponseEntity<?> res = controller.updateGroup(groupId, authFor(alice), body);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("name")).isEqualTo("Updated Name");
        }

        @Test
        void updateWithNoExplicitCourseIdKeepsCourseIdNull() {
            group.setCourseId(null);

            Map<String, Object> body = new HashMap<>();
            body.put("name", "Updated Name");
            body.put("moduleCode", "CS9000");
            body.put("description", "Updated desc");
            body.put("preferredSchedule", "2026-05-01T10:00:00");
            body.put("meetingLink", "https://zoom.us/updated");

            ResponseEntity<?> res = controller.updateGroup(groupId, authFor(alice), body);

            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            ArgumentCaptor<StudyGroup> groupCaptor = ArgumentCaptor.forClass(StudyGroup.class);
            verify(groupRepository, atLeastOnce()).save(groupCaptor.capture());
            assertThat(groupCaptor.getValue().getCourseId()).isNull();
        }

        @Test
        void nonAdminCannotUpdateGroup() {
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.updateGroup(groupId, authFor(bob), new HashMap<>());
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("DELETE /api/groups/{id}")
    class DeleteGroup {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        }

        @Test
        void adminCanDeleteGroup() {
            ResponseEntity<?> res = controller.deleteGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("deleted")).isEqualTo(true);
        }

        @Test
        void nonAdminCannotDeleteGroup() {
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.deleteGroup(groupId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/dissolve")
    class DissolveGroup {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        }

        @Test
        void adminCanDissolveGroup() {
            ResponseEntity<?> res = controller.dissolveGroup(groupId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("dissolved")).isEqualTo(true);
        }

        @Test
        void nonAdminCannotDissolveGroup() {
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.dissolveGroup(groupId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/members/invite")
    class InviteMember {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setMaxMembers((short) 10);
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        }

        @Test
        void adminCanInviteNewMember() {
            when(userRepository.findByEmail(charlie.getEmail())).thenReturn(Optional.of(charlie));
            when(memberRepository.findByGroupIdAndUserId(groupId, charlie.getId())).thenReturn(Optional.empty());
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(1L);

            ResponseEntity<?> res = controller.inviteMember(groupId, authFor(alice), Map.of("email", charlie.getEmail()));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("invited")).isEqualTo(true);

            ArgumentCaptor<StudyGroupMember> cap = ArgumentCaptor.forClass(StudyGroupMember.class);
            verify(memberRepository).save(cap.capture());
            assertThat(cap.getValue().getMembershipStatus()).isEqualTo("invited");
            verify(auditService).record(
                eq("GROUP_MEMBER_INVITED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                eq(groupId),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details -> charlie.getId().toString().equals(details.get("targetUserId")))
            );
        }

        @Test
        void inviteAlreadyMemberReturnsInvitedFalse() {
            when(userRepository.findByEmail(charlie.getEmail())).thenReturn(Optional.of(charlie));
            StudyGroupMember existing = new StudyGroupMember();
            existing.setMembershipStatus("approved");
            when(memberRepository.findByGroupIdAndUserId(groupId, charlie.getId()))
                .thenReturn(Optional.of(existing));

            ResponseEntity<?> res = controller.inviteMember(groupId, authFor(alice), Map.of("email", charlie.getEmail()));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("invited")).isEqualTo(false);
        }

        @Test
        void inviteToFullGroupReturns400() {
            when(userRepository.findByEmail(charlie.getEmail())).thenReturn(Optional.of(charlie));
            when(memberRepository.findByGroupIdAndUserId(groupId, charlie.getId())).thenReturn(Optional.empty());
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(10L);

            ResponseEntity<?> res = controller.inviteMember(groupId, authFor(alice), Map.of("email", charlie.getEmail()));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/members/{userId}/approve")
    class ApproveMember {
        UUID groupId;
        StudyGroup group;
        StudyGroupMember pendingMembership;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setMaxMembers((short) 10);

            pendingMembership = new StudyGroupMember();
            pendingMembership.setGroupId(groupId);
            pendingMembership.setUserId(charlie.getId());
            pendingMembership.setMembershipStatus("pending");

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(memberRepository.findByGroupIdAndUserId(groupId, charlie.getId()))
                .thenReturn(Optional.of(pendingMembership));
            lenient().when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(1L);
            lenient().when(memberRepository.findByGroupId(groupId)).thenReturn(Collections.emptyList());
            lenient().when(studySessionRepository.findByGroupIdOrderByStartsAtAsc(groupId)).thenReturn(Collections.emptyList());
            lenient().when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void adminCanApprovePendingMember() {
            ResponseEntity<?> res = controller.approveMember(groupId, charlie.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("approved")).isEqualTo(true);
            assertThat(pendingMembership.getMembershipStatus()).isEqualTo("approved");
            verify(auditService).record(
                eq("GROUP_MEMBER_ADDED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                eq(groupId),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details ->
                    charlie.getId().toString().equals(details.get("targetUserId"))
                        && Boolean.TRUE.equals(details.get("approvedByAdmin")))
            );
        }

        @Test
        void alreadyApprovedIsIdempotent() {
            pendingMembership.setMembershipStatus("approved");
            ResponseEntity<?> res = controller.approveMember(groupId, charlie.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("alreadyApproved")).isEqualTo(true);
        }

        @Test
        void approveOnFullGroupReturns400() {
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(10L);
            ResponseEntity<?> res = controller.approveMember(groupId, charlie.getId(), authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("DELETE /api/groups/{id}/members/{userId}")
    class RemoveMember {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(2L);
            lenient().when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void adminCanRemoveMember() {
            StudyGroupMember membership = new StudyGroupMember();
            membership.setUserId(charlie.getId());
            membership.setMembershipStatus("approved");
            when(memberRepository.findByGroupIdAndUserId(groupId, charlie.getId()))
                .thenReturn(Optional.of(membership));

            ResponseEntity<?> res = controller.removeMember(groupId, charlie.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("removed")).isEqualTo(true);
            verify(memberRepository).deleteByGroupIdAndUserId(groupId, charlie.getId());
            verify(auditService).record(
                eq("GROUP_MEMBER_REMOVED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                eq(groupId),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details -> charlie.getId().toString().equals(details.get("targetUserId")))
            );
        }

        @Test
        void cannotRemoveOwnerDirectly() {
            ResponseEntity<?> res = controller.removeMember(groupId, alice.getId(), authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error"))
                .isEqualTo("Use transfer ownership before removing owner");
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/transfer-ownership")
    class TransferOwnership {
        UUID groupId;
        StudyGroup group;
        StudyGroupMember aliceMembership;
        StudyGroupMember charlieMembership;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());

            aliceMembership = new StudyGroupMember();
            aliceMembership.setUserId(alice.getId());
            aliceMembership.setRole("owner");
            aliceMembership.setMembershipStatus("approved");

            charlieMembership = new StudyGroupMember();
            charlieMembership.setUserId(charlie.getId());
            charlieMembership.setRole("member");
            charlieMembership.setMembershipStatus("approved");

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId()))
                .thenReturn(Optional.of(aliceMembership));
            lenient().when(memberRepository.findByGroupIdAndUserId(groupId, charlie.getId()))
                .thenReturn(Optional.of(charlieMembership));
            lenient().when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void ownerCanTransferOwnership() {
            ResponseEntity<?> res = controller.transferOwnership(groupId, authFor(alice),
                Map.of("newOwnerUserId", charlie.getId().toString()));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("transferred")).isEqualTo(true);
            assertThat(charlieMembership.getRole()).isEqualTo("owner");
            assertThat(aliceMembership.getRole()).isEqualTo("admin");
            verify(auditService).record(
                eq("GROUP_OWNERSHIP_TRANSFERRED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_GROUP"),
                eq(groupId),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details -> charlie.getId().toString().equals(details.get("newOwnerUserId")))
            );
        }

        @Test
        void nonOwnerCannotTransferOwnership() {
            // bob is not the owner (group.createdBy = alice); isOwner check fails immediately
            ResponseEntity<?> res = controller.transferOwnership(groupId, authFor(bob),
                Map.of("newOwnerUserId", charlie.getId().toString()));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("POST /api/groups/{id}/sessions")
    class CreateSession {
        UUID groupId;
        StudyGroup group;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setStudyMode("online");
            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(studySessionRepository.save(any())).thenAnswer(inv -> {
                StudySession s = inv.getArgument(0);
                if (s.getId() == null) s.setId(UUID.randomUUID());
                return s;
            });
        }

        @Test
        void adminCanCreateSession() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Week 1 Session");
            body.put("startsAt", "2026-05-01T10:00:00");
            body.put("meetingLink", "https://zoom.us/session1");

            ResponseEntity<?> res = controller.createSession(groupId, authFor(alice), body);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("title")).isEqualTo("Week 1 Session");
            verify(auditService).record(
                eq("STUDY_SESSION_CREATED"),
                eq(alice.getId()),
                eq(alice.getEmail()),
                eq("STUDY_SESSION"),
                any(UUID.class),
                eq("SUCCESS"),
                isNull(),
                isNull(),
                argThat(details -> groupId.toString().equals(details.get("groupId")))
            );
        }

        @Test
        void missingTitleReturns400() {
            Map<String, Object> body = Map.of("startsAt", "2026-05-01T10:00:00", "meetingLink", "https://zoom.us/x");
            ResponseEntity<?> res = controller.createSession(groupId, authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("Session title is required");
        }

        @Test
        void endsAtBeforeStartsAtReturns400() {
            Map<String, Object> body = new HashMap<>();
            body.put("title", "Bad Session");
            body.put("startsAt", "2026-05-01T10:00:00");
            body.put("endsAt", "2026-05-01T09:00:00");
            body.put("meetingLink", "https://zoom.us/x");
            ResponseEntity<?> res = controller.createSession(groupId, authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
            assertThat(((Map<?, ?>) res.getBody()).get("error")).isEqualTo("endsAt must be after startsAt");
        }

        @Test
        void nonAdminCannotCreateSession() {
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.createSession(groupId, authFor(bob), new HashMap<>());
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("PUT /api/groups/{id}/sessions/{sessionId}")
    class UpdateSession {
        UUID groupId;
        UUID sessionId;
        StudyGroup group;
        StudySession session;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            sessionId = UUID.randomUUID();

            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());

            session = new StudySession();
            session.setId(sessionId);
            session.setGroupId(groupId);
            session.setTitle("Original Title");
            session.setStartsAt(LocalDateTime.parse("2026-05-01T10:00:00"));
            session.setCreatedBy(alice.getId());

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            lenient().when(studySessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void adminCanUpdateSession() {
            ResponseEntity<?> res = controller.updateSession(groupId, sessionId, authFor(alice),
                Map.of("title", "Updated Title"));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("title")).isEqualTo("Updated Title");
        }

        @Test
        void sessionNotFoundReturns404() {
            UUID unknownSession = UUID.randomUUID();
            when(studySessionRepository.findById(unknownSession)).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.updateSession(groupId, unknownSession, authFor(alice), new HashMap<>());
            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("DELETE /api/groups/{id}/sessions/{sessionId}")
    class DeleteSession {
        UUID groupId;
        UUID sessionId;
        StudyGroup group;
        StudySession session;

        @BeforeEach
        void init() {
            groupId = UUID.randomUUID();
            sessionId = UUID.randomUUID();

            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());

            session = new StudySession();
            session.setId(sessionId);
            session.setGroupId(groupId);

            lenient().when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
            lenient().when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
            lenient().when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            lenient().when(studySessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        }

        @Test
        void adminCanDeleteSession() {
            ResponseEntity<?> res = controller.deleteSession(groupId, sessionId, authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(((Map<?, ?>) res.getBody()).get("deleted")).isEqualTo(true);
            verify(studySessionRepository).delete(session);
        }

        @Test
        void sessionNotFoundReturns404() {
            UUID unknownSession = UUID.randomUUID();
            when(studySessionRepository.findById(unknownSession)).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.deleteSession(groupId, unknownSession, authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void nonAdminCannotDeleteSession() {
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.deleteSession(groupId, sessionId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }
    }
}
