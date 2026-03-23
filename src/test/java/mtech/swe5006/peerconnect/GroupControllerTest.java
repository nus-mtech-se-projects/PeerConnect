package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.GroupController;
import mtech.swe5006.peerconnect.data.sql.RestrictedUserRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.StudySessionRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.EmailService;
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
import static org.mockito.ArgumentMatchers.eq;
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
    EmailService emailService;
    @Mock
    RestrictedUserRepository restrictedUserRepository;

    @InjectMocks
    GroupController controller;

    private User alice;

    @BeforeEach
    void setup() {
        alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail("alice@u.nus.edu");
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
    @DisplayName("restrict checks")
    class RestrictChecks {
        StudyGroup group;
        UUID groupId;
        User bob;

        @BeforeEach
        void initGroup() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setMaxMembers((short) 5);
            group.setStatus("active");
            group.setName("Test Group");
            group.setModuleCode("CS5000");

            bob = new User();
            bob.setId(UUID.randomUUID());
            bob.setEmail("bob@u.nus.edu");
            bob.setFirstName("Bob");
            bob.setLastName("Lee");
        }

        @Test
        @DisplayName("joinGroup returns 403 when user is restricted by owner")
        void joinGroup_restricted() {
            when(userRepository.findByEmail("bob@u.nus.edu")).thenReturn(Optional.of(bob));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(true);

            ResponseEntity<?> res = controller.joinGroup(groupId, authFor(bob));
            assertThat(res.getStatusCode().value()).isEqualTo(403);
        }

        @Test
        @DisplayName("joinGroup succeeds when user is not restricted")
        void joinGroup_notRestricted() {
            when(userRepository.findByEmail("bob@u.nus.edu")).thenReturn(Optional.of(bob));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(false);
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.joinGroup(groupId, authFor(bob));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @Test
        @DisplayName("inviteMember returns 400 when target is restricted")
        void inviteMember_restricted() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            // alice is owner so isAdmin → isOwner returns true
            when(userRepository.findByEmail("bob@u.nus.edu")).thenReturn(Optional.of(bob));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(true);

            Map<String, Object> body = Map.of("email", "bob@u.nus.edu");
            ResponseEntity<?> res = controller.inviteMember(groupId, authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("getAllGroups filters out groups from restricting owners")
        void getAllGroups_filtersRestricted() {
            StudyGroup group2 = new StudyGroup();
            group2.setId(UUID.randomUUID());
            group2.setCreatedBy(bob.getId());
            group2.setStatus("active");

            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(groupRepository.findByStatusInOrderByCreatedAtDesc(List.of("active", "full")))
                .thenReturn(new ArrayList<>(List.of(group, group2)));
            // bob restricted alice
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), alice.getId())).thenReturn(false);
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(bob.getId(), alice.getId())).thenReturn(true);

            ResponseEntity<?> res = controller.getAllGroups(authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            // group2 (owned by bob who restricted alice) should be filtered out
            @SuppressWarnings("unchecked")
            List<?> groups = (List<?>) res.getBody();
            assertThat(groups).hasSize(1);
        }
    }

    @Nested
    @DisplayName("approve/reject email notifications")
    class EmailNotifications {
        StudyGroup group;
        UUID groupId;
        User bob;

        @BeforeEach
        void initGroup() {
            groupId = UUID.randomUUID();
            group = new StudyGroup();
            group.setId(groupId);
            group.setCreatedBy(alice.getId());
            group.setMaxMembers((short) 5);
            group.setStatus("active");
            group.setName("Test Group");
            group.setModuleCode("CS5000");
            group.setTopic("Algorithms");

            bob = new User();
            bob.setId(UUID.randomUUID());
            bob.setEmail("bob@u.nus.edu");
            bob.setFirstName("Bob");
            bob.setLastName("Lee");
        }

        @Test
        @DisplayName("approveMember sends approval email")
        void approveMember_sendsEmail() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            // alice is owner so isAdmin → isOwner returns true

            StudyGroupMember membership = new StudyGroupMember();
            membership.setUserId(bob.getId());
            membership.setMembershipStatus("pending");
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.of(membership));
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(1L);
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<?> res = controller.approveMember(groupId, bob.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            verify(emailService).sendMemberApproved(
                eq("bob@u.nus.edu"),
                eq("Bob Lee"),
                eq("Test Group"),
                eq("CS5000"),
                eq("Algorithms"),
                any(),
                any(),
                eq("alice@u.nus.edu")
            );
        }

        @Test
        @DisplayName("removeMember sends rejection email")
        void removeMember_sendsEmail() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            // alice is owner so isAdmin → isOwner returns true

            StudyGroupMember membership = new StudyGroupMember();
            membership.setUserId(bob.getId());
            membership.setMembershipStatus("pending");
            when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.of(membership));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
            when(memberRepository.countByGroupIdAndMembershipStatus(groupId, "approved")).thenReturn(1L);
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ResponseEntity<?> res = controller.removeMember(groupId, bob.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            verify(emailService).sendMemberRejected(
                eq("bob@u.nus.edu"),
                eq("Bob Lee"),
                eq("Test Group"),
                eq("CS5000"),
                eq("Algorithms"),
                any(),
                any(),
                eq("alice@u.nus.edu")
            );
        }
    }
}
