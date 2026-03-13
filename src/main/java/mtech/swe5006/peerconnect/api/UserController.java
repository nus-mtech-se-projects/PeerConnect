package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        return ResponseEntity.ok(Map.of(
            "id", user.getId().toString(),
            "email", user.getEmail(),
            "firstName", user.getFirstName() != null ? user.getFirstName() : "",
            "lastName", user.getLastName() != null ? user.getLastName() : "",
            "nusStudentId", user.getNusStudentId() != null ? user.getNusStudentId() : "",
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "userType", user.getUserType() != null ? user.getUserType() : "",
            "status", user.getStatus() != null ? user.getStatus() : ""
        ));
    }
}
