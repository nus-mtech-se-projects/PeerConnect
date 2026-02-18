package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  
  public AuthController(UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
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

    return ResponseEntity.ok(Map.of(
        "id", user.getId().toString(),
        "email", user.getEmail()));
  }

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

    String token = jwtService.generateAccessToken(user.getEmail());
    return ResponseEntity.ok(Map.of(
        "id", user.getId().toString(),
        "email", user.getEmail(),
        "nusStudentId", user.getNusStudentId(),
        "accessToken", token,
        "expiresInSeconds", jwtService.expiresInSeconds()));
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
