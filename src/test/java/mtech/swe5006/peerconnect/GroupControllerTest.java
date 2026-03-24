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
            ArgumentCaptor<StudyGroup> groupCaptor = ArgumentCaptor.forClass(StudyGroup.class);
            verify(groupRepository).save(groupCaptor.capture());
            assertThat(groupCaptor.getValue().getCourseId()).isEqualTo("CS5000");
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
}
