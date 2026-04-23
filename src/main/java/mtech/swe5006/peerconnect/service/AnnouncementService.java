package mtech.swe5006.peerconnect.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Service that encapsulates all business rules for group announcements.
 *
 * Authorisation matrix:
 *   - read (single group):  any approved member OR the group owner
 *   - read (joined feed):   current user's approved memberships
 *   - create:                group owner or approved admin member
 *   - update / delete:       announcement creator OR group owner / admin
 *   - archive (hide):        any approved member (per-user, non-destructive)
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final String ROLE_OWNER = "owner";
    private static final String ROLE_ADMIN = "admin";
    private static final String STATUS_APPROVED = "approved";

    private final StudyGroupAnnouncementRepository announcementRepository;
    private final StudyGroupAnnouncementArchiveRepository archiveRepository;
    private final StudyGroupRepository groupRepository;
    private final StudyGroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getGroupAnnouncements(UUID groupId, UUID userId) {
        StudyGroup group = getGroupOrThrow(groupId);
        requireApprovedMembership(group, userId);

        Set<UUID> archivedIds = getArchivedAnnouncementIds(userId);
        List<StudyGroupAnnouncement> announcements = announcementRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
            .filter(notArchived(archivedIds))
            .toList();

        Map<UUID, User> authorsById = loadAuthorsFor(announcements);
        return announcements.stream()
            .map(ann -> toResponse(ann, group, authorsById.get(ann.getCreatedBy())))
            .toList();
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(UUID groupId, UUID userId, CreateAnnouncementRequest request) {
        StudyGroup group = getGroupOrThrow(groupId);
        requireAdminOrOwner(group, userId);

        StudyGroupAnnouncement announcement = new StudyGroupAnnouncement();
        announcement.setGroupId(groupId);
        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        announcement.setCreatedBy(userId);
        StudyGroupAnnouncement saved = announcementRepository.save(announcement);
        User author = userRepository.findById(userId).orElse(null);
        return toResponse(saved, group, author);
    }

    @Transactional
    public AnnouncementResponse updateAnnouncement(UUID groupId,
                                                   UUID announcementId,
                                                   UUID userId,
                                                   UpdateAnnouncementRequest request) {
        StudyGroup group = getGroupOrThrow(groupId);
        StudyGroupAnnouncement announcement = getAnnouncementOrThrow(groupId, announcementId);
        requireAnnouncementManager(group, announcement, userId);

        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        StudyGroupAnnouncement saved = announcementRepository.save(announcement);
        User author = userRepository.findById(saved.getCreatedBy()).orElse(null);
        return toResponse(saved, group, author);
    }

    @Transactional
    public void deleteAnnouncement(UUID groupId, UUID announcementId, UUID userId) {
        StudyGroup group = getGroupOrThrow(groupId);
        StudyGroupAnnouncement announcement = getAnnouncementOrThrow(groupId, announcementId);
        requireAnnouncementManager(group, announcement, userId);

        archiveRepository.deleteByAnnouncementId(announcementId);
        announcementRepository.delete(announcement);
    }

    @Transactional
    public void archiveAnnouncement(UUID groupId, UUID announcementId, UUID userId) {
        StudyGroup group = getGroupOrThrow(groupId);
        requireApprovedMembership(group, userId);
        StudyGroupAnnouncement announcement = getAnnouncementOrThrow(groupId, announcementId);

        if (archiveRepository.existsByAnnouncementIdAndUserId(announcement.getId(), userId)) {
            return;
        }

        StudyGroupAnnouncementArchive archive = new StudyGroupAnnouncementArchive();
        archive.setAnnouncementId(announcement.getId());
        archive.setUserId(userId);
        archiveRepository.save(archive);
    }

    /**
     * Restore a previously-archived announcement so it shows up in the user's
     * active feed again. A no-op when the announcement was never archived for
     * this user — we don't want to 404 in that case because it would look like
     * a bug to the caller.
     */
    @Transactional
    public void unarchiveAnnouncement(UUID groupId, UUID announcementId, UUID userId) {
        StudyGroup group = getGroupOrThrow(groupId);
        requireApprovedMembership(group, userId);
        getAnnouncementOrThrow(groupId, announcementId);
        archiveRepository.deleteByAnnouncementIdAndUserId(announcementId, userId);
    }

    /**
     * Returns the announcements the current user has archived across all their
     * joined groups. Only considers groups the user is still approved in — if
     * they've been removed from a group, we hide those archives too so nothing
     * they can't otherwise see leaks back via this endpoint.
     */
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getArchivedAnnouncements(UUID userId) {
        Set<UUID> archivedIds = getArchivedAnnouncementIds(userId);
        if (archivedIds.isEmpty()) {
            return List.of();
        }

        List<StudyGroupMember> memberships = groupMemberRepository.findByUserIdAndMembershipStatus(userId, STATUS_APPROVED);
        Set<UUID> joinedGroupIds = memberships.stream()
            .map(StudyGroupMember::getGroupId)
            .collect(Collectors.toSet());

        List<StudyGroupAnnouncement> announcements = announcementRepository.findAllById(archivedIds).stream()
            .filter(ann -> joinedGroupIds.contains(ann.getGroupId()))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();

        if (announcements.isEmpty()) {
            return List.of();
        }

        Map<UUID, StudyGroup> groupsById = groupRepository.findAllById(
            announcements.stream().map(StudyGroupAnnouncement::getGroupId).distinct().toList()
        ).stream().collect(Collectors.toMap(StudyGroup::getId, g -> g));
        Map<UUID, User> authorsById = loadAuthorsFor(announcements);

        return announcements.stream()
            .map(ann -> toResponse(ann, groupsById.get(ann.getGroupId()), authorsById.get(ann.getCreatedBy())))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getJoinedAnnouncements(UUID userId) {
        List<StudyGroupMember> memberships = groupMemberRepository.findByUserIdAndMembershipStatus(userId, STATUS_APPROVED);
        List<UUID> groupIds = memberships.stream()
            .map(StudyGroupMember::getGroupId)
            .distinct()
            .toList();

        if (groupIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, StudyGroup> groupsById = groupRepository.findAllById(groupIds).stream()
            .collect(Collectors.toMap(StudyGroup::getId, group -> group));
        Set<UUID> archivedIds = getArchivedAnnouncementIds(userId);

        List<StudyGroupAnnouncement> announcements = announcementRepository.findByGroupIdInOrderByCreatedAtDesc(groupIds).stream()
            .filter(notArchived(archivedIds))
            .toList();

        Map<UUID, User> authorsById = loadAuthorsFor(announcements);
        return announcements.stream()
            .map(ann -> toResponse(ann, groupsById.get(ann.getGroupId()), authorsById.get(ann.getCreatedBy())))
            .toList();
    }

    /* ── Helpers ───────────────────────────────────────────────────── */

    private StudyGroup getGroupOrThrow(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
    }

    private StudyGroupAnnouncement getAnnouncementOrThrow(UUID groupId, UUID announcementId) {
        StudyGroupAnnouncement announcement = announcementRepository.findById(announcementId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Announcement not found"));
        if (!groupId.equals(announcement.getGroupId())) {
            throw new ResponseStatusException(NOT_FOUND, "Announcement not found");
        }
        return announcement;
    }

    private void requireApprovedMembership(StudyGroup group, UUID userId) {
        if (isAdmin(group, userId)) {
            return;
        }

        StudyGroupMember membership = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);
        if (membership == null || !STATUS_APPROVED.equalsIgnoreCase(membership.getMembershipStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "Not a group member or insufficient permissions");
        }
    }

    private void requireAdminOrOwner(StudyGroup group, UUID userId) {
        if (!isAdmin(group, userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Not a group member or insufficient permissions");
        }
    }

    private void requireAnnouncementManager(StudyGroup group, StudyGroupAnnouncement announcement, UUID userId) {
        if (announcement.getCreatedBy() != null && announcement.getCreatedBy().equals(userId)) {
            // creator can always manage their own announcement, provided they are still approved
            requireApprovedMembership(group, userId);
            return;
        }
        requireAdminOrOwner(group, userId);
    }

    private boolean isAdmin(StudyGroup group, UUID userId) {
        if (group.getCreatedBy() != null && group.getCreatedBy().equals(userId)) {
            return true;
        }

        StudyGroupMember membership = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);
        return membership != null
            && STATUS_APPROVED.equalsIgnoreCase(membership.getMembershipStatus())
            && (ROLE_OWNER.equalsIgnoreCase(membership.getRole()) || ROLE_ADMIN.equalsIgnoreCase(membership.getRole()));
    }

    private Set<UUID> getArchivedAnnouncementIds(UUID userId) {
        return archiveRepository.findByUserId(userId).stream()
            .map(StudyGroupAnnouncementArchive::getAnnouncementId)
            .collect(Collectors.toSet());
    }

    private Predicate<StudyGroupAnnouncement> notArchived(Set<UUID> archivedIds) {
        return announcement -> !archivedIds.contains(announcement.getId());
    }

    private Map<UUID, User> loadAuthorsFor(Collection<StudyGroupAnnouncement> announcements) {
        if (announcements.isEmpty()) {
            return new HashMap<>();
        }
        List<UUID> authorIds = announcements.stream()
            .map(StudyGroupAnnouncement::getCreatedBy)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
        if (authorIds.isEmpty()) {
            return new HashMap<>();
        }
        return userRepository.findAllById(authorIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));
    }

    private AnnouncementResponse toResponse(StudyGroupAnnouncement announcement, StudyGroup group, User author) {
        String authorName = author == null
            ? null
            : blankToNull(String.join(" ",
                nullToEmpty(author.getFirstName()),
                nullToEmpty(author.getLastName())).trim());
        return new AnnouncementResponse(
            announcement.getId(),
            announcement.getGroupId(),
            announcement.getTitle(),
            announcement.getContent(),
            announcement.getCreatedBy(),
            announcement.getCreatedAt(),
            author == null ? null : author.getEmail(),
            authorName,
            group == null ? null : blankToNull(firstNonBlank(group.getName(), group.getTopic())),
            group == null ? null : blankToNull(group.getModuleCode())
        );
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
