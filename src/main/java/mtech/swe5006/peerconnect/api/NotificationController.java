package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.NotificationService;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public NotificationController(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(@RequestBody GeneralNotificationRequest req) {
        String email = trim(req.email());
        String phone = trim(req.phone());
        String nusStudentId = trim(req.nusStudentId());
        String channel = req.channel();

        if (email.isBlank() && phone.isBlank() && nusStudentId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide at least one target: email, phone or nusStudentId."));
        }

        if (nusStudentId != null && !nusStudentId.isBlank()) {
            User user = userRepository.findByNusStudentId(nusStudentId).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No user found for nusStudentId."));
            }
            if (email.isBlank()) {
                email = user.getEmail();
            }
            if (phone.isBlank()) {
                phone = user.getPhone();
            }
        }

        String subject = normalizeSubject(req.subject(), req.type());
        String message = trim(req.message());

        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required."));
        }

        try {
            notificationService.sendGeneral(email, phone, subject, message, channel);
            return ResponseEntity.ok(Map.of("message", "Notification sent."));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send notification."));
        }
    }

    private String normalizeSubject(String provided, String type) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }

        if (type == null || type.isBlank()) {
            return "PeerConnect Notification";
        }

        String t = type.trim().toLowerCase();
        return switch (t) {
            case "newsletter" -> "PeerConnect Newsletter";
            case "marketing" -> "PeerConnect Product Marketing";
            default -> "PeerConnect " + capitalize(t);
        };
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public record GeneralNotificationRequest(
        String email,
        String nusStudentId,
        String phone,
        String channel,
        String subject,
        String message,
        String type
    ) {}
}
