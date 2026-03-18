package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.TutoringClass;
import mtech.swe5006.peerconnect.data.sql.TutoringClassRepository;
import mtech.swe5006.peerconnect.data.sql.TutoringEnrollment;
import mtech.swe5006.peerconnect.data.sql.TutoringEnrollmentRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tutoring")
public class TutoringController {

    private final TutoringClassRepository tutoringClassRepository;
    private final TutoringEnrollmentRepository tutoringEnrollmentRepository;
    private final UserRepository userRepository;

    public TutoringController(TutoringClassRepository tutoringClassRepository,
                              TutoringEnrollmentRepository tutoringEnrollmentRepository,
                              UserRepository userRepository) {
        this.tutoringClassRepository = tutoringClassRepository;
        this.tutoringEnrollmentRepository = tutoringEnrollmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/classes")
    public ResponseEntity<?> getAllClasses(Authentication auth) {
        User currentUser = getCurrentUser(auth);
        if (currentUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        List<Map<String, Object>> payload = tutoringClassRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(c -> buildClassPayload(c, currentUser))
            .toList();

        return ResponseEntity.ok(payload);
    }

    @PostMapping("/classes")
    public ResponseEntity<?> createClass(Authentication auth, @RequestBody Map<String, Object> body) {
        User currentUser = getCurrentUser(auth);
        if (currentUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        String title = asString(body.get("title"));
        String moduleCode = asString(body.get("moduleCode"));
        String topic = asString(body.get("topic"));
        String description = asString(body.get("description"));
        String schedule = asString(body.get("schedule"));
        String mode = firstNonBlank(asString(body.get("mode")), "online").toLowerCase();
        String location = asString(body.get("location"));
        String meetingLink = asString(body.get("meetingLink"));
        Short maxStudents = asShort(body.get("maxStudents"), (short) 5);

        String validationError = validateClassInput(title, moduleCode, schedule, mode, location, meetingLink, maxStudents);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        TutoringClass tutoringClass = new TutoringClass();
        tutoringClass.setTitle(title);
        tutoringClass.setModuleCode(moduleCode);
        tutoringClass.setTopic(topic);
        tutoringClass.setDescription(description);
        tutoringClass.setSchedule(schedule);
        tutoringClass.setMode(mode);
        tutoringClass.setLocation(location);
        tutoringClass.setMeetingLink(meetingLink);
        tutoringClass.setMaxStudents(maxStudents);
        tutoringClass.setCreatedBy(currentUser.getId());

        tutoringClassRepository.save(tutoringClass);
        return ResponseEntity.ok(buildClassPayload(tutoringClass, currentUser));
    }

    @DeleteMapping("/classes/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable UUID id, Authentication auth) {
        User currentUser = getCurrentUser(auth);
        if (currentUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        TutoringClass tutoringClass = tutoringClassRepository.findById(id).orElse(null);
        if (tutoringClass == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tutoring class not found"));
        }
        if (!currentUser.getId().equals(tutoringClass.getCreatedBy())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to delete this tutoring class"));
        }

        tutoringEnrollmentRepository.deleteByClassId(id);
        tutoringClassRepository.delete(tutoringClass);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @PostMapping("/classes/{id}/enroll")
    public ResponseEntity<?> enroll(@PathVariable UUID id, Authentication auth) {
        User currentUser = getCurrentUser(auth);
        if (currentUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        TutoringClass tutoringClass = tutoringClassRepository.findById(id).orElse(null);
        if (tutoringClass == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tutoring class not found"));
        }
        if (currentUser.getId().equals(tutoringClass.getCreatedBy())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tutor cannot enroll in their own class"));
        }

        TutoringEnrollment existing = tutoringEnrollmentRepository.findByClassIdAndUserId(id, currentUser.getId()).orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(Map.of("alreadyEnrolled", true));
        }

        long enrolledCount = tutoringEnrollmentRepository.countByClassId(id);
        if (tutoringClass.getMaxStudents() != null && enrolledCount >= tutoringClass.getMaxStudents()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tutoring class is full"));
        }

        TutoringEnrollment enrollment = new TutoringEnrollment();
        enrollment.setClassId(id);
        enrollment.setUserId(currentUser.getId());
        tutoringEnrollmentRepository.save(enrollment);

        return ResponseEntity.ok(Map.of(
            "enrolled", true,
            "enrolledCount", enrolledCount + 1
        ));
    }

    @PostMapping("/classes/{id}/leave")
    public ResponseEntity<?> leave(@PathVariable UUID id, Authentication auth) {
        User currentUser = getCurrentUser(auth);
        if (currentUser == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        TutoringClass tutoringClass = tutoringClassRepository.findById(id).orElse(null);
        if (tutoringClass == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tutoring class not found"));
        }
        if (currentUser.getId().equals(tutoringClass.getCreatedBy())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tutor cannot leave their own class"));
        }

        TutoringEnrollment existing = tutoringEnrollmentRepository.findByClassIdAndUserId(id, currentUser.getId()).orElse(null);
        if (existing == null) {
            return ResponseEntity.ok(Map.of("alreadyLeft", true));
        }

        long currentCount = tutoringEnrollmentRepository.countByClassId(id);
        tutoringEnrollmentRepository.deleteByClassIdAndUserId(id, currentUser.getId());
        long remaining = Math.max(0, currentCount - 1);
        return ResponseEntity.ok(Map.of(
            "enrolled", false,
            "enrolledCount", remaining
        ));
    }

    private User getCurrentUser(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private Map<String, Object> buildClassPayload(TutoringClass tutoringClass, User currentUser) {
        User tutor = userRepository.findById(tutoringClass.getCreatedBy()).orElse(null);
        long enrolledCount = tutoringEnrollmentRepository.countByClassId(tutoringClass.getId());
        boolean enrolled = currentUser != null
            && tutoringEnrollmentRepository.findByClassIdAndUserId(tutoringClass.getId(), currentUser.getId()).isPresent();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tutoringClass.getId());
        row.put("title", tutoringClass.getTitle());
        row.put("moduleCode", tutoringClass.getModuleCode());
        row.put("topic", tutoringClass.getTopic());
        row.put("description", tutoringClass.getDescription());
        row.put("schedule", tutoringClass.getSchedule());
        row.put("mode", tutoringClass.getMode());
        row.put("location", tutoringClass.getLocation());
        row.put("meetingLink", tutoringClass.getMeetingLink());
        row.put("maxStudents", tutoringClass.getMaxStudents());
        row.put("createdBy", tutoringClass.getCreatedBy());
        row.put("createdAt", tutoringClass.getCreatedAt());
        row.put("enrolledCount", enrolledCount);
        row.put("isTutor", currentUser != null && tutoringClass.getCreatedBy().equals(currentUser.getId()));
        row.put("enrolled", enrolled);
        row.put("tutorName", tutor == null ? null :
            ((firstNonBlank(tutor.getFirstName(), "") + " " + firstNonBlank(tutor.getLastName(), "")).trim()));
        return row;
    }

    private String validateClassInput(String title,
                                      String moduleCode,
                                      String schedule,
                                      String mode,
                                      String location,
                                      String meetingLink,
                                      Short maxStudents) {
        if (title == null || title.isBlank()) return "Class title is required";
        if (moduleCode == null || moduleCode.isBlank()) return "Module code is required";
        if (schedule == null || schedule.isBlank()) return "Schedule is required";
        if (!"online".equalsIgnoreCase(mode) && !"in-person".equalsIgnoreCase(mode) && !"hybrid".equalsIgnoreCase(mode)) {
            return "Mode must be one of: online, in-person, hybrid";
        }
        if (("in-person".equalsIgnoreCase(mode) || "hybrid".equalsIgnoreCase(mode))
            && (location == null || location.isBlank())) {
            return "Location is required for in-person/hybrid classes";
        }
        if (("online".equalsIgnoreCase(mode) || "hybrid".equalsIgnoreCase(mode))
            && (meetingLink == null || meetingLink.isBlank())) {
            return "Meeting link is required for online/hybrid classes";
        }
        if (maxStudents == null || maxStudents < 1) return "Max students must be at least 1";
        return null;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}