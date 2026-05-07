package mtech.swe5006.peerconnect.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service that sends reminder emails to approved study group members
 * for sessions starting in the next 1–2 days.
 *
 * Runs daily at 08:00 AM (Asia/Singapore). Requires @EnableScheduling on the
 * application entry point or a @Configuration class.
 */
@Service
public class SessionReminderService {

    private static final Logger log = LoggerFactory.getLogger(SessionReminderService.class);
    private static final String FROM_ADDRESS = "peerconnectsg@gmail.com";
    private static final String SYS_FOOTER   = "This is a system-generated email. Please do not reply.";
    private static final Set<String> BLOCKED_DOMAINS = Set.of("example.com", "test.com");

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;

    public SessionReminderService(JdbcTemplate jdbcTemplate, JavaMailSender mailSender) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender   = mailSender;
    }

    // ── Scheduler ────────────────────────────────────────────────────────────

    /**
     * Fires every day at 09:00 AM Singapore time.
     * Finds every approved member whose group has a session starting in the
     * next 1–2 calendar days, then sends each one a personalised reminder.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Singapore")
    public void sendUpcomingSessionReminders() {
        LocalDateTime now  = LocalDateTime.now();
        // Window: start of tomorrow  ->  end of the day after tomorrow
        LocalDateTime from = now.plusDays(1).toLocalDate().atStartOfDay();
        LocalDateTime to   = now.plusDays(2).toLocalDate().atTime(23, 59, 59);

        log.info("Session reminder scheduler running. Window: {} -> {}", from, to);

        String sql = """
                SELECT b.first_name,
                       b.last_name,
                       b.email,
                       a.title,
                       a.location,
                       a.meeting_link,
                       a.notes,
                       a.starts_at,
                       a.ends_at,
                       c.name         AS group_name,
                       c.module_code,
                       d.id           AS member_id,
                       d.group_id,
                       d.user_id,
                       d.role,
                       d.membership_status,
                       d.joined_at
                  FROM [dbo].[study_sessions]     a
                  JOIN [dbo].[study_groups]        c ON c.id       = a.group_id
                  JOIN [dbo].[study_group_members] d ON d.group_id = a.group_id
                  JOIN [dbo].[users]               b ON b.id       = d.user_id
                 WHERE 1=1
                   AND d.membership_status = 'approved'
                   AND a.starts_at BETWEEN ? AND ?
                 ORDER BY a.title, b.email
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql, Timestamp.valueOf(from), Timestamp.valueOf(to));

        if (rows.isEmpty()) {
            log.info("No upcoming sessions found for the next 1–2 days.");
            return;
        }

        log.info("Processing {} member-session row(s).", rows.size());

        for (Map<String, Object> row : rows) {
            String email = (String) row.get("email");
            if (isBlockedEmail(email)) {
                log.info("Skipping blocked/invalid email: {}", email);
                continue;
            }

            String firstName   = nullSafe(row.get("first_name"));
            String lastName    = nullSafe(row.get("last_name"));
            String groupName   = nullSafe(row.get("group_name"));
            String moduleCode  = nullSafe(row.get("module_code"));
            String title       = nullSafe(row.get("title"));
            String location    = nullSafe(row.get("location"));
            String meetingLink = nullSafe(row.get("meeting_link"));
            String notes       = nullSafe(row.get("notes"));
            String startsAt    = formatTimestamp(row.get("starts_at"));
            String endsAt      = formatTimestamp(row.get("ends_at"));

            sendReminderEmail(email, firstName, lastName,
                    groupName, moduleCode,
                    title, location, meetingLink, notes,
                    startsAt, endsAt);
        }
    }

    // ── Email sender ──────────────────────────────────────────────────────────

    private void sendReminderEmail(String toEmail,
                                   String firstName, String lastName,
                                   String groupName, String moduleCode,
                                   String title, String location,
                                   String meetingLink, String notes,
                                   String startsAt, String endsAt) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(FROM_ADDRESS);
            msg.setTo(toEmail);
            msg.setSubject("[PeerConnectSG] Reminder: Upcoming Session – Group " + groupName);

            String fullName = (firstName + " " + lastName).trim();

            StringBuilder body = new StringBuilder();
            body.append("Dear ").append(fullName).append(",\n\n")
                .append("This is a friendly reminder about your upcoming study session:\n\n")
                .append("    Group:         ").append(groupName).append("\n")
                .append("    Module Code:   ").append(blankOrNa(moduleCode)).append("\n")
                .append("    Session Title: ").append(title).append("\n")
                .append("    Starts At:     ").append(startsAt).append("\n")
                .append("    Ends At:       ").append(endsAt).append("\n")
                .append("    Location:      ").append(blankOrNa(location)).append("\n")
                .append("    Meeting Link:  ").append(blankOrNa(meetingLink)).append("\n");

            if (!notes.isBlank()) {
                body.append("    Notes:         ").append(notes).append("\n");
            }

            body.append("\nPlease log in to PeerConnectSG for full details.\n\n")
                .append("PeerConnect Team\n\n")
                .append(SYS_FOOTER);

            msg.setText(body.toString());
            mailSender.send(msg);

            log.info("Reminder sent -> {} | session: '{}'", toEmail, title);

        } catch (Exception e) {
            log.error("Failed to send reminder -> {} | session: '{}' | error: {}",
                    toEmail, title, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatTimestamp(Object ts) {
        if (ts == null) return "N/A";
        if (ts instanceof Timestamp t) {
            return t.toLocalDateTime().format(DISPLAY_FMT);
        }
        return ts.toString();
    }

    private boolean isBlockedEmail(String email) {
        if (email == null || email.isBlank()) return true;
        String lower = email.toLowerCase();
        return BLOCKED_DOMAINS.stream().anyMatch(d -> lower.endsWith("@" + d));
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    private String blankOrNa(String value) {
        return (value != null && !value.isBlank()) ? value : "N/A";
    }
}
