package mtech.swe5006.peerconnect.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtech.swe5006.peerconnect.dto.AnnouncementResponse;
import mtech.swe5006.peerconnect.dto.CreateAnnouncementRequest;
import mtech.swe5006.peerconnect.dto.UpdateAnnouncementRequest;
import mtech.swe5006.peerconnect.service.AnnouncementService;
import mtech.swe5006.peerconnect.service.SecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final SecurityService securityService;

    @GetMapping("/{groupId}/announcements")
    public List<AnnouncementResponse> getAnnouncements(@PathVariable UUID groupId) {
        UUID userId = securityService.getCurrentUserId();
        return announcementService.getGroupAnnouncements(groupId, userId);
    }

    @PostMapping("/{groupId}/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse createAnnouncement(@PathVariable UUID groupId,
                                                   @Valid @RequestBody CreateAnnouncementRequest request) {
        UUID userId = securityService.getCurrentUserId();
        return announcementService.createAnnouncement(groupId, userId, request);
    }

    @PutMapping("/{groupId}/announcements/{announcementId}")
    public AnnouncementResponse updateAnnouncement(@PathVariable UUID groupId,
                                                   @PathVariable UUID announcementId,
                                                   @Valid @RequestBody UpdateAnnouncementRequest request) {
        UUID userId = securityService.getCurrentUserId();
        return announcementService.updateAnnouncement(groupId, announcementId, userId, request);
    }

    @DeleteMapping("/{groupId}/announcements/{announcementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnnouncement(@PathVariable UUID groupId, @PathVariable UUID announcementId) {
        UUID userId = securityService.getCurrentUserId();
        announcementService.deleteAnnouncement(groupId, announcementId, userId);
    }

    @PostMapping("/{groupId}/announcements/{announcementId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveAnnouncement(@PathVariable UUID groupId, @PathVariable UUID announcementId) {
        UUID userId = securityService.getCurrentUserId();
        announcementService.archiveAnnouncement(groupId, announcementId, userId);
    }

    @DeleteMapping("/{groupId}/announcements/{announcementId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unarchiveAnnouncement(@PathVariable UUID groupId, @PathVariable UUID announcementId) {
        UUID userId = securityService.getCurrentUserId();
        announcementService.unarchiveAnnouncement(groupId, announcementId, userId);
    }

    @GetMapping("/joined/announcements")
    public List<AnnouncementResponse> getJoinedAnnouncements() {
        UUID userId = securityService.getCurrentUserId();
        return announcementService.getJoinedAnnouncements(userId);
    }

    @GetMapping("/joined/announcements/archived")
    public List<AnnouncementResponse> getArchivedAnnouncements() {
        UUID userId = securityService.getCurrentUserId();
        return announcementService.getArchivedAnnouncements(userId);
    }
}
