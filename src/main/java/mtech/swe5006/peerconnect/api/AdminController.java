package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.service.SessionReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal admin endpoints – NOT secured by JWT on purpose so you can trigger
 * them from Postman or curl during development/testing.
 *
 * IMPORTANT: restrict or remove these endpoints before deploying to production,
 * or add role-based security (e.g. @PreAuthorize("hasRole('ADMIN')")).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SessionReminderService reminderService;

    public AdminController(SessionReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * POST /api/admin/trigger-session-reminders
     *
     * Manually fires the same logic as the daily cron job.
     * Useful for testing without waiting for 09:00 AM SGT.
     */
    @PostMapping("/trigger-session-reminders")
    public ResponseEntity<?> triggerSessionReminders() {
        reminderService.sendUpcomingSessionReminders();
        return ResponseEntity.ok(Map.of("message", "Session reminder job triggered. Check logs for details."));
    }
}
