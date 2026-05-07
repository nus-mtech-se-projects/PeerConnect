package mtech.swe5006.peerconnect.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import mtech.swe5006.peerconnect.data.sql.StudyGroup;
import mtech.swe5006.peerconnect.data.sql.StudySession;
import mtech.swe5006.peerconnect.dto.CreateAnnouncementRequest;

/**
 * Posts a combined auto-announcement when an owner edits group details
 * and the group has {@code autoAnnounceEnabled = true}.
 * <p>
 * The diff-to-body transformation is a pure function ({@link #buildUpdateSummary})
 * so it can be unit-tested without a Spring context or a database.
 */
@Component
@RequiredArgsConstructor
public class StudyGroupAutoAnnouncer {

    private static final Logger log = LoggerFactory.getLogger(StudyGroupAutoAnnouncer.class);

    /** Max title length from {@link CreateAnnouncementRequest}. */
    private static final int MAX_TITLE_LENGTH = 200;

    /** Max content length from {@link CreateAnnouncementRequest}. */
    private static final int MAX_CONTENT_LENGTH = 4000;

    private static final DateTimeFormatter SCHEDULE_FORMATTER =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm");

    private final AnnouncementService announcementService;

    /**
     * Posts an auto-announcement if the group has the feature enabled and the diff
     * is non-empty. Swallows and logs any failure so the caller's save path is never
     * broken by announcement posting issues.
     */
    public void maybePostUpdateAnnouncement(GroupSnapshot before, StudyGroup after, UUID editorUserId) {
        if (!after.isAutoAnnounceEnabled()) return;

        UpdateSummary summary = buildUpdateSummary(before, after);
        if (summary.isEmpty()) return;

        try {
            announcementService.createAnnouncement(
                after.getId(),
                editorUserId,
                new CreateAnnouncementRequest(summary.title(), summary.content()));
        } catch (Exception ex) {
            log.warn("[AutoAnnounce] Failed to post update announcement for group {}: {}",
                after.getId(), ex.getMessage());
        }
    }

    /**
     * Posts an auto-announcement summarising a newly created study session.
     * Respects the per-create {@code autoAnnounceEnabled} flag (which is
     * independent of the group-level setting). Swallows and logs any failure
     * so the caller's save path is never broken by announcement posting issues.
     */
    public void maybePostSessionCreated(StudyGroup group, StudySession session,
                                        UUID editorUserId, boolean autoAnnounceEnabled) {
        if (!autoAnnounceEnabled) return;
        if (group == null || session == null) return;

        SessionSummary summary = buildSessionCreatedSummary(session);
        if (summary.isEmpty()) return;

        try {
            announcementService.createAnnouncement(
                group.getId(),
                editorUserId,
                new CreateAnnouncementRequest(summary.title(), summary.content()));
        } catch (Exception ex) {
            log.warn("[AutoAnnounce] Failed to post session-created announcement for group {}: {}",
                group.getId(), ex.getMessage());
        }
    }

    /**
     * Builds the title + body summarising a newly created session. Pure function
     * so it can be unit-tested without a Spring context or a database.
     */
    public static SessionSummary buildSessionCreatedSummary(StudySession session) {
        if (session == null) return SessionSummary.empty();

        String sessionTitle = normalize(session.getTitle());
        String titleLabel = sessionTitle != null ? sessionTitle : "Untitled session";
        String title = truncate("New session scheduled: " + titleLabel, MAX_TITLE_LENGTH);

        List<String> lines = new ArrayList<>();
        if (session.getStartsAt() != null) {
            String starts = formatSchedule(session.getStartsAt());
            if (session.getEndsAt() != null) {
                lines.add("When: " + starts + " -> " + formatSchedule(session.getEndsAt()));
            } else {
                lines.add("When: " + starts);
            }
        }
        String location = normalize(session.getLocation());
        if (location != null) lines.add("Location: " + location);
        String meetingLink = normalize(session.getMeetingLink());
        if (meetingLink != null) lines.add("Meeting Link: " + meetingLink);
        String notes = normalize(session.getNotes());
        if (notes != null) lines.add("Notes: " + notes);

        String content = truncate(String.join("\n", lines), MAX_CONTENT_LENGTH);
        return new SessionSummary(title, content);
    }

    /**
     * Builds the title + body summarising the tracked-field changes between
     * {@code before} and {@code after}. Returns an empty {@link UpdateSummary}
     * when nothing tracked has changed.
     */
    public static UpdateSummary buildUpdateSummary(GroupSnapshot before, StudyGroup after) {
        List<String> lines = new ArrayList<>();
        addChange(lines, "Name", before.name(), after.getName());
        addChange(lines, "Module / Subject", before.moduleCode(), after.getModuleCode());
        addChange(lines, "Topic", before.topic(), after.getTopic());
        addChange(lines, "Study Mode", before.studyMode(), after.getStudyMode());
        addChange(lines, "Venue", before.location(), after.getLocation());
        addChange(lines, "Meeting Link", before.meetingLink(), after.getMeetingLink());
        addChange(lines, "Preferred Schedule",
            formatSchedule(before.preferredSchedule()), formatSchedule(after.getPreferredSchedule()));
        addChange(lines, "Max Members",
            before.maxMembers() == null ? null : before.maxMembers().toString(),
            after.getMaxMembers() == null ? null : after.getMaxMembers().toString());
        addChange(lines, "Description", before.description(), after.getDescription());

        if (lines.isEmpty()) return UpdateSummary.empty();

        String title = truncate("Group details updated", MAX_TITLE_LENGTH);
        String content = truncate(String.join("\n", lines), MAX_CONTENT_LENGTH);
        return new UpdateSummary(title, content);
    }

    private static void addChange(List<String> lines, String label, String oldValue, String newValue) {
        String normalizedOld = normalize(oldValue);
        String normalizedNew = normalize(newValue);
        if (Objects.equals(normalizedOld, normalizedNew)) return;
        lines.add(String.format("%s: %s -> %s",
            label, displayValue(normalizedOld), displayValue(normalizedNew)));
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String displayValue(String value) {
        return value == null ? "(empty)" : value;
    }

    private static String formatSchedule(LocalDateTime schedule) {
        return schedule == null ? null : schedule.format(SCHEDULE_FORMATTER);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }

    /**
     * Immutable snapshot of the tracked fields on a {@link StudyGroup} taken
     * before an update is applied. Using a snapshot avoids subtle bugs where
     * the controller mutates the entity and we'd compare a row against itself.
     */
    public record GroupSnapshot(
        String name,
        String moduleCode,
        String topic,
        String studyMode,
        String location,
        String meetingLink,
        LocalDateTime preferredSchedule,
        Short maxMembers,
        String description
    ) {
        public static GroupSnapshot from(StudyGroup group) {
            return new GroupSnapshot(
                group.getName(),
                group.getModuleCode(),
                group.getTopic(),
                group.getStudyMode(),
                group.getLocation(),
                group.getMeetingLink(),
                group.getPreferredSchedule(),
                group.getMaxMembers(),
                group.getDescription()
            );
        }
    }

    /** Resolved announcement content, or empty when no tracked field changed. */
    public record UpdateSummary(String title, String content) {
        public boolean isEmpty() {
            return title == null || title.isEmpty();
        }
        public static UpdateSummary empty() {
            return new UpdateSummary("", "");
        }
    }

    /** Resolved session-created announcement content, or empty when no usable data. */
    public record SessionSummary(String title, String content) {
        public boolean isEmpty() {
            return title == null || title.isEmpty();
        }
        public static SessionSummary empty() {
            return new SessionSummary("", "");
        }
    }
}
