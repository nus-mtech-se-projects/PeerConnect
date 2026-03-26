package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.Profile;
import mtech.swe5006.peerconnect.data.sql.ProfileRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.service.AzureBlobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2 MB
    private static final List<String> ALLOWED_TYPES = List.of("image/png", "image/jpeg");

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AzureBlobService azureBlobService;

    public ProfileController(ProfileRepository profileRepository,
                             UserRepository userRepository,
                             AzureBlobService azureBlobService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.azureBlobService = azureBlobService;
    }

    @GetMapping
    public ResponseEntity<?> getProfile(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }
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
        result.put("fullTime", profile != null && "Y".equals(profile.getFullTimeInd()));
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(Authentication auth, @RequestBody Map<String, Object> body) {
        if (auth == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }
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
        if (body.containsKey("fullTime")) {
            Boolean ft = (Boolean) body.get("fullTime");
            profile.setFullTimeInd(Boolean.TRUE.equals(ft) ? "Y" : "N");
        }

        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    // ──────────────── Avatar Upload ────────────────

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(Authentication auth,
                                          @RequestParam("avatar") MultipartFile file) {
        if (auth == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }
        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PNG or JPG files are allowed"));
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must be smaller than 2 MB"));
        }

        // Resolve user
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        try {
            // Determine file extension from content type
            String ext = "image/png".equals(file.getContentType()) ? ".png" : ".jpg";
            String blobName = user.getId().toString() + ext;

            // Delete any old blob with the other extension (e.g. switching png↔jpg)
            String otherExt = ".png".equals(ext) ? ".jpg" : ".png";
            azureBlobService.delete(user.getId().toString() + otherExt);

            // Upload to Azure Blob Storage
            String avatarUrl = azureBlobService.upload(blobName, file);

            // Append cache-busting timestamp so browsers fetch the fresh image
            avatarUrl = avatarUrl + "?t=" + System.currentTimeMillis();

            // Upsert profile with the new avatar URL
            Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
            if (profile == null) {
                profile = new Profile();
                profile.setUserId(user.getId());
            }
            profile.setAvatarUrl(avatarUrl);
            profileRepository.save(profile);

            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<?> deleteAvatar(Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Authentication required"));
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        if (profile == null || profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) {
            return ResponseEntity.ok(Map.of("message", "No avatar to delete"));
        }

        try {
            // Delete both .png and .jpg variants to be safe
            String userId = user.getId().toString();
            azureBlobService.delete(userId + ".png");
            azureBlobService.delete(userId + ".jpg");

            // Clear avatar URL in database
            profile.setAvatarUrl(null);
            profileRepository.save(profile);

            return ResponseEntity.ok(Map.of("message", "Avatar deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Delete failed: " + e.getMessage()));
        }
    }
}
