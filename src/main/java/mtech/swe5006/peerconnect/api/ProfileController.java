package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.Profile;
import mtech.swe5006.peerconnect.data.sql.ProfileRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    public ProfileController(ProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getProfile(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);

        // Return combined user + profile data
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId().toString());
        result.put("email", user.getEmail());
        result.put("firstName", nullSafe(user.getFirstName()));
        result.put("lastName", nullSafe(user.getLastName()));
        result.put("phone", nullSafe(user.getPhone()));
        result.put("nusStudentId", nullSafe(user.getNusStudentId()));
        result.put("faculty", profile != null ? nullSafe(profile.getFaculty()) : "");
        result.put("major", profile != null ? nullSafe(profile.getMajor()) : "");
        result.put("yearOfStudy", profile != null && profile.getYearOfStudy() != null ? profile.getYearOfStudy() : 0);
        result.put("bio", profile != null ? nullSafe(profile.getBio()) : "");
        result.put("avatarUrl", profile != null ? nullSafe(profile.getAvatarUrl()) : "");
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(Authentication auth, @RequestBody Map<String, Object> body) {
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        // Update user fields if provided
        if (body.containsKey("firstName")) user.setFirstName((String) body.get("firstName"));
        if (body.containsKey("lastName")) user.setLastName((String) body.get("lastName"));
        if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
        userRepository.save(user);

        // Update or create profile
        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        if (profile == null) {
            profile = new Profile();
            profile.setUserId(user.getId());
        }

        if (body.containsKey("faculty")) profile.setFaculty((String) body.get("faculty"));
        if (body.containsKey("major")) profile.setMajor((String) body.get("major"));
        if (body.containsKey("yearOfStudy")) {
            Object yos = body.get("yearOfStudy");
            if (yos instanceof Number) profile.setYearOfStudy(((Number) yos).shortValue());
        }
        if (body.containsKey("bio")) profile.setBio((String) body.get("bio"));
        if (body.containsKey("avatarUrl")) profile.setAvatarUrl((String) body.get("avatarUrl"));

        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
