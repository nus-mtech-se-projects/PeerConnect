package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.RestrictedUser;
import mtech.swe5006.peerconnect.data.sql.RestrictedUserRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/restricted-users")
public class RestrictedUserController {

    private static final Logger log = LoggerFactory.getLogger(RestrictedUserController.class);

    private final RestrictedUserRepository restrictedUserRepository;
    private final UserRepository userRepository;

    public RestrictedUserController(RestrictedUserRepository restrictedUserRepository,
                                     UserRepository userRepository) {
        this.restrictedUserRepository = restrictedUserRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/restricted-users
     * Returns the list of users restricted by the current user (owner).
     */
    @GetMapping
    public ResponseEntity<?> getRestrictedUsers(Authentication auth) {
        User owner = getCurrentUser(auth);
        if (owner == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        List<RestrictedUser> restricted = restrictedUserRepository.findByBlockerId(owner.getId());
        List<Map<String, Object>> payload = new ArrayList<>();
        for (RestrictedUser r : restricted) {
            User u = userRepository.findById(r.getBlockedId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("restrictedUserId", r.getBlockedId());
            row.put("email", u != null ? u.getEmail() : null);
            row.put("firstName", u != null ? u.getFirstName() : null);
            row.put("lastName", u != null ? u.getLastName() : null);
            row.put("createdAt", r.getCreatedAt());
            payload.add(row);
        }
        return ResponseEntity.ok(payload);
    }

    /**
     * POST /api/restricted-users
     * Restrict a user by their userId.
     * Body: { "userId": "..." }
     */
    @PostMapping
    public ResponseEntity<?> restrictUser(Authentication auth, @RequestBody Map<String, Object> body) {
        User owner = getCurrentUser(auth);
        if (owner == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        String userIdStr = body.get("userId") != null ? body.get("userId").toString() : null;
        if (userIdStr == null || userIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid userId format"));
        }

        if (owner.getId().equals(targetId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot restrict yourself"));
        }

        User target = userRepository.findById(targetId).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Target user not found"));
        }

        if (restrictedUserRepository.existsByBlockerIdAndBlockedId(owner.getId(), targetId)) {
            return ResponseEntity.ok(Map.of("restricted", true, "alreadyRestricted", true));
        }

        RestrictedUser entry = new RestrictedUser();
        entry.setBlockerId(owner.getId());
        entry.setBlockedId(targetId);
        restrictedUserRepository.save(entry);

        return ResponseEntity.ok(Map.of("restricted", true));
    }

    /**
     * DELETE /api/restricted-users/{userId}
     * Allow (un-restrict) a previously restricted user.
     */
    @DeleteMapping("/{userId}")
    @Transactional
    public ResponseEntity<?> allowUser(@PathVariable UUID userId, Authentication auth) {
        User owner = getCurrentUser(auth);
        if (owner == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        if (!restrictedUserRepository.existsByBlockerIdAndBlockedId(owner.getId(), userId)) {
            return ResponseEntity.ok(Map.of("allowed", true, "wasNotRestricted", true));
        }

        restrictedUserRepository.deleteByBlockerIdAndBlockedId(owner.getId(), userId);
        return ResponseEntity.ok(Map.of("allowed", true));
    }

    /**
     * GET /api/restricted-users/search?q=...
     * Search active users by email or name. Returns max 20 results.
     * Excludes the current user and marks if each result is already restricted.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam String q, Authentication auth) {
        User owner = getCurrentUser(auth);
        if (owner == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        List<User> results = userRepository.searchByEmailOrName(q.trim());
        List<Map<String, Object>> payload = new ArrayList<>();
        int count = 0;
        for (User u : results) {
            if (u.getId().equals(owner.getId())) continue;
            if (count >= 20) break;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("email", u.getEmail());
            row.put("firstName", u.getFirstName());
            row.put("lastName", u.getLastName());
            row.put("restricted", restrictedUserRepository.existsByBlockerIdAndBlockedId(owner.getId(), u.getId()));
            payload.add(row);
            count++;
        }
        return ResponseEntity.ok(payload);
    }

    private User getCurrentUser(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }
}
