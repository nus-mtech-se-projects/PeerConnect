package mtech.swe5006.peerconnect.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mtech.swe5006.peerconnect.data.sql.RestrictedUserRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMember;
import mtech.swe5006.peerconnect.data.sql.StudyGroupMemberRepository;
import mtech.swe5006.peerconnect.data.sql.StudyGroupRepository;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.data.sql.StudySessionRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.EmailService;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);
    private static final String SESSION_NOT_FOUND = "Session not found";
    private final StudyGroupRepository groupRepository;
    private final StudyGroupMemberRepository groupMemberRepository;
    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;
    private final RestrictedUserRepository restrictedUserRepository;

    public GroupController(StudyGroupRepository groupRepository,
                           StudyGroupMemberRepository groupMemberRepository,
                           StudySessionRepository studySessionRepository,
                           UserRepository userRepository,
                           JdbcTemplate jdbcTemplate,
                           EmailService emailService,
                           RestrictedUserRepository restrictedUserRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        this.restrictedUserRepository = restrictedUserRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAllGroups(Authentication auth) {
        User currentUser = getCurrentUser(auth);
        List<StudyGroup> groups = groupRepository.findByStatusInOrderByCreatedAtDesc(List.of("active", "full"));

        // Batch-load all blocker IDs for the current user in ONE query, then filter in memory
        if (currentUser != null) {
            UUID uid = currentUser.getId();
            Set<UUID> blockerIds = restrictedUserRepository.findByBlockedId(uid)
                .stream().map(r -> r.getBlockerId()).collect(Collectors.toSet());
            if (!blockerIds.isEmpty()) {
                int before = groups.size();
                groups = groups.stream()
                    .filter(g -> g.getCreatedBy() == null || !blockerIds.contains(g.getCreatedBy()))
                    .toList();
                if (groups.size() < before) {
                    log.info("[AllGroups] Filtered {} restricted groups for user {}", before - groups.size(), uid);
                }
            }
        }

        List<UUID> groupIds = groups.stream().map(StudyGroup::getId).toList();

        // Batch-load ALL members for all groups in ONE query
        List<StudyGroupMember> allMembers = groupIds.isEmpty()
            ? List.of()
            : groupMemberRepository.findByGroupIdIn(groupIds);

        // Aggregate counts and current-user memberships from the batch, no DB calls
        Map<UUID, Long> approvedCounts = new HashMap<>();
        Map<UUID, Long> pendingCounts = new HashMap<>();
        Map<UUID, StudyGroupMember> currentUserMemberships = new HashMap<>();
        UUID currentUserId = currentUser != null ? currentUser.getId() : null;
        for (StudyGroupMember m : allMembers) {
            UUID gid = m.getGroupId();
            if ("approved".equalsIgnoreCase(m.getMembershipStatus())) {
                approvedCounts.merge(gid, 1L, Long::sum);
            } else if ("pending".equalsIgnoreCase(m.getMembershipStatus())) {
                pendingCounts.merge(gid, 1L, Long::sum);
            }
            if (currentUserId != null && currentUserId.equals(m.getUserId())) {
                currentUserMemberships.put(gid, m);
            }
        }

        // Batch-load all distinct group owners in ONE query
        Set<UUID> ownerIds = groups.stream()
            .filter(g -> g.getCreatedBy() != null)
            .map(StudyGroup::getCreatedBy)
            .collect(Collectors.toSet());
        Map<UUID, User> ownerMap = userRepository.findAllById(ownerIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> payload = groups.stream()
            .map(g -> buildGroupSummaryBatched(g, currentUser, approvedCounts, pendingCounts, currentUserMemberships, ownerMap))
            .toList();
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
        String preferredScheduleStr = asString(body.get("preferredSchedule"));
        LocalDateTime preferredSchedule = parseDateTime(preferredScheduleStr);
        Short maxMembers = asShort(body.get("maxMembers"), (short) 10);
        boolean approvalRequired = asBoolean(body.get("approvalRequired"), false);

        if (preferredScheduleStr != null && !preferredScheduleStr.isBlank() && preferredSchedule == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid preferred schedule format. Use ISO format: yyyy-MM-ddTHH:mm:ss"));
        }

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

        try {
            log.info("[StudyGroup] Attempting to save new group: name='{}', createdBy={}", name, user.getId());
            groupRepository.save(group);
            log.info("[StudyGroup] Successfully saved group id={} into dbo.study_groups", group.getId());
        } catch (Exception ex) {
            log.warn("[StudyGroup] JPA save failed, retrying with JDBC fallback: {}", ex.getMessage());
            try {
                saveStudyGroupFallback(group, preferredSchedule);
            } catch (Exception fallbackEx) {
                log.error("[StudyGroup] FAILED to save group into dbo.study_groups: {}", fallbackEx.getMessage(), fallbackEx);
                return ResponseEntity.status(500).body(Map.of("error", "Failed to save study group: " + fallbackEx.getMessage()));
            }
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
        String topic = firstNonBlank(asString(body.get("topic")), group.getTopic());
        String studyMode = firstNonBlank(asString(body.get("studyMode")), group.getStudyMode()).toLowerCase();
        String location = body.containsKey("location") ? asString(body.get("location")) : group.getLocation();
        String meetingLink = body.containsKey("meetingLink") ? asString(body.get("meetingLink")) : group.getMeetingLink();
        String preferredScheduleStr = asString(body.get("preferredSchedule"));
        LocalDateTime preferredSchedule = body.containsKey("preferredSchedule")
            ? parseDateTime(preferredScheduleStr)
            : group.getPreferredSchedule();
        if (preferredScheduleStr != null && !preferredScheduleStr.isBlank() && preferredSchedule == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid preferred schedule format. Use ISO format: yyyy-MM-ddTHH:mm:ss"));
        }
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
        group.setPreferredSchedule(body.containsKey("preferredSchedule") ? preferredSchedule : group.getPreferredSchedule());
        group.setMaxMembers(maxMembers);
        group.setApprovalRequired(approvalRequired);
        refreshGroupStatus(group);
        groupRepository.save(group);

        // Send group-updated email notification to approved members with role "member"
        try {
            String schedule = formatSchedule(group.getPreferredSchedule());
            String[] memberEmails = getApprovedMemberEmails(group.getId());
            String ownerName = resolveOwnerName(group);
            String ownerEmail = resolveOwnerEmail(group);
            if (memberEmails.length > 0) {
                emailService.sendGroupUpdated(
                    memberEmails,
                    group.getName() != null ? group.getName() : "",
                    group.getModuleCode() != null ? group.getModuleCode() : "",
                    group.getTopic() != null ? group.getTopic() : "",
                    schedule,
                    group.getLocation(),
                    group.getMeetingLink(),
                    group.getDescription(),
                    ownerName,
                    ownerEmail
                );
            } else if (ownerEmail != null && !ownerEmail.isBlank()) {
                emailService.sendGroupUpdated(
                    new String[]{ownerEmail},
                    group.getName() != null ? group.getName() : "",
                    group.getModuleCode() != null ? group.getModuleCode() : "",
                    group.getTopic() != null ? group.getTopic() : "",
                    schedule,
                    group.getLocation(),
                    group.getMeetingLink(),
                    group.getDescription(),
                    ownerName,
                    null
                );
            }
        } catch (Exception emailEx) {
            log.warn("[UpdateGroup] Failed to send update emails for group {}: {}", id, emailEx.getMessage());
        }

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

        // Check if the group owner has restricted this user
        if (group.getCreatedBy() != null
                && restrictedUserRepository.existsByBlockerIdAndBlockedId(group.getCreatedBy(), user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You have been restricted from joining this group by the owner"));
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

        // Send join notification email (TO: owner, CC: user)
        try {
            String ownerEmail = resolveOwnerEmail(group);
            String ownerName = resolveOwnerName(group);
            String userName = (user.getFirstName() + " " + user.getLastName()).trim();
            String schedule = formatSchedule(group.getPreferredSchedule());
            emailService.sendUserJoinedGroup(
                ownerEmail,
                user.getEmail(),
                userName,
                group.getName() != null ? group.getName() : "",
                group.getModuleCode() != null ? group.getModuleCode() : "",
                group.getTopic() != null ? group.getTopic() : "",
                schedule
            );
        } catch (Exception emailEx) {
            log.warn("[JoinGroup] Failed to send join notification: {}", emailEx.getMessage());
        }

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

        // Send leave notification email (TO: owner, CC: user)
        try {
            String ownerEmail = resolveOwnerEmail(group);
            String ownerName = resolveOwnerName(group);
            String userName = (user.getFirstName() + " " + user.getLastName()).trim();
            String schedule = formatSchedule(group.getPreferredSchedule());
            emailService.sendUserLeftGroup(
                ownerEmail,
                user.getEmail(),
                userName,
                group.getName() != null ? group.getName() : "",
                group.getModuleCode() != null ? group.getModuleCode() : "",
                group.getTopic() != null ? group.getTopic() : "",
                schedule
            );
        } catch (Exception emailEx) {
            log.warn("[LeaveGroup] Failed to send leave notification: {}", emailEx.getMessage());
        }

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

        // Check if the group owner has restricted this user
        if (group.getCreatedBy() != null
                && restrictedUserRepository.existsByBlockerIdAndBlockedId(group.getCreatedBy(), target.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "This user has been restricted and cannot be invited"));
        }

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

        // Send invitation email notification
        try {
            String inviteeName = (target.getFirstName() + " " + target.getLastName()).trim();
            String schedule = formatSchedule(group.getPreferredSchedule());

            emailService.sendGroupInvitation(
                target.getEmail(),
                inviteeName,
                group.getName() != null ? group.getName() : "",
                group.getModuleCode() != null ? group.getModuleCode() : "",
                group.getTopic() != null ? group.getTopic() : "",
                schedule,
                resolveOwnerName(group),
                resolveOwnerEmail(group)
            );
        } catch (Exception emailEx) {
            log.warn("[Invite] Failed to send invitation email to {}: {}", email, emailEx.getMessage());
        }

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

        // Send approval notification email
        try {
            User member = userRepository.findById(userId).orElse(null);
            String memberName = member != null
                ? ((member.getFirstName() + " " + member.getLastName()).trim())
                : "Member";
            String schedule = formatSchedule(group.getPreferredSchedule());

            if (member != null && member.getEmail() != null) {
                emailService.sendMemberApproved(
                    member.getEmail(),
                    memberName,
                    group.getName() != null ? group.getName() : "",
                    group.getModuleCode() != null ? group.getModuleCode() : "",
                    group.getTopic() != null ? group.getTopic() : "",
                    schedule,
                    resolveOwnerName(group),
                    resolveOwnerEmail(group)
                );
            }
        } catch (Exception emailEx) {
            log.warn("[Approve] Failed to send approval email for user {} in group {}: {}", userId, id, emailEx.getMessage());
        }

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

        // Resolve member details before deletion for email notification
        User member = userRepository.findById(userId).orElse(null);

        groupMemberRepository.deleteByGroupIdAndUserId(id, userId);
        refreshGroupStatus(group);
        try {
            groupRepository.save(group);
        } catch (Exception ex) {
            log.error("[RemoveMember] DB error for group {}: {}", id, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to remove member. Please try again."));
        }

        // Send rejection notification email
        try {
            String memberName = member != null
                ? ((member.getFirstName() + " " + member.getLastName()).trim())
                : "Member";
            String schedule = formatSchedule(group.getPreferredSchedule());

            if (member != null && member.getEmail() != null) {
                emailService.sendMemberRejected(
                    member.getEmail(),
                    memberName,
                    group.getName() != null ? group.getName() : "",
                    group.getModuleCode() != null ? group.getModuleCode() : "",
                    group.getTopic() != null ? group.getTopic() : "",
                    schedule,
                    resolveOwnerName(group),
                    resolveOwnerEmail(group)
                );
            }
        } catch (Exception emailEx) {
            log.warn("[RemoveMember] Failed to send rejection email for user {} in group {}: {}", userId, id, emailEx.getMessage());
        }

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
        if ("dissolved".equalsIgnoreCase(group.getStatus())) return ResponseEntity.ok(Map.of("dissolved", true));

        try {
            group.setStatus("dissolved");
            groupRepository.save(group);
        } catch (Exception ex) {
            log.warn("[Dissolve] JPA save failed for group {}. Retrying with SQL. Cause: {}", id, ex.getMessage());
            try {
                jdbcTemplate.update("UPDATE study_groups SET status = ? WHERE id = ?", "dissolved", group.getId());
            } catch (Exception fallbackEx) {
                log.error("[Dissolve] DB error for group {}: {}", id, fallbackEx.getMessage());
                return ResponseEntity.status(500).body(Map.of("error", "Failed to dissolve group. Please try again."));
            }
        }

        // Send dissolution email to all members
        try {
            String schedule = formatSchedule(group.getPreferredSchedule());

            List<StudyGroupMember> allMembers = groupMemberRepository.findByGroupId(id);
            List<String> memberEmails = allMembers.stream()
                .filter(m -> !"owner".equalsIgnoreCase(m.getRole()))
                .map(m -> userRepository.findById(m.getUserId()).map(User::getEmail).orElse(null))
                .filter(e -> e != null && !e.isBlank())
                .toList();

            String ownerName = resolveOwnerName(group);
            String ownerEmail = resolveOwnerEmail(group);
            if (!memberEmails.isEmpty()) {
                emailService.sendGroupDissolved(
                    memberEmails.toArray(new String[0]),
                    group.getName() != null ? group.getName() : "",
                    group.getModuleCode() != null ? group.getModuleCode() : "",
                    group.getTopic() != null ? group.getTopic() : "",
                    schedule,
                    ownerName,
                    ownerEmail
                );
            } else if (ownerEmail != null && !ownerEmail.isBlank()) {
                emailService.sendGroupDissolved(
                    new String[]{ownerEmail},
                    group.getName() != null ? group.getName() : "",
                    group.getModuleCode() != null ? group.getModuleCode() : "",
                    group.getTopic() != null ? group.getTopic() : "",
                    schedule,
                    ownerName,
                    null
                );
            }
        } catch (Exception emailEx) {
            log.warn("[Dissolve] Failed to send dissolution emails for group {}: {}", id, emailEx.getMessage());
        }

        return ResponseEntity.ok(Map.of("dissolved", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGroup(@PathVariable UUID id, Authentication auth) {
        User actor = getCurrentUser(auth);
        if (actor == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        StudyGroup group = groupRepository.findById(id).orElse(null);
        if (group == null) return ResponseEntity.status(404).body(Map.of("error", "Group not found"));
        if (!isAdmin(group, actor.getId())) return ResponseEntity.status(403).body(Map.of("error", "Only admins can delete group"));

        try {
            jdbcTemplate.update("DELETE FROM study_sessions WHERE group_id = ?", id.toString());
            jdbcTemplate.update("DELETE FROM study_group_members WHERE group_id = ?", id.toString());
            jdbcTemplate.update("DELETE FROM study_groups WHERE id = ?", id.toString());
        } catch (Exception ex) {
            log.error("[Delete] DB error for group {}: {}", id, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete group. Please try again."));
        }
        return ResponseEntity.ok(Map.of("deleted", true));
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
        return ResponseEntity.ok(getSessionsPayload(id));
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
        try {
            studySessionRepository.save(session);
        } catch (Exception ex) {
            log.error("[CreateSession] DB error for group {}: {}", id, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save session. Please try again."));
        }

        // Send session-created email notification to approved members with role "member"
        try {
            String[] memberEmails = getApprovedMemberEmails(group.getId());
            String ownerName = resolveOwnerName(group);
            String ownerEmail = resolveOwnerEmail(group);
            if (memberEmails.length > 0) {
                emailService.sendSessionCreated(
                    memberEmails,
                    group.getName() != null ? group.getName() : "",
                    session.getTitle(),
                    formatSchedule(session.getStartsAt()),
                    session.getEndsAt() != null ? formatSchedule(session.getEndsAt()) : null,
                    session.getLocation(),
                    session.getMeetingLink(),
                    ownerName,
                    ownerEmail
                );
            } else if (ownerEmail != null && !ownerEmail.isBlank()) {
                emailService.sendSessionCreated(
                    new String[]{ownerEmail},
                    group.getName() != null ? group.getName() : "",
                    session.getTitle(),
                    formatSchedule(session.getStartsAt()),
                    session.getEndsAt() != null ? formatSchedule(session.getEndsAt()) : null,
                    session.getLocation(),
                    session.getMeetingLink(),
                    ownerName,
                    null
                );
            }
        } catch (Exception emailEx) {
            log.warn("[CreateSession] Failed to send session-created emails for group {}: {}", id, emailEx.getMessage());
        }

        return ResponseEntity.ok(buildSessionResponse(session));
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
            return ResponseEntity.status(404).body(Map.of("error", SESSION_NOT_FOUND));
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

        // Send session-updated email notification to approved members with role "member"
        try {
            String[] memberEmails = getApprovedMemberEmails(group.getId());
            String ownerName = resolveOwnerName(group);
            String ownerEmail = resolveOwnerEmail(group);
            if (memberEmails.length > 0) {
                emailService.sendSessionUpdated(
                    memberEmails,
                    group.getName() != null ? group.getName() : "",
                    session.getTitle(),
                    formatSchedule(session.getStartsAt()),
                    session.getEndsAt() != null ? formatSchedule(session.getEndsAt()) : null,
                    session.getLocation(),
                    session.getMeetingLink(),
                    ownerName,
                    ownerEmail
                );
            } else if (ownerEmail != null && !ownerEmail.isBlank()) {
                emailService.sendSessionUpdated(
                    new String[]{ownerEmail},
                    group.getName() != null ? group.getName() : "",
                    session.getTitle(),
                    formatSchedule(session.getStartsAt()),
                    session.getEndsAt() != null ? formatSchedule(session.getEndsAt()) : null,
                    session.getLocation(),
                    session.getMeetingLink(),
                    ownerName,
                    null
                );
            }
        } catch (Exception emailEx) {
            log.warn("[UpdateSession] Failed to send session-updated emails for session {} in group {}: {}", sessionId, id, emailEx.getMessage());
        }

        return ResponseEntity.ok(buildSessionResponse(session));
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
            return ResponseEntity.status(404).body(Map.of("error", SESSION_NOT_FOUND));
        }
        // Capture session details before deletion for email
        String deletedTitle = session.getTitle();
        String deletedStartsAt = formatSchedule(session.getStartsAt());

        studySessionRepository.delete(session);

        // Send session-deleted email notification to approved members with role "member"
        try {
            String[] memberEmails = getApprovedMemberEmails(group.getId());
            String ownerName = resolveOwnerName(group);
            String ownerEmail = resolveOwnerEmail(group);
            if (memberEmails.length > 0) {
                emailService.sendSessionDeleted(
                    memberEmails,
                    group.getName() != null ? group.getName() : "",
                    deletedTitle,
                    deletedStartsAt,
                    ownerName,
                    ownerEmail
                );
            } else if (ownerEmail != null && !ownerEmail.isBlank()) {
                emailService.sendSessionDeleted(
                    new String[]{ownerEmail},
                    group.getName() != null ? group.getName() : "",
                    deletedTitle,
                    deletedStartsAt,
                    ownerName,
                    null
                );
            }
        } catch (Exception emailEx) {
            log.warn("[DeleteSession] Failed to send session-deleted emails for session {} in group {}: {}", sessionId, id, emailEx.getMessage());
        }

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

    private String resolveOwnerName(StudyGroup group) {
        User owner = group.getCreatedBy() == null ? null : userRepository.findById(group.getCreatedBy()).orElse(null);
        return owner != null ? (owner.getFirstName() + " " + owner.getLastName()).trim() : "Group Owner";
    }

    private String resolveOwnerEmail(StudyGroup group) {
        User owner = group.getCreatedBy() == null ? null : userRepository.findById(group.getCreatedBy()).orElse(null);
        return owner != null ? owner.getEmail() : "";
    }

    private Map<String, Object> buildSessionResponse(StudySession session) {
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
        return resp;
    }

    /**
     * Return emails of approved members with role "member" only (excludes owner/admin).
     */
    private String[] getApprovedMemberEmails(UUID groupId) {
        List<StudyGroupMember> allMembers = groupMemberRepository.findByGroupId(groupId);
        return allMembers.stream()
            .filter(m -> "member".equalsIgnoreCase(m.getRole())
                      && "approved".equalsIgnoreCase(m.getMembershipStatus()))
            .map(m -> userRepository.findById(m.getUserId()).map(User::getEmail).orElse(null))
            .filter(e -> e != null && !e.isBlank())
            .toArray(String[]::new);
    }

    /**
     * Format a LocalDateTime as "DD-MON-YYYY at HH:mm" (e.g. "20-JAN-2025 at 15:30").
     */
    private String formatSchedule(LocalDateTime dt) {
        if (dt == null) return "Not specified";
        return dt.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH)
             + " at " + dt.format(DateTimeFormatter.ofPattern("HH:mm"));
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
                                      LocalDateTime preferredSchedule,
                                      Short maxMembers) {
        if (name == null || name.isBlank()) return "Group name is required";
        if (moduleCode == null || moduleCode.isBlank()) return "Module/subject is required";
        if (description == null || description.isBlank()) return "Description is required";
        if (preferredSchedule == null) return "Preferred schedule is required";
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
        details.put("sessions", getSessionsPayload(group.getId()));
        return details;
    }

    /** Zero-DB version of buildGroupSummary — uses pre-loaded batch maps from getAllGroups. */
    private Map<String, Object> buildGroupSummaryBatched(StudyGroup group, User currentUser,
                                                          Map<UUID, Long> approvedCounts,
                                                          Map<UUID, Long> pendingCounts,
                                                          Map<UUID, StudyGroupMember> currentUserMemberships,
                                                          Map<UUID, User> ownerMap) {
        UUID gid = group.getId();
        long approvedCount = approvedCounts.getOrDefault(gid, 0L);
        long pendingCount = pendingCounts.getOrDefault(gid, 0L);
        StudyGroupMember currentMembership = currentUserMemberships.get(gid);
        User owner = group.getCreatedBy() != null ? ownerMap.get(group.getCreatedBy()) : null;

        boolean admin = false;
        if (currentUser != null) {
            UUID uid = currentUser.getId();
            if (isOwner(group, uid)) {
                admin = true;
            } else if (currentMembership != null
                    && "approved".equalsIgnoreCase(currentMembership.getMembershipStatus())
                    && ("owner".equalsIgnoreCase(currentMembership.getRole())
                        || "admin".equalsIgnoreCase(currentMembership.getRole()))) {
                admin = true;
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", group.getId());
        row.put("name", firstNonBlank(group.getName(), group.getTopic(), "Study Group"));
        row.put("moduleCode", firstNonBlank(group.getModuleCode(), group.getTopic(), "General"));
        row.put("topic", group.getTopic());
        row.put("description", group.getDescription());
        row.put("studyMode", group.getStudyMode());
        row.put("location", group.getLocation());
        row.put("meetingLink", group.getMeetingLink());
        row.put("preferredSchedule", group.getPreferredSchedule());
        row.put("createdBy", group.getCreatedBy());
        row.put("ownerName", owner == null ? null : (owner.getFirstName() + " " + owner.getLastName()).trim());
        row.put("maxMembers", group.getMaxMembers());
        row.put("status", group.getStatus());
        row.put("approvalRequired", Boolean.TRUE.equals(group.getApprovalRequired()));
        row.put("createdAt", group.getCreatedAt());
        row.put("memberCount", approvedCount);
        row.put("pendingCount", pendingCount);
        row.put("joined", currentMembership != null && "approved".equalsIgnoreCase(currentMembership.getMembershipStatus()));
        row.put("membershipStatus", currentMembership != null ? currentMembership.getMembershipStatus() : null);
        row.put("isAdmin", admin);
        return row;
    }

    private List<Map<String, Object>> getSessionsPayload(UUID groupId) {
        return studySessionRepository.findByGroupIdOrderByStartsAtAsc(groupId).stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId().toString());
            row.put("groupId", s.getGroupId().toString());
            row.put("title", s.getTitle());
            row.put("notes", s.getNotes() != null ? s.getNotes() : "");
            row.put("startsAt", s.getStartsAt() != null ? s.getStartsAt().toString() : null);
            row.put("endsAt", s.getEndsAt() != null ? s.getEndsAt().toString() : null);
            row.put("location", s.getLocation() != null ? s.getLocation() : "");
            row.put("meetingLink", s.getMeetingLink() != null ? s.getMeetingLink() : "");
            row.put("createdBy", s.getCreatedBy().toString());
            return row;
        }).toList();
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
            try {
                return LocalDateTime.parse(value.trim().replace(" ", "T"));
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return null;
    }

    private void saveStudyGroupFallback(StudyGroup group, LocalDateTime preferredSchedule) {
        if (group.getId() == null) group.setId(UUID.randomUUID());
        if (group.getStatus() == null) group.setStatus("active");
        if (group.getCreatedAt() == null) group.setCreatedAt(LocalDateTime.now());

        jdbcTemplate.update(
            """
            INSERT INTO study_groups
                (id, topic, name, module_code, description, meeting_link, preferred_schedule,
                 study_mode, location, created_by, max_members, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            group.getId().toString(),
            group.getTopic(),
            group.getName(),
            group.getModuleCode(),
            group.getDescription(),
            group.getMeetingLink(),
            preferredSchedule,
            group.getStudyMode(),
            group.getLocation(),
            group.getCreatedBy().toString(),
            group.getMaxMembers(),
            group.getStatus(),
            group.getCreatedAt()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("[GroupController] Unhandled exception", ex);
        return ResponseEntity.status(500).body(Map.of(
            "error", ex.getMessage() != null ? ex.getMessage() : "Internal server error"
        ));
    }
}
