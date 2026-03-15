package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.data.sql.StudySessionRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    private final StudyGroupRepository groupRepository;
    private final StudyGroupMemberRepository groupMemberRepository;
    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public GroupController(StudyGroupRepository groupRepository,
                           StudyGroupMemberRepository groupMemberRepository,
                           StudySessionRepository studySessionRepository,
                           UserRepository userRepository,
                           JdbcTemplate jdbcTemplate) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<?> getAllGroups(Authentication auth) {
        User currentUser = getCurrentUser(auth);
        List<StudyGroup> groups = groupRepository.findByStatusInOrderByCreatedAtDesc(List.of("active", "full"));
        List<Map<String, Object>> payload = groups.stream().map(group -> buildGroupSummary(group, currentUser)).toList();
        return ResponseEntity.ok(payload);
    }

    @PostMapping
    public ResponseEntity<?> createGroup(Authentication auth, @RequestBody Map<String, Object> body) {
        User user = getCurrentUser(auth);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        String name = asString(body.get("name"));
        String moduleCode = firstNonBlank(asString(body.get("moduleCode")), asString(body.get("courseCode")));
        String description = asString(body.get("description"));
        String topic = firstNonBlank(asString(body.get("topic")), moduleCode);
        String studyMode = firstNonBlank(asString(body.get("studyMode")), "online").toLowerCase();
        String location = asString(body.get("location"));
        String meetingLink = asString(body.get("meetingLink"));
        String preferredSchedule = asString(body.get("preferredSchedule"));
        Short maxMembers = asShort(body.get("maxMembers"), (short) 10);
        boolean approvalRequired = asBoolean(body.get("approvalRequired"), false);

        String validationError = validateGroupInput(name, moduleCode, description, studyMode, location, meetingLink, preferredSchedule, maxMembers);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        StudyGroup group = new StudyGroup();
        group.setName(name);
        group.setModuleCode(moduleCode);
        group.setDescription(description);
        group.setTopic(topic);
        group.setStudyMode(studyMode);
        group.setLocation(location);
        group.setMeetingLink(meetingLink);
        group.setPreferredSchedule(preferredSchedule);
        group.setApprovalRequired(approvalRequired);
        group.setCreatedBy(user.getId());
        group.setStatus("active");
        group.setMaxMembers(maxMembers);
        UUID resolvedCourseId = resolveCourseId(body.get("courseId"), moduleCode);
        if (resolvedCourseId == null) {
            // Last resort: make course_id nullable in the DB so the group can be created without a course FK.
            // NULL is always allowed by FK constraints; only the NOT NULL constraint blocks us.
            try {
                jdbcTemplate.execute("ALTER TABLE dbo.study_groups ALTER COLUMN course_id UNIQUEIDENTIFIER NULL");
                log.info("[StudyGroup] Made study_groups.course_id nullable; proceeding without a course reference");
                // leave group.courseId as null — Hibernate will omit it, DB now accepts NULL
            } catch (Exception ex) {
                log.error("[StudyGroup] Could not resolve course or alter table: {}", ex.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", "No valid course found. Please provide a valid courseId."));
            }
        } else {
            group.setCourseId(resolvedCourseId);
        }

        try {
            log.info("[StudyGroup] Attempting to save new group: name='{}', createdBy={}", name, user.getId());
            groupRepository.save(group);
            log.info("[StudyGroup] Successfully saved group id={} into dbo.study_groups", group.getId());
        } catch (Exception ex) {
            log.error("[StudyGroup] FAILED to save group into dbo.study_groups: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save study group: " + ex.getMessage()));
        }

        StudyGroupMember ownerMembership = new StudyGroupMember();
        ownerMembership.setGroupId(group.getId());
        ownerMembership.setUserId(user.getId());
        ownerMembership.setRole("owner");
        ownerMembership.setMembershipStatus("approved");
        try {
            groupMemberRepository.save(ownerMembership);
            log.info("[StudyGroup] Owner membership saved for groupId={} userId={}", group.getId(), user.getId());
        } catch (Exception ex) {
            log.error("[StudyGroup] FAILED to save owner membership: {}", ex.getMessage(), ex);
            // Group was saved; return success but log the member insert failure
        }

        return ResponseEntity.ok(buildGroupDetails(group, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGroup(@PathVariable UUID id, Authentication auth, @RequestBody Map<String, Object> body) {
        User user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, user.getId())) return ResponseEntity.status(403).body(Map.of("error", "Not authorized to edit this group"));

        String name = firstNonBlank(asString(body.get("name")), group.getName());
        String moduleCode = firstNonBlank(asString(body.get("moduleCode")), asString(body.get("courseCode")), group.getModuleCode());
        String description = firstNonBlank(asString(body.get("description")), group.getDescription());
        String topic = firstNonBlank(asString(body.get("topic")), group.getTopic(), moduleCode);
        String studyMode = firstNonBlank(asString(body.get("studyMode")), group.getStudyMode(), "online").toLowerCase();
        String location = body.containsKey("location") ? asString(body.get("location")) : group.getLocation();
        String meetingLink = body.containsKey("meetingLink") ? asString(body.get("meetingLink")) : group.getMeetingLink();
        String preferredSchedule = firstNonBlank(asString(body.get("preferredSchedule")), group.getPreferredSchedule());
        Short maxMembers = body.containsKey("maxMembers") ? asShort(body.get("maxMembers"), group.getMaxMembers()) : group.getMaxMembers();
        boolean approvalRequired = body.containsKey("approvalRequired")
            ? asBoolean(body.get("approvalRequired"), false)
            : Boolean.TRUE.equals(group.getApprovalRequired());

        String validationError = validateGroupInput(name, moduleCode, description, studyMode, location, meetingLink, preferredSchedule, maxMembers);
        if (validationError != null) return ResponseEntity.badRequest().body(Map.of("error", validationError));

        long approvedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(group.getId(), "approved");
        if (maxMembers != null && approvedCount > maxMembers) {
            return ResponseEntity.badRequest().body(Map.of("error", "Max members cannot be below current approved member count"));
        }

        group.setName(name);
        group.setModuleCode(moduleCode);
        group.setDescription(description);
        group.setTopic(topic);
        group.setStudyMode(studyMode);
        group.setLocation(location);
        group.setMeetingLink(meetingLink);
        group.setPreferredSchedule(preferredSchedule);
        group.setMaxMembers(maxMembers);
        group.setApprovalRequired(approvalRequired);
        refreshGroupStatus(group);
        groupRepository.save(group);

        return ResponseEntity.ok(buildGroupDetails(group, user));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinGroup(@PathVariable java.util.UUID id, Authentication auth) {
        User user = getCurrentUser(auth);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        }

        if ("dissolved".equalsIgnoreCase(group.getStatus())) {
            return ResponseEntity.status(400).body(Map.of("error", "Group is dissolved"));
        }

        Optional<StudyGroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(id, user.getId());
        if (existing.isPresent()) {
            StudyGroupMember found = existing.get();
            long count = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
            return ResponseEntity.ok(Map.of(
                "joined", "approved".equalsIgnoreCase(found.getMembershipStatus()),
                "alreadyJoined", true,
                "membershipStatus", found.getMembershipStatus(),
                "groupId", id,
                "memberCount", count
            ));
        }

        long currentCount = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
        Short maxMembers = group.getMaxMembers();
        if (maxMembers != null && currentCount >= maxMembers) {
            group.setStatus("full");
            groupRepository.save(group);
            return ResponseEntity.status(400).body(Map.of("error", "Group is full"));
        }

        StudyGroupMember membership = new StudyGroupMember();
        membership.setGroupId(id);
        membership.setUserId(user.getId());
        membership.setRole("member");
        membership.setMembershipStatus(Boolean.TRUE.equals(group.getApprovalRequired()) ? "pending" : "approved");
        groupMemberRepository.save(membership);

        long updatedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
        refreshGroupStatus(group);
        groupRepository.save(group);
        return ResponseEntity.ok(Map.of(
            "joined", "approved".equalsIgnoreCase(membership.getMembershipStatus()),
            "alreadyJoined", false,
            "membershipStatus", membership.getMembershipStatus(),
            "groupId", id,
            "memberCount", updatedCount
        ));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leaveGroup(@PathVariable java.util.UUID id, Authentication auth) {
        User user = getCurrentUser(auth);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        }

        Optional<StudyGroupMember> membership = groupMemberRepository.findByGroupIdAndUserId(id, user.getId());
        if (membership.isEmpty()) {
            long count = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
            return ResponseEntity.ok(Map.of(
                "left", true,
                "notMember", true,
                "groupId", id,
                "memberCount", count
            ));
        }

        if ("owner".equalsIgnoreCase(membership.get().getRole())) {
            return ResponseEntity.status(400).body(Map.of("error", "Group owner cannot leave the group"));
        }

        groupMemberRepository.deleteByGroupIdAndUserId(id, user.getId());
        long updatedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
        refreshGroupStatus(group);
        groupRepository.save(group);

        return ResponseEntity.ok(Map.of(
            "left", true,
            "notMember", false,
            "groupId", id,
            "memberCount", updatedCount
        ));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> getMembers(@PathVariable UUID id, Authentication auth) {
        User user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isMember(group.getId(), user.getId()) && !isAdmin(group, user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to view members"));
        }

        List<StudyGroupMember> members = groupMemberRepository.findByGroupId(id);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (StudyGroupMember member : members) {
            User mUser = userRepository.findById(member.getUserId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", member.getUserId());
            row.put("role", member.getRole());
            row.put("membershipStatus", member.getMembershipStatus());
            row.put("joinedAt", member.getJoinedAt());
            row.put("email", mUser != null ? mUser.getEmail() : null);
            row.put("firstName", mUser != null ? mUser.getFirstName() : null);
            row.put("lastName", mUser != null ? mUser.getLastName() : null);
            payload.add(row);
        }
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{id}/members/invite")
    public ResponseEntity<?> inviteMember(@PathVariable UUID id, Authentication auth, @RequestBody Map<String, Object> body) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can invite members"));

        String email = asString(body.get("email"));
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));

        User target = userRepository.findByEmail(email).orElse(null);
        if (target == null) return ResponseEntity.status(404).body(Map.of("error", "Target user not found"));

        Optional<StudyGroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(id, target.getId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("invited", false, "message", "User is already associated with this group"));
        }

        long approvedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
        if (group.getMaxMembers() != null && approvedCount >= group.getMaxMembers()) {
            group.setStatus("full");
            groupRepository.save(group);
            return ResponseEntity.badRequest().body(Map.of("error", "Group is full"));
        }

        StudyGroupMember membership = new StudyGroupMember();
        membership.setGroupId(id);
        membership.setUserId(target.getId());
        membership.setRole("member");
        membership.setMembershipStatus("invited");
        groupMemberRepository.save(membership);

        return ResponseEntity.ok(Map.of("invited", true, "email", email));
    }

    @PostMapping("/{id}/members/{userId}/approve")
    public ResponseEntity<?> approveMember(@PathVariable UUID id, @PathVariable UUID userId, Authentication auth) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can approve requests"));

        StudyGroupMember membership = groupMemberRepository.findByGroupIdAndUserId(id, userId).orElse(null);
        if (membership == null) return ResponseEntity.status(404).body(Map.of("error", "Membership not found"));

        if ("approved".equalsIgnoreCase(membership.getMembershipStatus())) {
            return ResponseEntity.ok(Map.of("approved", true, "alreadyApproved", true));
        }

        long approvedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(id, "approved");
        if (group.getMaxMembers() != null && approvedCount >= group.getMaxMembers()) {
            group.setStatus("full");
            groupRepository.save(group);
            return ResponseEntity.badRequest().body(Map.of("error", "Group is full"));
        }

        membership.setMembershipStatus("approved");
        groupMemberRepository.save(membership);
        refreshGroupStatus(group);
        groupRepository.save(group);
        return ResponseEntity.ok(Map.of("approved", true));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable UUID id, @PathVariable UUID userId, Authentication auth) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can remove members"));

        if (group.getCreatedBy() != null && group.getCreatedBy().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Use transfer ownership before removing owner"));
        }

        Optional<StudyGroupMember> existing = groupMemberRepository.findByGroupIdAndUserId(id, userId);
        if (existing.isEmpty()) {
            return ResponseEntity.ok(Map.of("removed", false, "message", "User is not a member"));
        }

        groupMemberRepository.deleteByGroupIdAndUserId(id, userId);
        refreshGroupStatus(group);
        groupRepository.save(group);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    @PostMapping("/{id}/transfer-ownership")
    public ResponseEntity<?> transferOwnership(@PathVariable UUID id, Authentication auth, @RequestBody Map<String, Object> body) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isOwner(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only the current owner can transfer ownership"));

        UUID newOwnerId = parseUuid(asString(body.get("newOwnerUserId")));
        if (newOwnerId == null) return ResponseEntity.badRequest().body(Map.of("error", "newOwnerUserId is required"));

        StudyGroupMember currentOwnerMember = groupMemberRepository.findByGroupIdAndUserId(id, actor.getId()).orElse(null);
        StudyGroupMember newOwnerMember = groupMemberRepository.findByGroupIdAndUserId(id, newOwnerId).orElse(null);
        if (newOwnerMember == null || !"approved".equalsIgnoreCase(newOwnerMember.getMembershipStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "New owner must be an approved member"));
        }

        if (currentOwnerMember != null) {
            currentOwnerMember.setRole("admin");
            groupMemberRepository.save(currentOwnerMember);
        }
        newOwnerMember.setRole("owner");
        newOwnerMember.setMembershipStatus("approved");
        groupMemberRepository.save(newOwnerMember);

        group.setCreatedBy(newOwnerId);
        groupRepository.save(group);
        return ResponseEntity.ok(Map.of("transferred", true, "newOwnerUserId", newOwnerId));
    }

    @PostMapping("/{id}/dissolve")
    public ResponseEntity<?> dissolveGroup(@PathVariable UUID id, Authentication auth) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can dissolve group"));

        try {
            jdbcTemplate.update("UPDATE study_groups SET status = 'dissolved' WHERE id = ?", group.getId());
        } catch (Exception ex) {
            log.error("[Dissolve] DB error for group {}: {}", id, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to dissolve group: " + ex.getMessage()));
        }
        return ResponseEntity.ok(Map.of("dissolved", true));
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<?> listSessions(@PathVariable UUID id, Authentication auth) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isMember(id, actor.getId()) && !isAdmin(group, actor.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to view sessions"));
        }
        return ResponseEntity.ok(studySessionRepository.findByGroupIdOrderByStartsAtAsc(id));
    }

    @PostMapping("/{id}/sessions")
    public ResponseEntity<?> createSession(@PathVariable UUID id, Authentication auth, @RequestBody Map<String, Object> body) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can create sessions"));

        String title = asString(body.get("title"));
        String notes = asString(body.get("notes"));
        String location = asString(body.get("location"));
        String meetingLink = asString(body.get("meetingLink"));
        LocalDateTime startsAt = parseDateTime(asString(body.get("startsAt")));
        LocalDateTime endsAt = parseDateTime(asString(body.get("endsAt")));

        if (title == null || title.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Session title is required"));
        if (startsAt == null) return ResponseEntity.badRequest().body(Map.of("error", "startsAt is required in ISO datetime format"));
        if (endsAt != null && endsAt.isBefore(startsAt)) return ResponseEntity.badRequest().body(Map.of("error", "endsAt must be after startsAt"));

        if ("online".equalsIgnoreCase(group.getStudyMode())) {
            if (meetingLink == null || meetingLink.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Meeting link is required for online sessions"));
            }
        } else if (location == null || location.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Location is required for in-person sessions"));
        }

        StudySession session = new StudySession();
        session.setGroupId(id);
        session.setTitle(title);
        session.setNotes(notes);
        session.setStartsAt(startsAt);
        session.setEndsAt(endsAt);
        session.setLocation(location);
        session.setMeetingLink(meetingLink);
        session.setCreatedBy(actor.getId());
        studySessionRepository.save(session);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", session.getId().toString());
        resp.put("groupId", session.getGroupId().toString());
        resp.put("title", session.getTitle());
        resp.put("notes", session.getNotes() != null ? session.getNotes() : "");
        resp.put("startsAt", session.getStartsAt().toString());
        resp.put("endsAt", session.getEndsAt() != null ? session.getEndsAt().toString() : null);
        resp.put("location", session.getLocation() != null ? session.getLocation() : "");
        resp.put("meetingLink", session.getMeetingLink() != null ? session.getMeetingLink() : "");
        resp.put("createdBy", session.getCreatedBy().toString());
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{id}/sessions/{sessionId}")
    public ResponseEntity<?> updateSession(@PathVariable UUID id, @PathVariable UUID sessionId, Authentication auth, @RequestBody Map<String, Object> body) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can update sessions"));

        StudySession session = studySessionRepository.findById(sessionId).orElse(null);
        if (session == null || !id.equals(session.getGroupId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Session not found"));
        }

        String title = firstNonBlank(asString(body.get("title")), session.getTitle());
        String notes = body.containsKey("notes") ? asString(body.get("notes")) : session.getNotes();
        String location = body.containsKey("location") ? asString(body.get("location")) : session.getLocation();
        String meetingLink = body.containsKey("meetingLink") ? asString(body.get("meetingLink")) : session.getMeetingLink();
        LocalDateTime startsAt = body.containsKey("startsAt") ? parseDateTime(asString(body.get("startsAt"))) : session.getStartsAt();
        LocalDateTime endsAt = body.containsKey("endsAt") ? parseDateTime(asString(body.get("endsAt"))) : session.getEndsAt();

        if (title == null || title.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Session title is required"));
        if (startsAt == null) return ResponseEntity.badRequest().body(Map.of("error", "startsAt is required in ISO datetime format"));
        if (endsAt != null && endsAt.isBefore(startsAt)) return ResponseEntity.badRequest().body(Map.of("error", "endsAt must be after startsAt"));

        session.setTitle(title);
        session.setNotes(notes);
        session.setLocation(location);
        session.setMeetingLink(meetingLink);
        session.setStartsAt(startsAt);
        session.setEndsAt(endsAt);
        studySessionRepository.save(session);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", session.getId().toString());
        resp.put("groupId", session.getGroupId().toString());
        resp.put("title", session.getTitle());
        resp.put("notes", session.getNotes() != null ? session.getNotes() : "");
        resp.put("startsAt", session.getStartsAt().toString());
        resp.put("endsAt", session.getEndsAt() != null ? session.getEndsAt().toString() : null);
        resp.put("location", session.getLocation() != null ? session.getLocation() : "");
        resp.put("meetingLink", session.getMeetingLink() != null ? session.getMeetingLink() : "");
        resp.put("createdBy", session.getCreatedBy().toString());
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable UUID id, @PathVariable UUID sessionId, Authentication auth) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can delete sessions"));

        StudySession session = studySessionRepository.findById(sessionId).orElse(null);
        if (session == null || !id.equals(session.getGroupId())) {
            return ResponseEntity.status(404).body(Map.of("error", "Session not found"));
        }
        studySessionRepository.delete(session);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(@PathVariable java.util.UUID id, Authentication auth) {
        User user = getCurrentUser(auth);
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        }
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        if (!isMember(id, user.getId()) && !isAdmin(group, user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to view this group"));
        }
        return ResponseEntity.ok(buildGroupDetails(group, user));
    }

    /**
     * Debug endpoint: confirms the app can read from dbo.study_groups.
     * GET /api/groups/debug/db-check
     * Returns row count + sample rows so you can verify Azure SQL connectivity.
     */
    @GetMapping("/debug/db-check")
    public ResponseEntity<?> dbCheck() {
        try {
            long count = groupRepository.count();
            List<StudyGroup> sample = groupRepository.findAll().stream().limit(10).toList();
            List<Map<String, Object>> rows = sample.stream().map(g -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", g.getId());
                row.put("name", g.getName());
                row.put("moduleCode", g.getModuleCode());
                row.put("status", g.getStatus());
                row.put("createdBy", g.getCreatedBy());
                row.put("createdAt", g.getCreatedAt());
                return row;
            }).toList();
            return ResponseEntity.ok(Map.of(
                "table", "dbo.study_groups",
                "totalRows", count,
                "sample", rows
            ));
        } catch (Exception ex) {
            log.error("[DbCheck] Failed to query dbo.study_groups: {}", ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of(
                "table", "dbo.study_groups",
                "error", ex.getMessage()
            ));
        }
    }

    private User getCurrentUser(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean isOwner(StudyGroup group, UUID userId) {
        return group.getCreatedBy() != null && group.getCreatedBy().equals(userId);
    }

    private boolean isAdmin(StudyGroup group, UUID userId) {
        if (isOwner(group, userId)) return true;
        StudyGroupMember membership = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);
        return membership != null
            && "approved".equalsIgnoreCase(membership.getMembershipStatus())
            && ("owner".equalsIgnoreCase(membership.getRole()) || "admin".equalsIgnoreCase(membership.getRole()));
    }

    private boolean isMember(UUID groupId, UUID userId) {
        StudyGroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        return membership != null && "approved".equalsIgnoreCase(membership.getMembershipStatus());
    }

    private void refreshGroupStatus(StudyGroup group) {
        if ("dissolved".equalsIgnoreCase(group.getStatus())) return;
        long approvedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(group.getId(), "approved");
        Short maxMembers = group.getMaxMembers();
        if (maxMembers != null && approvedCount >= maxMembers) {
            group.setStatus("full");
        } else {
            group.setStatus("active");
        }
    }

    private String validateGroupInput(String name,
                                      String moduleCode,
                                      String description,
                                      String studyMode,
                                      String location,
                                      String meetingLink,
                                      String preferredSchedule,
                                      Short maxMembers) {
        if (name == null || name.isBlank()) return "Group name is required";
        if (moduleCode == null || moduleCode.isBlank()) return "Module/subject is required";
        if (description == null || description.isBlank()) return "Description is required";
        if (preferredSchedule == null || preferredSchedule.isBlank()) return "Preferred schedule is required";
        if (!"online".equalsIgnoreCase(studyMode) && !"in-person".equalsIgnoreCase(studyMode) && !"hybrid".equalsIgnoreCase(studyMode)) {
            return "Study mode must be one of: online, in-person, hybrid";
        }
        if (("in-person".equalsIgnoreCase(studyMode) || "hybrid".equalsIgnoreCase(studyMode))
            && (location == null || location.isBlank())) {
            return "Location is required for in-person/hybrid groups";
        }
        if (("online".equalsIgnoreCase(studyMode) || "hybrid".equalsIgnoreCase(studyMode))
            && (meetingLink == null || meetingLink.isBlank())) {
            return "Meeting link is required for online/hybrid groups";
        }
        if (maxMembers == null || maxMembers < 2) return "Max members must be at least 2";
        return null;
    }

    private Map<String, Object> buildGroupSummary(StudyGroup group, User currentUser) {
        long approvedCount = groupMemberRepository.countByGroupIdAndMembershipStatus(group.getId(), "approved");
        long pendingCount = groupMemberRepository.countByGroupIdAndMembershipStatus(group.getId(), "pending");
        StudyGroupMember currentMembership = currentUser == null
            ? null
            : groupMemberRepository.findByGroupIdAndUserId(group.getId(), currentUser.getId()).orElse(null);

        User owner = group.getCreatedBy() == null ? null : userRepository.findById(group.getCreatedBy()).orElse(null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", group.getId());
        row.put("name", firstNonBlank(group.getName(), group.getTopic(), "Study Group"));
        row.put("moduleCode", firstNonBlank(group.getModuleCode(), group.getTopic(), "General"));
        row.put("courseCode", firstNonBlank(group.getModuleCode(), group.getTopic(), "General"));
        row.put("topic", group.getTopic());
        row.put("description", group.getDescription());
        row.put("studyMode", group.getStudyMode());
        row.put("location", group.getLocation());
        row.put("meetingLink", group.getMeetingLink());
        row.put("preferredSchedule", group.getPreferredSchedule());
        row.put("createdBy", group.getCreatedBy());
        row.put("ownerName", owner == null ? null : ((owner.getFirstName() + " " + owner.getLastName()).trim()));
        row.put("maxMembers", group.getMaxMembers());
        row.put("status", group.getStatus());
        row.put("approvalRequired", Boolean.TRUE.equals(group.getApprovalRequired()));
        row.put("createdAt", group.getCreatedAt());
        row.put("memberCount", approvedCount);
        row.put("pendingCount", pendingCount);
        row.put("joined", currentMembership != null && "approved".equalsIgnoreCase(currentMembership.getMembershipStatus()));
        row.put("membershipStatus", currentMembership != null ? currentMembership.getMembershipStatus() : null);
        row.put("isAdmin", currentUser != null && isAdmin(group, currentUser.getId()));
        return row;
    }

    private Map<String, Object> buildGroupDetails(StudyGroup group, User currentUser) {
        Map<String, Object> details = new LinkedHashMap<>(buildGroupSummary(group, currentUser));
        details.put("members", getMembersPayload(group.getId()));
        details.put("sessions", studySessionRepository.findByGroupIdOrderByStartsAtAsc(group.getId()));
        return details;
    }

    private List<Map<String, Object>> getMembersPayload(UUID groupId) {
        List<StudyGroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<Map<String, Object>> payload = new ArrayList<>();
        for (StudyGroupMember member : members) {
            User mUser = userRepository.findById(member.getUserId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", member.getUserId());
            row.put("role", member.getRole());
            row.put("membershipStatus", member.getMembershipStatus());
            row.put("joinedAt", member.getJoinedAt());
            row.put("email", mUser != null ? mUser.getEmail() : null);
            row.put("firstName", mUser != null ? mUser.getFirstName() : null);
            row.put("lastName", mUser != null ? mUser.getLastName() : null);
            payload.add(row);
        }
        return payload;
    }

    private String asString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private Short asShort(Object value, Short defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.shortValue();
        try {
            return Short.parseShort(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }

    private UUID resolveCourseId(Object courseIdValue, String moduleCode) {
        String normalizedModuleCode = (moduleCode == null || moduleCode.isBlank())
                ? "general"
                : moduleCode.trim().toLowerCase();

        if (courseIdValue instanceof String raw && !raw.isBlank()) {
            try {
                UUID parsed = UUID.fromString(raw.trim());
                if (courseExists(parsed)) {
                    return parsed;
                }
                log.warn("[StudyGroup] Provided courseId '{}' does not exist in dbo.courses.", raw);
            } catch (IllegalArgumentException ex) {
                log.warn("[StudyGroup] Invalid courseId '{}' in request.", raw);
            }
        }

        UUID byModule = findCourseIdByModule(normalizedModuleCode);
        if (byModule != null) {
            return byModule;
        }

        UUID anyExisting = findAnyCourseId();
        if (anyExisting != null) {
            return anyExisting;
        }

        UUID created = createFallbackCourse(moduleCode);
        if (created != null) {
            return created;
        }

        return findAnyCourseId();
    }

    private boolean courseExists(UUID courseId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM dbo.courses WHERE id = ?",
                    Integer.class,
                    courseId
            );
            if (count != null && count > 0) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM dbo.courses WHERE course_id = ?",
                    Integer.class,
                    courseId
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            log.debug("[StudyGroup] courseExists lookup failed for '{}': {}", courseId, ex.getMessage());
            return false;
        }
    }

    private UUID findCourseIdByModule(String normalizedModuleCode) {
        List<Object[]> attempts = List.of(
                new Object[]{"SELECT TOP 1 id FROM dbo.courses WHERE LOWER(code)=? OR LOWER(name)=?", new Object[]{normalizedModuleCode, normalizedModuleCode}},
                new Object[]{"SELECT TOP 1 id FROM dbo.courses WHERE LOWER(module_code)=? OR LOWER(course_code)=? OR LOWER(name)=?", new Object[]{normalizedModuleCode, normalizedModuleCode, normalizedModuleCode}},
                new Object[]{"SELECT TOP 1 course_id FROM dbo.courses WHERE LOWER(code)=? OR LOWER(name)=?", new Object[]{normalizedModuleCode, normalizedModuleCode}},
                new Object[]{"SELECT TOP 1 course_id FROM dbo.courses WHERE LOWER(module_code)=? OR LOWER(course_code)=? OR LOWER(name)=?", new Object[]{normalizedModuleCode, normalizedModuleCode, normalizedModuleCode}}
        );

        for (Object[] attempt : attempts) {
            String sql = (String) attempt[0];
            Object[] params = (Object[]) attempt[1];
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
                if (!rows.isEmpty()) {
                    UUID parsed = extractUuid(rows.get(0).values().stream().findFirst().orElse(null));
                    if (parsed != null) {
                        return parsed;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private UUID findAnyCourseId() {
        List<String> attempts = List.of(
                "SELECT TOP 1 id FROM dbo.courses",
                "SELECT TOP 1 course_id FROM dbo.courses"
        );

        for (String sql : attempts) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                if (!rows.isEmpty()) {
                    UUID parsed = extractUuid(rows.get(0).values().stream().findFirst().orElse(null));
                    if (parsed != null) {
                        return parsed;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private UUID createFallbackCourse(String moduleCode) {
        String code = (moduleCode == null || moduleCode.isBlank()) ? "GENERAL" : moduleCode.trim().toUpperCase();
        String name = code + " Course";

        // Strategy 1: Discover the actual dbo.courses schema and build a dynamic INSERT
        try {
            List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT " +
                    "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='courses' ORDER BY ORDINAL_POSITION"
            );
            log.info("[StudyGroup] dbo.courses schema: {}",
                    cols.stream().map(c -> c.get("COLUMN_NAME") + "(" + c.get("DATA_TYPE") + ",nullable=" + c.get("IS_NULLABLE") + ")")
                            .collect(Collectors.joining(",")));

            List<String> colDefs = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (Map<String, Object> col : cols) {
                String colName = String.valueOf(col.get("COLUMN_NAME"));
                String dataType = String.valueOf(col.get("DATA_TYPE")).toLowerCase();
                boolean nullable = "YES".equalsIgnoreCase(String.valueOf(col.get("IS_NULLABLE")));
                Object defaultVal = col.get("COLUMN_DEFAULT");
                boolean hasDefault = defaultVal != null && !String.valueOf(defaultVal).equalsIgnoreCase("null");
                if (nullable || hasDefault) continue; // skip optional columns

                colDefs.add(colName);
                String lower = colName.toLowerCase();
                if (dataType.equals("uniqueidentifier")) {
                    params.add(UUID.randomUUID());
                } else if (dataType.contains("int")) {
                    params.add(0);
                } else if (dataType.equals("bit")) {
                    params.add(false);
                } else {
                    params.add(lower.contains("code") ? code : name);
                }
            }
            if (!colDefs.isEmpty()) {
                String insertSql = "INSERT INTO dbo.courses (" +
                        String.join(", ", colDefs) + ") VALUES (" +
                        String.join(", ", Collections.nCopies(colDefs.size(), "?")) + ")";
                log.info("[StudyGroup] Dynamic course INSERT: {}", insertSql);
                jdbcTemplate.update(insertSql, params.toArray());
                UUID created = findAnyCourseId();
                if (created != null) {
                    log.info("[StudyGroup] Created fallback course via schema discovery, id={}", created);
                    return created;
                }
            }
        } catch (Exception ex) {
            log.warn("[StudyGroup] Schema-discovery course insert failed: {}", ex.getMessage());
        }

        // Strategy 2: DEFAULT VALUES (works when every column has a default)
        try {
            jdbcTemplate.update("INSERT INTO dbo.courses DEFAULT VALUES");
            UUID created = findAnyCourseId();
            if (created != null) {
                return created;
            }
        } catch (Exception ignored) {
        }

        // Strategy 3: Known column-name patterns
        List<Object[]> attempts = List.of(
                new Object[]{"INSERT INTO dbo.courses (id, code, name) VALUES (NEWID(), ?, ?)", new Object[]{code, name}},
                new Object[]{"INSERT INTO dbo.courses (id, name) VALUES (NEWID(), ?)", new Object[]{name}},
                new Object[]{"INSERT INTO dbo.courses (id, course_code, name) VALUES (NEWID(), ?, ?)", new Object[]{code, name}}
        );

        for (Object[] attempt : attempts) {
            String sql = (String) attempt[0];
            Object[] ps = (Object[]) attempt[1];
            try {
                jdbcTemplate.update(sql, ps);
                UUID created = findAnyCourseId();
                if (created != null) {
                    log.info("[StudyGroup] Created fallback course '{}', id={}", code, created);
                    return created;
                }
            } catch (Exception ex) {
                log.debug("[StudyGroup] Fallback course insert attempt failed: {}", ex.getMessage());
            }
        }

        return null;
    }

    private UUID extractUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (Exception ex) {
            return null;
        }
    }
}

