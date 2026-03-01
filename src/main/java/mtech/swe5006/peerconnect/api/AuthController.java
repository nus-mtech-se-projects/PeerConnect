package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.data.sql.PasswordResetToken;
import mtech.swe5006.peerconnect.data.sql.PasswordResetTokenRepository;
import mtech.swe5006.peerconnect.security.JwtService;
import mtech.swe5006.peerconnect.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final PasswordResetTokenRepository resetTokenRepository;
  private final EmailService emailService;
  
  private static final SecureRandom RANDOM = new SecureRandom();

  public AuthController(UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      PasswordResetTokenRepository resetTokenRepository,
      EmailService emailService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.resetTokenRepository = resetTokenRepository;
    this.emailService = emailService;
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

    // Invalidate all unused password-reset tokens for this user
    var unusedTokens = resetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
    if (!unusedTokens.isEmpty()) {
      LocalDateTime now = LocalDateTime.now();
      unusedTokens.forEach(t -> t.setUsedAt(now));
      resetTokenRepository.saveAll(unusedTokens);
    }

    String token = jwtService.generateAccessToken(user.getEmail());
    return ResponseEntity.ok(Map.of(
        "id", user.getId().toString(),
        "email", user.getEmail(),
        "nusStudentId", user.getNusStudentId(),
        "accessToken", token,
        "expiresInSeconds", jwtService.expiresInSeconds()));
  }
@PostMapping("/microsoft")
public ResponseEntity<?> microsoftLogin(@RequestBody Map<String, String> body) {
    String idToken = body.get("idToken");
    if (idToken == null || idToken.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("error", "idToken required"));
    }

    try {
        com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(idToken);
        var claims = jwt.getJWTClaimsSet();

        String email = claims.getStringClaim("preferred_username");
        if (email == null) email = claims.getStringClaim("email");
        String name = claims.getStringClaim("name");
        String oid  = claims.getStringClaim("oid"); // unique MS user ID

        if (email == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No email in token"));
        }

        final String resolvedEmail = email;
        final String resolvedOid = oid;
        final String resolvedName = name;

        User user = userRepository.findByEmail(resolvedEmail).orElseGet(() -> {
            User u = new User();
            u.setEmail(resolvedEmail);
            u.setPasswordHash("");           // no password for OAuth users
            u.setNusStudentId("MS-" + resolvedOid); // unique placeholder
            u.setFirstName(resolvedName != null ? resolvedName.split(" ")[0] : "");
            u.setLastName(resolvedName != null && resolvedName.contains(" ")
                ? resolvedName.split(" ", 2)[1] : "");
            u.setUserType("student");
            u.setStatus("active");
            return userRepository.save(u);
        });

        String token = jwtService.generateAccessToken(user.getEmail());
        return ResponseEntity.ok(Map.of(
            "accessToken", token,
            "email", user.getEmail(),
            "expiresInSeconds", jwtService.expiresInSeconds()
        ));

    } catch (Exception e) {
        return ResponseEntity.status(400).body(Map.of("error", "Invalid ID token: " + e.getMessage()));
    }
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

  // ── Forgot Password: generate code & email it ────────────────────────────

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest req) {
    String email = req.email();
    String nusStudentId = req.nusStudentId();

    boolean hasEmail = email != null && !email.isBlank();
    boolean hasNusId = nusStudentId != null && !nusStudentId.isBlank();

    if (!hasEmail && !hasNusId) {
      return ResponseEntity.badRequest().body("Provide email or NUS Student ID.");
    }

    User user = hasEmail
        ? userRepository.findByEmail(email.trim()).orElse(null)
        : userRepository.findByNusStudentId(nusStudentId.trim()).orElse(null);

    if (user == null) {
      return ResponseEntity.badRequest().body(
          "Account does not exist. Please verify the email or NUS Student ID.");
    }

    // Generate a 6-digit code
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));

    // Store token
    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(user.getId());
    token.setToken(code);
    token.setExpiry(LocalDateTime.now().plusMinutes(15));
    resetTokenRepository.save(token);

    // Send email
    emailService.sendResetCode(user.getEmail(), code);

    return ResponseEntity.ok(Map.of("message", "If the account exists, a code has been sent."));
  }

  // ── Reset Password: verify code & update password ────────────────────────

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
    String email = req.email();
    String nusStudentId = req.nusStudentId();
    String code = req.code();
    String newPassword = req.newPassword();

    if (code == null || code.isBlank()) {
      return ResponseEntity.badRequest().body("Verification code is required.");
    }
    if (newPassword == null || newPassword.length() < 6) {
      return ResponseEntity.badRequest().body("Password must be at least 6 characters.");
    }

    boolean hasEmail = email != null && !email.isBlank();
    boolean hasNusId = nusStudentId != null && !nusStudentId.isBlank();

    if (!hasEmail && !hasNusId) {
      return ResponseEntity.badRequest().body("Provide email or NUS Student ID.");
    }

    // Resolve user
    User user = hasEmail
        ? userRepository.findByEmail(email.trim()).orElse(null)
        : userRepository.findByNusStudentId(nusStudentId.trim()).orElse(null);

    if (user == null) {
      return ResponseEntity.badRequest().body("Invalid request.");
    }

    // Find valid token
    var tokenOpt = resetTokenRepository
        .findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
            user.getId(), code.trim(), LocalDateTime.now());

    if (tokenOpt.isEmpty()) {
      return ResponseEntity.badRequest().body("Invalid or expired code.");
    }

    // Mark ALL unused tokens for this user as used
    var allUnused = resetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
    LocalDateTime now = LocalDateTime.now();
    allUnused.forEach(t -> t.setUsedAt(now));
    resetTokenRepository.saveAll(allUnused);

    // Update password
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
  }

  public record ForgotPasswordRequest(String email, String nusStudentId) {}
  public record ResetPasswordRequest(String email, String nusStudentId, String code, String newPassword) {}
}
