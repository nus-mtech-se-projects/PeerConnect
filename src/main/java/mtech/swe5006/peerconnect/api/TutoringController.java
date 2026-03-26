package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.PeerFeedback;
import mtech.swe5006.peerconnect.data.sql.PeerFeedbackRepository;
import mtech.swe5006.peerconnect.data.sql.TutoringClass;
import mtech.swe5006.peerconnect.data.sql.TutoringClassRepository;
import mtech.swe5006.peerconnect.data.sql.TutoringEnrollment;
import mtech.swe5006.peerconnect.data.sql.TutoringEnrollmentRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TutoringController.class);

    private final TutoringClassRepository tutoringClassRepository;
    private final TutoringEnrollmentRepository tutoringEnrollmentRepository;
    private final UserRepository userRepository;
    private final PeerFeedbackRepository peerFeedbackRepository;

    public TutoringController(TutoringClassRepository tutoringClassRepository,
                              TutoringEnrollmentRepository tutoringEnrollmentRepository,
                              UserRepository userRepository,
                              PeerFeedbackRepository peerFeedbackRepository) {
        this.tutoringClassRepository = tutoringClassRepository;
        this.tutoringEnrollmentRepository = tutoringEnrollmentRepository;
        this.userRepository = userRepository;
        this.peerFeedbackRepository = peerFeedbackRepository;
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

        peerFeedbackRepository.deleteByPeerTutorGroupId(id);
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

    @PostMapping("/classes/{id}/feedback")
    public ResponseEntity<?> submitFeedback(@PathVariable UUID id, Authentication auth, @RequestBody Map<String, Object> body) {
        User reviewer = getCurrentUser(auth);
        if (reviewer == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        TutoringClass tutoringClass = tutoringClassRepository.findById(id).orElse(null);
        if (tutoringClass == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tutoring class not found"));
        }

        TutoringEnrollment enrollment = tutoringEnrollmentRepository
            .findByClassIdAndUserId(id, reviewer.getId())
            .orElse(null);

        if (enrollment == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Only enrolled students can submit feedback"));
        }

        UUID revieweeId;
        try {
            Object reqReviewee = body.get("revieweeId");
            if (reqReviewee == null || String.valueOf(reqReviewee).isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "revieweeId is required"));
            }
            revieweeId = UUID.fromString(String.valueOf(reqReviewee));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid revieweeId format"));
        }

        if (reviewer.getId().equals(revieweeId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot submit feedback for yourself"));
        }

        User reviewee = userRepository.findById(revieweeId).orElse(null);
        if (reviewee == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Reviewee not found"));
        }

        if (peerFeedbackRepository.existsBySessionIdAndReviewerIdAndRevieweeId(id, reviewer.getId(), revieweeId)) {
            return ResponseEntity.status(409).body(Map.of("error", "Feedback has already been submitted for this peer and session"));
        }

        Short overallRating = asShort(body.get("overallRating"), null);
        Short preparedness = asShort(body.get("preparedness"), null);
        Short communication = asShort(body.get("communication"), null);
        Short helpfulness = asShort(body.get("helpfulness"), null);
        Short reliability = asShort(body.get("reliability"), null);

        Short[] ratings = {overallRating, preparedness, communication, helpfulness, reliability};
        for (Short rating : ratings) {
            if (rating == null || rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(Map.of("error", "All ratings (overall, preparedness, communication, helpfulness, reliability) are required and must be between 1 and 5"));
            }
        }

        PeerFeedback feedback = new PeerFeedback();
        feedback.setPeerTutorGroupId(id);
        feedback.setSessionId(id);
        feedback.setReviewerId(reviewer.getId());
        feedback.setRevieweeId(revieweeId);
        feedback.setOverallRating(overallRating);
        feedback.setPreparedness(preparedness);
        feedback.setCommunication(communication);
        feedback.setHelpfulness(helpfulness);
        feedback.setReliability(reliability);
        feedback.setStrengths(asString(body.get("strengths")));
        feedback.setImprovements(asString(body.get("improvements")));
        feedback.setAnonymousToPeer(Boolean.parseBoolean(String.valueOf(body.get("anonymousToPeer"))));

        peerFeedbackRepository.save(feedback);
        return ResponseEntity.ok(buildFeedbackResponse(id, id, revieweeId, reviewee, feedback));
    }

    @GetMapping("/classes/{id}/feedback")
    public ResponseEntity<?> getClassFeedback(@PathVariable UUID id, Authentication auth) {
        User currentUser = getCurrentUser(auth);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        TutoringClass tutoringClass = tutoringClassRepository.findById(id).orElse(null);
        if (tutoringClass == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Tutoring class not found"));
        }

        if (!currentUser.getId().equals(tutoringClass.getCreatedBy())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the tutor can view submitted feedback"));
        }

        List<Map<String, Object>> payload = peerFeedbackRepository.findByPeerTutorGroupIdOrderByCreatedAtDesc(id)
            .stream()
            .map(feedback -> {
                User reviewer = userRepository.findById(feedback.getReviewerId()).orElse(null);
                User reviewee = userRepository.findById(feedback.getRevieweeId()).orElse(null);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", feedback.getId() != null ? feedback.getId().toString() : null);
                row.put("peerTutorGroupId", feedback.getPeerTutorGroupId() != null ? feedback.getPeerTutorGroupId().toString() : null);
                row.put("sessionId", feedback.getSessionId() != null ? feedback.getSessionId().toString() : null);
                row.put("revieweeId", feedback.getRevieweeId() != null ? feedback.getRevieweeId().toString() : null);
                row.put("revieweeName", displayName(reviewee));
                row.put("reviewerName", displayName(reviewer));
                row.put("reviewerEmail", reviewer != null ? reviewer.getEmail() : null);
                row.put("overallRating", feedback.getOverallRating());
                row.put("preparedness", feedback.getPreparedness());
                row.put("communication", feedback.getCommunication());
                row.put("helpfulness", feedback.getHelpfulness());
                row.put("reliability", feedback.getReliability());
                row.put("strengths", feedback.getStrengths());
                row.put("improvements", feedback.getImprovements());
                row.put("anonymousToPeer", Boolean.TRUE.equals(feedback.getAnonymousToPeer()));
                row.put("submittedAt", feedback.getCreatedAt());
                return row;
            })
            .toList();

        return ResponseEntity.ok(payload);
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
        row.put("tutorId", tutor == null ? null : tutor.getId().toString());
        row.put("tutorEmail", tutor == null ? null : tutor.getEmail());
        return row;
    }

    private Map<String, Object> buildFeedbackResponse(UUID groupId, UUID sessionId, UUID revieweeId, User reviewee, PeerFeedback feedback) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", feedback.getId() != null ? feedback.getId().toString() : null);
        response.put("peerTutorGroupId", groupId.toString());
        response.put("sessionId", sessionId.toString());
        response.put("revieweeId", revieweeId.toString());
        String revieweeName = (firstNonBlank(reviewee.getFirstName(), "") + " " + firstNonBlank(reviewee.getLastName(), "")).trim();
        response.put("revieweeName", revieweeName.isEmpty() ? reviewee.getEmail() : revieweeName);
        response.put("anonymousToPeer", Boolean.TRUE.equals(feedback.getAnonymousToPeer()));
        return response;
    }

    private String displayName(User user) {
        if (user == null) return null;
        String fullName = (firstNonBlank(user.getFirstName(), "") + " " + firstNonBlank(user.getLastName(), "")).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("[TutoringController] Unhandled exception", ex);
        return ResponseEntity.status(500).body(Map.of(
            "error", ex.getMessage() != null ? ex.getMessage() : "Internal server error"
        ));
    }
}
