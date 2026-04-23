package mtech.swe5006.peerconnect;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupAnnouncement;
import mtech.swe5006.peerconnect.data.sql.StudyGroupAnnouncementArchive;
import mtech.swe5006.peerconnect.data.sql.StudyGroupAnnouncementArchiveRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupAnnouncementRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.dto.AnnouncementResponse;
import mtech.swe5006.peerconnect.dto.CreateAnnouncementRequest;
import mtech.swe5006.peerconnect.dto.UpdateAnnouncementRequest;
import mtech.swe5006.peerconnect.service.AnnouncementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnnouncementService}. Exercises the authorisation
 * matrix, the archive filter logic, and the repository interaction contract
 * for create / update / delete / archive / read paths.
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock(lenient = true)
    private StudyGroupAnnouncementRepository announcementRepository;
    @Mock(lenient = true)
    private StudyGroupAnnouncementArchiveRepository archiveRepository;
    @Mock(lenient = true)
    private StudyGroupRepository groupRepository;
    @Mock(lenient = true)
    private StudyGroupMemberRepository memberRepository;
    @Mock(lenient = true)
    private UserRepository userRepository;

    @InjectMocks
    private AnnouncementService service;

    private UUID groupId;
    private UUID ownerId;
    private StudyGroup group;
    private User owner;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        group = new StudyGroup();
        group.setId(groupId);
        group.setCreatedBy(ownerId);
        group.setName("Study Group A");
        group.setModuleCode("SWE5006");

        owner = new User();
        owner.setId(ownerId);
        owner.setEmail("owner@u.nus.edu");
        owner.setFirstName("Owner");
        owner.setLastName("User");

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
    }

    /* ── create ──────────────────────────────────────────────── */

    @Test
    void ownerCanCreateAnnouncement() {
        when(announcementRepository.save(any(StudyGroupAnnouncement.class))).thenAnswer(inv -> {
            StudyGroupAnnouncement announcement = inv.getArgument(0);
            announcement.setId(UUID.randomUUID());
            announcement.setCreatedAt(LocalDateTime.now());
            return announcement;
        });

        var response = service.createAnnouncement(groupId, ownerId, new CreateAnnouncementRequest("Venue", "Changed"));

        assertThat(response.groupId()).isEqualTo(groupId);
        assertThat(response.title()).isEqualTo("Venue");
        assertThat(response.authorName()).isEqualTo("Owner User");
        assertThat(response.groupName()).isEqualTo("Study Group A");
        assertThat(response.moduleCode()).isEqualTo("SWE5006");
        verify(announcementRepository).save(any(StudyGroupAnnouncement.class));
    }

    @Test
    void createAnnouncementTrimsTitleAndContent() {
        ArgumentCaptor<StudyGroupAnnouncement> captor = ArgumentCaptor.forClass(StudyGroupAnnouncement.class);
        when(announcementRepository.save(any(StudyGroupAnnouncement.class))).thenAnswer(inv -> {
            StudyGroupAnnouncement announcement = inv.getArgument(0);
            announcement.setId(UUID.randomUUID());
            announcement.setCreatedAt(LocalDateTime.now());
            return announcement;
        });

        service.createAnnouncement(groupId, ownerId, new CreateAnnouncementRequest("  Title  ", "\n Content \t"));

        verify(announcementRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getContent()).isEqualTo("Content");
    }

    @Test
    void approvedAdminMemberCanCreateAnnouncement() {
        UUID adminId = UUID.randomUUID();
        StudyGroupMember membership = approvedMember(adminId, "admin");
        when(memberRepository.findByGroupIdAndUserId(groupId, adminId)).thenReturn(Optional.of(membership));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(owner));
        when(announcementRepository.save(any(StudyGroupAnnouncement.class))).thenAnswer(inv -> {
            StudyGroupAnnouncement a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(LocalDateTime.now());
            return a;
        });

        AnnouncementResponse response = service.createAnnouncement(groupId, adminId,
            new CreateAnnouncementRequest("Hi", "Body"));

        assertThat(response.createdBy()).isEqualTo(adminId);
    }

    @Test
    void nonAdminCannotCreateAnnouncement() {
        UUID memberId = UUID.randomUUID();
        StudyGroupMember membership = approvedMember(memberId, "member");
        when(memberRepository.findByGroupIdAndUserId(groupId, memberId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() ->
            service.createAnnouncement(groupId, memberId, new CreateAnnouncementRequest("Venue", "Changed"))
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void createAnnouncementOnMissingGroupReturns404() {
        UUID unknownGroup = UUID.randomUUID();
        when(groupRepository.findById(unknownGroup)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.createAnnouncement(unknownGroup, ownerId, new CreateAnnouncementRequest("Title", "Body"))
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    /* ── update ──────────────────────────────────────────────── */

    @Test
    void creatorCanUpdateOwnAnnouncement() {
        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        UUID announcementId = UUID.randomUUID();
        announcement.setId(announcementId);
        announcement.setGroupId(groupId);
        announcement.setCreatedBy(ownerId);
        announcement.setTitle("Old");
        announcement.setContent("Old body");
        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));
        when(announcementRepository.save(announcement)).thenReturn(announcement);

        var response = service.updateAnnouncement(groupId, announcementId, ownerId, new UpdateAnnouncementRequest("New", "New body"));

        assertThat(response.title()).isEqualTo("New");
        assertThat(announcement.getContent()).isEqualTo("New body");
    }

    @Test
    void nonCreatorNonAdminCannotUpdateAnnouncement() {
        UUID creatorId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();

        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setId(announcementId);
        announcement.setGroupId(groupId);
        announcement.setCreatedBy(creatorId);
        announcement.setTitle("Old");
        announcement.setContent("Old body");
        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));
        when(memberRepository.findByGroupIdAndUserId(groupId, strangerId))
            .thenReturn(Optional.of(approvedMember(strangerId, "member")));

        assertThatThrownBy(() ->
            service.updateAnnouncement(groupId, announcementId, strangerId,
                new UpdateAnnouncementRequest("New", "New body"))
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void updateAnnouncementNotFoundReturns404() {
        UUID announcementId = UUID.randomUUID();
        when(announcementRepository.findById(announcementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.updateAnnouncement(groupId, announcementId, ownerId,
                new UpdateAnnouncementRequest("New", "Body"))
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void updateAnnouncementFromDifferentGroupReturns404() {
        UUID announcementId = UUID.randomUUID();
        UUID otherGroupId = UUID.randomUUID();
        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setId(announcementId);
        announcement.setGroupId(otherGroupId); // belongs to a different group
        announcement.setCreatedBy(ownerId);
        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));

        assertThatThrownBy(() ->
            service.updateAnnouncement(groupId, announcementId, ownerId,
                new UpdateAnnouncementRequest("New", "Body"))
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    /* ── delete ──────────────────────────────────────────────── */

    @Test
    void deleteRemovesArchivesThenAnnouncement() {
        UUID announcementId = UUID.randomUUID();
        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setId(announcementId);
        announcement.setGroupId(groupId);
        announcement.setCreatedBy(ownerId);
        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));

        service.deleteAnnouncement(groupId, announcementId, ownerId);

        // Order matters: archives must be removed before the parent row so the
        // FK would not block deletion even if it were enforced.
        InOrder inOrder = inOrder(archiveRepository, announcementRepository);
        inOrder.verify(archiveRepository).deleteByAnnouncementId(announcementId);
        inOrder.verify(announcementRepository).delete(announcement);
        verify(archiveRepository, never()).save(any());
    }

    @Test
    void nonCreatorNonAdminCannotDeleteAnnouncement() {
        UUID creatorId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setId(announcementId);
        announcement.setGroupId(groupId);
        announcement.setCreatedBy(creatorId);
        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));
        when(memberRepository.findByGroupIdAndUserId(groupId, strangerId))
            .thenReturn(Optional.of(approvedMember(strangerId, "member")));

        assertThatThrownBy(() -> service.deleteAnnouncement(groupId, announcementId, strangerId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        verify(announcementRepository, never()).delete(any(StudyGroupAnnouncement.class));
        verify(archiveRepository, never()).deleteByAnnouncementId(any());
    }

    /* ── archive ─────────────────────────────────────────────── */

    @Test
    void archiveCreatesRecordOnce() {
        UUID announcementId = UUID.randomUUID();
        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setId(announcementId);
        announcement.setGroupId(groupId);
        announcement.setCreatedBy(ownerId);

        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));
        when(archiveRepository.existsByAnnouncementIdAndUserId(announcementId, ownerId)).thenReturn(false);

        service.archiveAnnouncement(groupId, announcementId, ownerId);

        ArgumentCaptor<StudyGroupAnnouncementArchive> captor = ArgumentCaptor.forClass(StudyGroupAnnouncementArchive.class);
        verify(archiveRepository).save(captor.capture());
        assertThat(captor.getValue().getAnnouncementId()).isEqualTo(announcementId);
        assertThat(captor.getValue().getUserId()).isEqualTo(ownerId);
    }

    @Test
    void archiveIsIdempotentForSameUser() {
        UUID announcementId = UUID.randomUUID();
        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setId(announcementId);
        announcement.setGroupId(groupId);
        announcement.setCreatedBy(ownerId);

        when(announcementRepository.findById(announcementId)).thenReturn(Optional.of(announcement));
        when(archiveRepository.existsByAnnouncementIdAndUserId(announcementId, ownerId)).thenReturn(true);

        service.archiveAnnouncement(groupId, announcementId, ownerId);

        verify(archiveRepository, never()).save(any(StudyGroupAnnouncementArchive.class));
    }

    @Test
    void nonMemberCannotArchiveAnnouncement() {
        UUID strangerId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();

        assertThatThrownBy(() -> service.archiveAnnouncement(groupId, announcementId, strangerId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
        verify(archiveRepository, never()).save(any());
    }

    /* ── read: single group ──────────────────────────────────── */

    @Test
    void approvedMemberCanReadGroupAnnouncements() {
        UUID memberId = UUID.randomUUID();
        StudyGroupAnnouncement a1 = makeAnnouncement(groupId, ownerId, "First");
        StudyGroupAnnouncement a2 = makeAnnouncement(groupId, ownerId, "Second");
        when(memberRepository.findByGroupIdAndUserId(groupId, memberId))
            .thenReturn(Optional.of(approvedMember(memberId, "member")));
        when(announcementRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(a2, a1));

        var responses = service.getGroupAnnouncements(groupId, memberId);

        assertThat(responses).extracting(AnnouncementResponse::title).containsExactly("Second", "First");
    }

    @Test
    void getGroupAnnouncementsFiltersArchivedForThisUser() {
        StudyGroupAnnouncement kept = makeAnnouncement(groupId, ownerId, "Keep me");
        StudyGroupAnnouncement archived = makeAnnouncement(groupId, ownerId, "Hidden");
        StudyGroupAnnouncementArchive archive = new StudyGroupAnnouncementArchive();
        archive.setAnnouncementId(archived.getId());
        archive.setUserId(ownerId);

        when(announcementRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(kept, archived));
        when(archiveRepository.findByUserId(ownerId)).thenReturn(List.of(archive));

        var responses = service.getGroupAnnouncements(groupId, ownerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("Keep me");
    }

    @Test
    void nonMemberCannotReadGroupAnnouncements() {
        UUID strangerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getGroupAnnouncements(groupId, strangerId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void getGroupAnnouncementsOnMissingGroupReturns404() {
        UUID unknown = UUID.randomUUID();
        when(groupRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGroupAnnouncements(unknown, ownerId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    /* ── read: joined feed ───────────────────────────────────── */

    @Test
    void archiveHidesAnnouncementFromJoinedFeed() {
        UUID announcementId = UUID.randomUUID();
        StudyGroupAnnouncement announcement = makeAnnouncement(groupId, ownerId, "Title");
        announcement.setId(announcementId);

        StudyGroupAnnouncementArchive archive = new StudyGroupAnnouncementArchive();
        archive.setAnnouncementId(announcementId);
        archive.setUserId(ownerId);

        when(memberRepository.findByUserIdAndMembershipStatus(ownerId, "approved"))
            .thenReturn(List.of(approvedMember(ownerId, "owner")));
        when(groupRepository.findAllById(List.of(groupId))).thenReturn(List.of(group));
        when(announcementRepository.findByGroupIdInOrderByCreatedAtDesc(List.of(groupId))).thenReturn(List.of(announcement));
        when(archiveRepository.findByUserId(ownerId)).thenReturn(List.of(archive));

        var responses = service.getJoinedAnnouncements(ownerId);

        assertThat(responses).isEmpty();
    }

    @Test
    void joinedFeedReturnsEmptyWhenNoMemberships() {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findByUserIdAndMembershipStatus(userId, "approved")).thenReturn(List.of());

        var responses = service.getJoinedAnnouncements(userId);

        assertThat(responses).isEmpty();
        verify(announcementRepository, never()).findByGroupIdInOrderByCreatedAtDesc(any());
    }

    @Test
    void joinedFeedPopulatesGroupNameAndAuthor() {
        StudyGroupAnnouncement announcement = makeAnnouncement(groupId, ownerId, "Title");

        when(memberRepository.findByUserIdAndMembershipStatus(ownerId, "approved"))
            .thenReturn(List.of(approvedMember(ownerId, "owner")));
        when(groupRepository.findAllById(List.of(groupId))).thenReturn(List.of(group));
        when(announcementRepository.findByGroupIdInOrderByCreatedAtDesc(List.of(groupId))).thenReturn(List.of(announcement));
        when(userRepository.findAllById(List.of(ownerId))).thenReturn(List.of(owner));

        var responses = service.getJoinedAnnouncements(ownerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).groupName()).isEqualTo("Study Group A");
        assertThat(responses.get(0).moduleCode()).isEqualTo("SWE5006");
        assertThat(responses.get(0).authorEmail()).isEqualTo("owner@u.nus.edu");
        assertThat(responses.get(0).authorName()).isEqualTo("Owner User");
    }

    /* ── test fixtures ───────────────────────────────────────── */

    private StudyGroupMember approvedMember(UUID userId, String role) {
        StudyGroupMember membership = new StudyGroupMember();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setMembershipStatus("approved");
        membership.setRole(role);
        return membership;
    }

    private StudyGroupAnnouncement makeAnnouncement(UUID gId, UUID createdBy, String title) {
        StudyGroupAnnouncement a = new StudyGroupAnnouncement();
        a.setId(UUID.randomUUID());
        a.setGroupId(gId);
        a.setCreatedBy(createdBy);
        a.setTitle(title);
        a.setContent(title + " body");
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }
}
