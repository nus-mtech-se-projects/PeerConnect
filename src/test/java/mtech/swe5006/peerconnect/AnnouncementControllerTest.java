package mtech.swe5006.peerconnect;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import mtech.swe5006.peerconnect.api.AnnouncementController;
import mtech.swe5006.peerconnect.dto.AnnouncementResponse;
import mtech.swe5006.peerconnect.dto.CreateAnnouncementRequest;
import mtech.swe5006.peerconnect.dto.UpdateAnnouncementRequest;
import mtech.swe5006.peerconnect.service.AnnouncementService;
import mtech.swe5006.peerconnect.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Delegation tests for {@link AnnouncementController}. Verifies each
 * endpoint forwards to the service with the right args and uses the
 * current user id from {@link SecurityService}.
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementControllerTest {

    @Mock
    private AnnouncementService announcementService;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private AnnouncementController controller;

    private UUID groupId;
    private UUID userId;
    private UUID announcementId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        userId = UUID.randomUUID();
        announcementId = UUID.randomUUID();
        when(securityService.getCurrentUserId()).thenReturn(userId);
    }

    private AnnouncementResponse sampleResponse() {
        return new AnnouncementResponse(
            announcementId,
            groupId,
            "Title",
            "Body",
            userId,
            LocalDateTime.now(),
            "owner@u.nus.edu",
            "Owner User",
            "Study Group A",
            "SWE5006"
        );
    }

    /* ── GET /api/groups/{groupId}/announcements ─────────────────────── */

    @Test
    void getAnnouncementsDelegatesToServiceWithCurrentUser() {
        List<AnnouncementResponse> expected = List.of(sampleResponse());
        when(announcementService.getGroupAnnouncements(groupId, userId)).thenReturn(expected);

        List<AnnouncementResponse> result = controller.getAnnouncements(groupId);

        assertThat(result).isSameAs(expected);
        verify(announcementService).getGroupAnnouncements(groupId, userId);
    }

    /* ── POST /api/groups/{groupId}/announcements ────────────────────── */

    @Test
    void createAnnouncementDelegatesToService() {
        CreateAnnouncementRequest request = new CreateAnnouncementRequest("Title", "Body");
        AnnouncementResponse response = sampleResponse();
        when(announcementService.createAnnouncement(groupId, userId, request)).thenReturn(response);

        AnnouncementResponse result = controller.createAnnouncement(groupId, request);

        assertThat(result).isSameAs(response);
        verify(announcementService).createAnnouncement(groupId, userId, request);
    }

    @Test
    void createAnnouncementForwardsCurrentUserNotPathUser() {
        CreateAnnouncementRequest request = new CreateAnnouncementRequest("Title", "Body");
        when(announcementService.createAnnouncement(any(), eq(userId), any())).thenReturn(sampleResponse());

        controller.createAnnouncement(groupId, request);

        verify(announcementService).createAnnouncement(groupId, userId, request);
        verify(securityService).getCurrentUserId();
    }

    /* ── PUT /api/groups/{groupId}/announcements/{announcementId} ────── */

    @Test
    void updateAnnouncementDelegatesToService() {
        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest("New", "Updated");
        AnnouncementResponse response = sampleResponse();
        when(announcementService.updateAnnouncement(groupId, announcementId, userId, request))
            .thenReturn(response);

        AnnouncementResponse result = controller.updateAnnouncement(groupId, announcementId, request);

        assertThat(result).isSameAs(response);
        verify(announcementService).updateAnnouncement(groupId, announcementId, userId, request);
    }

    /* ── DELETE /api/groups/{groupId}/announcements/{announcementId} ── */

    @Test
    void deleteAnnouncementDelegatesToService() {
        controller.deleteAnnouncement(groupId, announcementId);

        verify(announcementService).deleteAnnouncement(groupId, announcementId, userId);
        verify(announcementService, never()).archiveAnnouncement(any(), any(), any());
    }

    /* ── POST   /…/announcements/{id}/archive ────────────────────────── */
    /* ── DELETE /…/announcements/{id}/archive ────────────────────────── */

    @Test
    void archiveDelegatesToService() {
        controller.archiveAnnouncement(groupId, announcementId);

        verify(announcementService).archiveAnnouncement(groupId, announcementId, userId);
    }

    @Test
    void unarchiveDelegatesToService() {
        controller.unarchiveAnnouncement(groupId, announcementId);

        verify(announcementService).unarchiveAnnouncement(groupId, announcementId, userId);
    }

    /* ── GET /api/groups/joined/announcements ────────────────────────── */

    @Test
    void getJoinedAnnouncementsDelegatesToService() {
        List<AnnouncementResponse> expected = List.of(sampleResponse());
        when(announcementService.getJoinedAnnouncements(userId)).thenReturn(expected);

        List<AnnouncementResponse> result = controller.getJoinedAnnouncements();

        assertThat(result).isSameAs(expected);
        verify(announcementService).getJoinedAnnouncements(userId);
    }

    @Test
    void getArchivedAnnouncementsDelegatesToService() {
        List<AnnouncementResponse> expected = List.of(sampleResponse());
        when(announcementService.getArchivedAnnouncements(userId)).thenReturn(expected);

        List<AnnouncementResponse> result = controller.getArchivedAnnouncements();

        assertThat(result).isSameAs(expected);
        verify(announcementService).getArchivedAnnouncements(userId);
    }

    /* ── auth boundary ───────────────────────────────────────────────── */

    @Test
    void everyEndpointResolvesCurrentUserViaSecurityService() {
        when(announcementService.getGroupAnnouncements(any(), any())).thenReturn(List.of());
        when(announcementService.createAnnouncement(any(), any(), any())).thenReturn(sampleResponse());
        when(announcementService.updateAnnouncement(any(), any(), any(), any())).thenReturn(sampleResponse());
        when(announcementService.getJoinedAnnouncements(any())).thenReturn(List.of());
        when(announcementService.getArchivedAnnouncements(any())).thenReturn(List.of());

        controller.getAnnouncements(groupId);
        controller.createAnnouncement(groupId, new CreateAnnouncementRequest("t", "c"));
        controller.updateAnnouncement(groupId, announcementId, new UpdateAnnouncementRequest("t", "c"));
        controller.deleteAnnouncement(groupId, announcementId);
        controller.archiveAnnouncement(groupId, announcementId);
        controller.unarchiveAnnouncement(groupId, announcementId);
        controller.getJoinedAnnouncements();
        controller.getArchivedAnnouncements();

        verify(securityService, org.mockito.Mockito.times(8)).getCurrentUserId();
    }
}
