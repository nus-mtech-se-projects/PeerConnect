package mtech.swe5006.peerconnect;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mtech.swe5006.peerconnect.api.GroupController;
import mtech.swe5006.peerconnect.data.sql.RestrictedUserRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.data.sql.StudySessionRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.AnnouncementService;
import mtech.swe5006.peerconnect.service.AuditService;
import mtech.swe5006.peerconnect.service.EmailService;
import mtech.swe5006.peerconnect.service.StudyGroupAutoAnnouncer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link GroupController} delegates to {@link StudyGroupAutoAnnouncer}
 * on the group-update and session-create paths.
 */
@ExtendWith(MockitoExtension.class)
class GroupControllerAutoAnnounceTest {

    @Mock(lenient = true) StudyGroupRepository groupRepository;
    @Mock(lenient = true) StudyGroupMemberRepository memberRepository;
    @Mock(lenient = true) UserRepository userRepository;
    @Mock(lenient = true) StudySessionRepository studySessionRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock(lenient = true) AuditService auditService;
    @Mock(lenient = true) EmailService emailService;
    @Mock RestrictedUserRepository restrictedUserRepository;
    @Mock(lenient = true) AnnouncementService announcementService;

    @InjectMocks
    GroupController controller;

    private StudyGroupAutoAnnouncer autoAnnouncer;
    private User owner;
    private StudyGroup group;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        autoAnnouncer = mock(StudyGroupAutoAnnouncer.class);
        ReflectionTestUtils.setField(controller, "autoAnnouncer", autoAnnouncer);

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmail("alice@u.nus.edu");
        owner.setFirstName("Alice");
        owner.setLastName("Tan");

        groupId = UUID.randomUUID();
        group = new StudyGroup();
        group.setId(groupId);
        group.setCreatedBy(owner.getId());
        group.setName("Original Name");
        group.setModuleCode("SWE5006");
        group.setDescription("Existing description that satisfies validation.");
        group.setStudyMode("online");
        group.setMeetingLink("https://zoom.us/old");
        group.setPreferredSchedule(LocalDateTime.now().plusDays(7));
        group.setStatus("active");
        group.setMaxMembers((short) 10);
        group.setApprovalRequired(false);
        group.setAutoAnnounceEnabled(true);

        when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(owner));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(StudyGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(memberRepository.findByGroupId(any())).thenReturn(List.of());
        lenient().when(memberRepository.findByGroupIdAndUserId(any(), any())).thenReturn(Optional.empty());
        lenient().when(studySessionRepository.findByGroupIdOrderByStartsAtAsc(any())).thenReturn(List.of());
        lenient().when(announcementService.getGroupAnnouncements(any(), any())).thenReturn(List.of());
        lenient().when(userRepository.findById(any())).thenReturn(Optional.of(owner));
    }

    private Authentication authFor(User u) {
        Authentication a = mock(Authentication.class);
        when(a.getName()).thenReturn(u.getEmail());
        return a;
    }

    /* ── PUT /api/groups/{id} ────────────────────────────────────────── */

    @Test
    @DisplayName("PUT /api/groups/{id} invokes maybePostUpdateAnnouncement with snapshot taken before mutation")
    void updateGroupTriggersAutoAnnouncerWithBeforeSnapshot() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "New Name");

        ResponseEntity<?> res = controller.updateGroup(groupId, authFor(owner), body);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        ArgumentCaptor<StudyGroupAutoAnnouncer.GroupSnapshot> beforeCaptor =
            ArgumentCaptor.forClass(StudyGroupAutoAnnouncer.GroupSnapshot.class);
        ArgumentCaptor<StudyGroup> afterCaptor = ArgumentCaptor.forClass(StudyGroup.class);
        verify(autoAnnouncer).maybePostUpdateAnnouncement(
            beforeCaptor.capture(), afterCaptor.capture(), eq(owner.getId()));

        // Snapshot must be taken before mutation — old name in before, new name in after.
        assertThat(beforeCaptor.getValue().name()).isEqualTo("Original Name");
        assertThat(afterCaptor.getValue().getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("PUT /api/groups/{id} still calls auto-announcer when group has autoAnnounceEnabled=false (filter is in announcer)")
    void updateGroupCallsAutoAnnouncerEvenWhenGroupFlagDisabled() {
        // The flag is checked inside the announcer, not the controller.
        group.setAutoAnnounceEnabled(false);

        controller.updateGroup(groupId, authFor(owner), Map.of("name", "Renamed"));

        verify(autoAnnouncer).maybePostUpdateAnnouncement(any(), any(), eq(owner.getId()));
    }

    @Test
    @DisplayName("PUT /api/groups/{id} returns 403 and never calls auto-announcer when caller is not admin")
    void updateGroupForbiddenSkipsAutoAnnouncer() {
        User bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setEmail("bob@u.nus.edu");
        when(userRepository.findByEmail("bob@u.nus.edu")).thenReturn(Optional.of(bob));
        when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.empty());

        ResponseEntity<?> res = controller.updateGroup(groupId, authFor(bob), Map.of("name", "Hijack"));

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        verify(autoAnnouncer, never()).maybePostUpdateAnnouncement(any(), any(), any());
    }

    /* ── POST /api/groups/{id}/sessions ──────────────────────────────── */

    @Test
    @DisplayName("POST /api/groups/{id}/sessions invokes maybePostSessionCreated with autoAnnounceEnabled flag from body")
    void createSessionTriggersAutoAnnouncerWithBodyFlag() {
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> {
            StudySession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Mid-term review");
        body.put("startsAt", LocalDateTime.now().plusDays(1).toString());
        body.put("endsAt", LocalDateTime.now().plusDays(1).plusHours(2).toString());
        body.put("meetingLink", "https://zoom.us/new");
        body.put("autoAnnounceEnabled", true);

        ResponseEntity<?> res = controller.createSession(groupId, authFor(owner), body);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        ArgumentCaptor<StudySession> sessionCaptor = ArgumentCaptor.forClass(StudySession.class);
        verify(autoAnnouncer).maybePostSessionCreated(
            eq(group), sessionCaptor.capture(), eq(owner.getId()), eq(true));
        assertThat(sessionCaptor.getValue().getTitle()).isEqualTo("Mid-term review");
    }

    @Test
    @DisplayName("POST /api/groups/{id}/sessions defaults autoAnnounceEnabled to true when body omits flag")
    void createSessionDefaultsAutoAnnounceTrueWhenFlagOmitted() {
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> {
            StudySession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Q&A");
        body.put("startsAt", LocalDateTime.now().plusDays(1).toString());
        body.put("meetingLink", "https://zoom.us/qa");
        // autoAnnounceEnabled omitted — should default to true

        controller.createSession(groupId, authFor(owner), body);

        verify(autoAnnouncer).maybePostSessionCreated(any(), any(), eq(owner.getId()), eq(true));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/sessions still passes autoAnnounceEnabled=false through to announcer")
    void createSessionPassesFalseFlagThrough() {
        when(studySessionRepository.save(any(StudySession.class))).thenAnswer(inv -> {
            StudySession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Quiet session");
        body.put("startsAt", LocalDateTime.now().plusDays(1).toString());
        body.put("meetingLink", "https://zoom.us/quiet");
        body.put("autoAnnounceEnabled", false);

        controller.createSession(groupId, authFor(owner), body);

        // Announcer is still invoked — the flag=false no-op happens inside it.
        verify(autoAnnouncer).maybePostSessionCreated(any(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/sessions skips auto-announcer when caller is not admin")
    void createSessionForbiddenSkipsAutoAnnouncer() {
        User bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setEmail("bob@u.nus.edu");
        when(userRepository.findByEmail("bob@u.nus.edu")).thenReturn(Optional.of(bob));
        StudyGroupMember membership = new StudyGroupMember();
        membership.setRole("member");
        membership.setMembershipStatus("approved");
        when(memberRepository.findByGroupIdAndUserId(groupId, bob.getId())).thenReturn(Optional.of(membership));

        Map<String, Object> body = new HashMap<>();
        body.put("title", "Hijack");
        body.put("startsAt", LocalDateTime.now().plusDays(1).toString());
        body.put("meetingLink", "https://zoom.us/x");

        ResponseEntity<?> res = controller.createSession(groupId, authFor(bob), body);

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        verify(autoAnnouncer, never()).maybePostSessionCreated(any(), any(), any(), any(Boolean.class));
    }

    /* ── safety: announcer-null gate ─────────────────────────────────── */

    @Test
    @DisplayName("Update path does NOT NPE when auto-announcer bean is absent")
    void updateGroupHandlesNullAutoAnnouncer() {
        ReflectionTestUtils.setField(controller, "autoAnnouncer", null);

        ResponseEntity<?> res = controller.updateGroup(groupId, authFor(owner), Map.of("name", "Renamed"));

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
