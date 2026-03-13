package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.GroupController;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
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
            body.put("name","Test group");
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
            when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId())).thenReturn(Optional.empty());
            ResponseEntity<?> res = controller.joinGroup(authFor(alice), groupId);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            // already member
            when(memberRepository.findByGroupIdAndUserId(groupId, alice.getId())).thenReturn(Optional.of(new StudyGroupMember()));
            res = controller.joinGroup(authFor(alice), groupId);
            assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        }

        @Test
        void leaveGroup() {
            ResponseEntity<?> res = controller.leaveGroup(authFor(alice), groupId);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }
}
