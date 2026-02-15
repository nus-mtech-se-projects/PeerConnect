package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.User;
import mtech.swe5006.peerconnect.data.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthController(UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
    if (userRepository.existsByEmail(req.email())) {
      return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
    }
    if (userRepository.existsByNusStudentId(req.nusStudentId())) {
      return ResponseEntity.status(409).body(Map.of("error", "NUS Student ID already registered"));
    }

    User user = new User();
    user.setEmail(req.email()); // UPDATED: was setUsername(...)
    user.setPasswordHash(passwordEncoder.encode(req.password())); // UPDATED: now exists
    user.setFirstName(req.firstName());
    user.setLastName(req.lastName());
    user.setNusStudentId(req.nusStudentId());
    user.setPhone(req.phone());
    user.setUserType("student");
    user.setStatus("active");

    userRepository.save(user);

    // UPDATED: getId() now exists and returns UUID
    return ResponseEntity.ok(Map.of(
        "id", user.getId().toString(),
        "email", user.getEmail()));
  }

  // If you already have JWT login code, keep it—just use
  // getEmail()/getPasswordHash().
  // This is a minimal stub:
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req) {

    String password = req.password();
    String email = req.email();
    String nusStudentId = req.nusStudentId();

    boolean hasEmail = email != null && !email.isBlank();
    boolean hasNusId = nusStudentId != null && !nusStudentId.isBlank();

    if (password == null || password.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "password is required"));
    }

    if (hasEmail == hasNusId) { // both true or both false
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Provide exactly one of: email or nusStudentId"));
    }

    User user = hasEmail
        ? userRepository.findByEmail(email).orElse(null)
        : userRepository.findByNusStudentId(nusStudentId).orElse(null);

    if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }

    return ResponseEntity.ok(Map.of(
        "id", user.getId().toString(),
        "email", user.getEmail(),
        "nusStudentId", user.getNusStudentId()));
  }

  // Simple DTOs (records) to compile immediately
  public record RegisterRequest(
      String nusStudentId,
      String firstName,
      String lastName,
      String email,
      String phone,
      String password) {
  }

  public record LoginRequest(
      String email,
      String nusStudentId,
      String password) {
  }
}
