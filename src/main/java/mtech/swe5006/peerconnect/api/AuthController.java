package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.data.sql.PasswordResetToken;
import mtech.swe5006.peerconnect.data.sql.PasswordResetTokenRepository;
import mtech.swe5006.peerconnect.security.JwtService;
import mtech.swe5006.peerconnect.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final PasswordResetTokenRepository resetTokenRepository;
  private final EmailService emailService;
  
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Rate-limit: track last email-send time per user email. */
  private static final ConcurrentHashMap<String, LocalDateTime> emailCooldowns = new ConcurrentHashMap<>();

  @Value("${app.email.cooldown.seconds:120}")
  private long EMAIL_COOLDOWN_SECONDS;

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

    invalidateUnusedTokens(user);

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
            if (resolvedOid != null) u.setNusStudentId("MS-" + resolvedOid);
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
        log.error("[MicrosoftLogin] Failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.status(400).body(Map.of("error", "Microsoft login failed. Please try again."));
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
    if ((req.email() == null || req.email().isBlank())
        && (req.nusStudentId() == null || req.nusStudentId().isBlank())) {
      return ResponseEntity.badRequest().body("Provide email or NUS Student ID.");
    }

    User user = resolveUser(req.email(), req.nusStudentId());
    if (user == null) {
      return ResponseEntity.badRequest().body(
          "Account does not exist. Please verify the email or NUS Student ID.");
    }

    // Rate-limit: max 1 email per 2 minutes per user
    LocalDateTime lastSent = emailCooldowns.get(user.getEmail());
    if (lastSent != null && lastSent.plusSeconds(EMAIL_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
      return ResponseEntity.status(429).body(Map.of("error", "Please wait before requesting another code."));
    }

    String code = generateAndStoreVerificationCode(user);
    try {
      emailService.sendResetCode(user.getEmail(), code);
      emailCooldowns.put(user.getEmail(), LocalDateTime.now());
    } catch (Exception e) {
      log.error("[ForgotPassword] Failed to send reset code to {}: {}", user.getEmail(), e.getMessage());
    }

    return ResponseEntity.ok(Map.of("message", "If the account exists, a code has been sent."));
  }

  // ── Reset Password: verify code & update password ────────────────────────

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
    if ((req.email() == null || req.email().isBlank())
        && (req.nusStudentId() == null || req.nusStudentId().isBlank())) {
      return ResponseEntity.badRequest().body("Provide email or NUS Student ID.");
    }

    User user = resolveUser(req.email(), req.nusStudentId());
    if (user == null) {
      return ResponseEntity.badRequest().body("Invalid request.");
    }

    return verifyCodeAndUpdatePassword(user, req.code(), req.newPassword(),
        "Password reset successfully.");
  }

  public record ForgotPasswordRequest(String email, String nusStudentId) {}
  public record ResetPasswordRequest(String email, String nusStudentId, String code, String newPassword) {}

  // ── Change Password (authenticated): request code ────────────────────────

  @PostMapping("/change-password/request")
  public ResponseEntity<?> changePasswordRequest(
      @RequestHeader(value = "Authorization", required = false) String authHeader) {

    String email = extractEmailFromAuth(authHeader);
    if (email == null) {
      return ResponseEntity.status(401).body("Authentication required.");
    }

    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
      return ResponseEntity.badRequest().body("User not found.");
    }

    // Rate-limit: max 1 email per 2 minutes per user
    LocalDateTime lastSent = emailCooldowns.get(user.getEmail());
    if (lastSent != null && lastSent.plusSeconds(EMAIL_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
      return ResponseEntity.status(429).body(Map.of("error", "Please wait before requesting another code."));
    }

    String code = generateAndStoreVerificationCode(user);
    try {
      emailService.sendChangePasswordCode(user.getEmail(), code);
      emailCooldowns.put(user.getEmail(), LocalDateTime.now());
    } catch (Exception e) {
      log.error("[ChangePasswordRequest] Failed to send code to {}: {}", user.getEmail(), e.getMessage());
    }

    return ResponseEntity.ok(Map.of("message", "Verification code sent to your email."));
  }

  // ── Change Password (authenticated): confirm with code ───────────────────

  @PostMapping("/change-password/confirm")
  public ResponseEntity<?> changePasswordConfirm(
      @RequestBody ChangePasswordRequest req,
      @RequestHeader(value = "Authorization", required = false) String authHeader) {

    String email = extractEmailFromAuth(authHeader);
    if (email == null) {
      return ResponseEntity.status(401).body("Authentication required.");
    }

    User user = userRepository.findByEmail(email).orElse(null);
    if (user == null) {
      return ResponseEntity.badRequest().body("User not found.");
    }

    return verifyCodeAndUpdatePassword(user, req.code(), req.newPassword(),
        "Password changed successfully.");
  }

  // ── Private helpers (eliminate duplication) ────────────────────────────────

  /** Resolve a user by email (preferred) or NUS Student ID. */
  private User resolveUser(String email, String nusStudentId) {
    if (email != null && !email.isBlank())
      return userRepository.findByEmail(email.trim()).orElse(null);
    if (nusStudentId != null && !nusStudentId.isBlank())
      return userRepository.findByNusStudentId(nusStudentId.trim()).orElse(null);
    return null;
  }

  /** Generate a 6-digit code, persist it as a PasswordResetToken, and return the code. */
  private String generateAndStoreVerificationCode(User user) {
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    PasswordResetToken prt = new PasswordResetToken();
    prt.setUserId(user.getId());
    prt.setToken(code);
    prt.setExpiry(LocalDateTime.now().plusMinutes(15));
    resetTokenRepository.save(prt);
    return code;
  }

  /** Mark every unused reset-token for the given user as used. */
  private void invalidateUnusedTokens(User user) {
    var unusedTokens = resetTokenRepository.findByUserIdAndUsedAtIsNull(user.getId());
    if (!unusedTokens.isEmpty()) {
      LocalDateTime now = LocalDateTime.now();
      unusedTokens.forEach(t -> t.setUsedAt(now));
      resetTokenRepository.saveAll(unusedTokens);
    }
  }

  /** Validate code & password, verify the token, invalidate all tokens, and update the password. */
  private ResponseEntity<?> verifyCodeAndUpdatePassword(
      User user, String code, String newPassword, String successMessage) {
    if (code == null || code.isBlank()) {
      return ResponseEntity.badRequest().body("Verification code is required.");
    }
    if (newPassword == null || newPassword.length() < 6) {
      return ResponseEntity.badRequest().body("Password must be at least 6 characters.");
    }
    var tokenOpt = resetTokenRepository
        .findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
            user.getId(), code.trim(), LocalDateTime.now());
    if (tokenOpt.isEmpty()) {
      return ResponseEntity.badRequest().body("Invalid or expired code.");
    }
    invalidateUnusedTokens(user);
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    return ResponseEntity.ok(Map.of("message", successMessage));
  }

  /** Extract email from JWT Authorization header. */
  private String extractEmailFromAuth(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authHeader.substring(7);
    if (!jwtService.isValid(token)) {
      return null;
    }
    return jwtService.extractUsername(token);
  }

  public record ChangePasswordRequest(String code, String newPassword) {}

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleException(Exception ex) {
    log.error("[AuthController] Unhandled exception", ex);
    return ResponseEntity.status(500).body(Map.of(
        "error", ex.getMessage() != null ? ex.getMessage() : "Internal server error"));
  }
}
