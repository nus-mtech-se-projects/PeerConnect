package mtech.swe5006.peerconnect.api;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.data.sql.PasswordResetToken;
import mtech.swe5006.peerconnect.data.sql.PasswordResetTokenRepository;
import mtech.swe5006.peerconnect.security.JwtService;
import mtech.swe5006.peerconnect.service.AuditService;
import mtech.swe5006.peerconnect.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
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
  private final AuditService auditService;
  
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Rate-limit: track last email-send time per flow and user email. */
  private static final ConcurrentHashMap<String, LocalDateTime> emailCooldowns = new ConcurrentHashMap<>();

  @Value("${app.email.cooldown.seconds:120}")
  private long EMAIL_COOLDOWN_SECONDS;

  @Value("${azure.ad.tenant-id}")
  private String tenantId;

  @Value("${azure.ad.client-id}")
  private String clientId;

  private JWKSource<SecurityContext> jwkSource;

  @PostConstruct
  void initJwkSource() throws Exception {
    this.jwkSource = new RemoteJWKSet<>(
        new URL("https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys"));
  }

  public AuthController(UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      PasswordResetTokenRepository resetTokenRepository,
      EmailService emailService,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.resetTokenRepository = resetTokenRepository;
    this.emailService = emailService;
    this.auditService = auditService;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
    if (userRepository.existsByEmail(req.email())) {
      recordAuthEvent("REGISTER_REJECTED", null, req.email(), "FAILURE", Map.of("reason", "duplicate_email"));
      return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));
    }
    if (userRepository.existsByNusStudentId(req.nusStudentId())) {
      recordAuthEvent("REGISTER_REJECTED", null, req.email(), "FAILURE", Map.of("reason", "duplicate_nus_student_id"));
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
    recordAuthEvent("USER_REGISTERED", user, user.getEmail(), "SUCCESS", Map.of("authProvider", "local"));

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
    String loginMethod = hasEmail ? "email" : hasNusId ? "nusStudentId" : "unknown";

    if (password == null || password.isBlank()) {
      recordAuthEvent("LOGIN_REJECTED", null, email, "FAILURE", Map.of("reason", "missing_password", "loginMethod", loginMethod));
      return ResponseEntity.badRequest().body(Map.of("error", "password is required"));
    }

    if (hasEmail == hasNusId) { // both true or both false
      recordAuthEvent("LOGIN_REJECTED", null, email, "FAILURE", Map.of("reason", "invalid_identifier_selection", "loginMethod", loginMethod));
      return ResponseEntity.badRequest().body(Map.of(
          "error", "Provide exactly one of: email or nusStudentId"));
    }

    User user = hasEmail
        ? userRepository.findByEmail(email).orElse(null)
        : userRepository.findByNusStudentId(nusStudentId).orElse(null);

    if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      recordAuthEvent("LOGIN_FAILED", user, email, "FAILURE", Map.of("loginMethod", loginMethod));
      return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }

    invalidateUnusedTokens(user);
    recordAuthEvent("LOGIN_SUCCEEDED", user, user.getEmail(), "SUCCESS", Map.of("loginMethod", loginMethod, "authProvider", "local"));

    String token = jwtService.generateAccessToken(user.getEmail());
    return buildLoginSuccessResponse(user, token);
  }
@PostMapping("/microsoft")
@SuppressWarnings("unchecked")
public ResponseEntity<?> microsoftLogin(@RequestBody Map<String, Object> body) {
    // New flow: Azure Static Web Apps clientPrincipal format
    if (body != null && body.containsKey("clientPrincipal")) {
        try {
            Map<String, Object> cp = (Map<String, Object>) body.get("clientPrincipal");
            String email = cp != null ? (String) cp.get("userDetails") : null;
            String userId = cp != null ? (String) cp.get("userId") : null;

            if (email == null || email.isBlank()) {
                recordAuthEvent("LOGIN_REJECTED", null, null, "FAILURE", Map.of("authProvider", "microsoft", "reason", "missing_email_in_clientPrincipal"));
                return ResponseEntity.badRequest().body(Map.of("error", "No email in clientPrincipal"));
            }

            final String resolvedEmail = email.trim().toLowerCase();
            final String resolvedUserId = userId;

            User user = userRepository.findByEmail(resolvedEmail).orElseGet(() -> {
                User u = new User();
                u.setEmail(resolvedEmail);
                u.setPasswordHash("");
                if (resolvedUserId != null) u.setNusStudentId("MS-" + resolvedUserId);
                u.setFirstName("");
                u.setLastName("");
                u.setUserType("student");
                u.setStatus("active");
                return userRepository.save(u);
            });

            String token = jwtService.generateAccessToken(user.getEmail());
            recordAuthEvent("LOGIN_SUCCEEDED", user, user.getEmail(), "SUCCESS", Map.of("authProvider", "microsoft"));
            return buildLoginSuccessResponse(user, token);

        } catch (Exception e) {
            recordAuthEvent("LOGIN_FAILED", null, null, "FAILURE", Map.of("authProvider", "microsoft", "reason", e.getClass().getSimpleName()));
            log.error("[MicrosoftLogin] ClientPrincipal flow failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", "Microsoft login failed. Please try again."));
        }
    }

    // Legacy flow: raw access token JWT (sent under "token", "idToken", or "credential")
    String rawToken = firstNonBlank(
        body != null ? (String) body.get("idToken") : null,
        body != null ? (String) body.get("credential") : null,
        body != null ? (String) body.get("token") : null);
    if (rawToken == null || rawToken.isBlank()) {
        recordAuthEvent("LOGIN_REJECTED", null, null, "FAILURE", Map.of("authProvider", "microsoft", "reason", "missing_id_token"));
        return ResponseEntity.badRequest().body(Map.of("error", "idToken required"));
    }

    try {
        JWTClaimsSet claims = validateMicrosoftToken(rawToken);

        // oid is the stable immutable identifier; prefer it over sub
        String oid = firstNonBlank(claims.getStringClaim("oid"), claims.getStringClaim("sub"));

        String email = firstNonBlank(
            claims.getStringClaim("preferred_username"),
            claims.getStringClaim("email"),
            claims.getStringClaim("upn"));

        if (email == null) {
            recordAuthEvent("LOGIN_REJECTED", null, null, "FAILURE", Map.of("authProvider", "microsoft", "reason", "missing_email_claim"));
            return ResponseEntity.badRequest().body(Map.of("error", "No email in token"));
        }

        // Prefer given_name/family_name over splitting name (unreliable for multi-word names)
        String givenName  = claims.getStringClaim("given_name");
        String familyName = claims.getStringClaim("family_name");
        String name       = claims.getStringClaim("name");

        final String resolvedEmail  = email.trim().toLowerCase();
        final String resolvedOid    = oid;
        final String resolvedFirst  = givenName  != null ? givenName
            : (name != null ? name.split(" ")[0] : "");
        final String resolvedLast   = familyName != null ? familyName
            : (name != null && name.contains(" ") ? name.split(" ", 2)[1] : "");

        // OID-first lookup → email fallback with oid backfill → create new
        User user = null;
        if (resolvedOid != null) {
            user = userRepository.findByMicrosoftOid(resolvedOid).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByEmail(resolvedEmail).orElse(null);
            if (user != null && resolvedOid != null) {
                user.setMicrosoftOid(resolvedOid);
                userRepository.save(user);
            }
        }
        if (user == null) {
            User u = new User();
            u.setEmail(resolvedEmail);
            u.setPasswordHash("");
            u.setMicrosoftOid(resolvedOid);
            u.setFirstName(resolvedFirst);
            u.setLastName(resolvedLast);
            u.setUserType("student");
            u.setStatus("active");
            user = userRepository.save(u);
        }

        String token = jwtService.generateAccessToken(user.getEmail());
        recordAuthEvent("LOGIN_SUCCEEDED", user, user.getEmail(), "SUCCESS", Map.of("authProvider", "microsoft"));
        return buildLoginSuccessResponse(user, token);

    } catch (Exception e) {
        recordAuthEvent("LOGIN_FAILED", null, null, "FAILURE", Map.of("authProvider", "microsoft", "reason", e.getClass().getSimpleName()));
        log.error("[MicrosoftLogin] Token flow failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        return ResponseEntity.status(400).body(Map.of("error", "Microsoft login failed. Please try again."));
    }
}

  private JWTClaimsSet validateMicrosoftToken(String rawToken) throws Exception {
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    // Disable built-in verifier; we validate issuer, audience, and expiry manually below
    processor.setJWTClaimsSetVerifier((claimsSet, ctx) -> {});

    JWTClaimsSet claims = processor.process(rawToken, null);

    String expectedIssuer = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    if (!expectedIssuer.equals(claims.getIssuer())) {
      throw new SecurityException("Invalid issuer: " + claims.getIssuer());
    }

    List<String> aud = claims.getAudience();
    if (aud == null || (!aud.contains("api://" + clientId) && !aud.contains(clientId))) {
      throw new SecurityException("Invalid audience: " + aud);
    }

    Date exp = claims.getExpirationTime();
    if (exp == null || exp.before(new Date())) {
      throw new SecurityException("Token expired");
    }

    return claims;
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
    LocalDateTime lastSent = emailCooldowns.get(cooldownKey("forgot_password", user.getEmail()));
    if (lastSent != null && lastSent.plusSeconds(EMAIL_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
      return ResponseEntity.status(429).body(Map.of("error", "Please wait before requesting another code."));
    }

    String code = generateAndStoreVerificationCode(user);
    try {
      emailService.sendResetCode(user.getEmail(), code);
      emailCooldowns.put(cooldownKey("forgot_password", user.getEmail()), LocalDateTime.now());
    } catch (Exception e) {
      log.error("[ForgotPassword] Failed to send reset code to {}: {}", user.getEmail(), e.getMessage());
    }
    recordAuthEvent("PASSWORD_RESET_REQUESTED", user, user.getEmail(), "SUCCESS", Map.of("flow", "forgot_password"));

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
        "Password reset successfully.", "PASSWORD_RESET_COMPLETED");
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
    LocalDateTime lastSent = emailCooldowns.get(cooldownKey("change_password", user.getEmail()));
    if (lastSent != null && lastSent.plusSeconds(EMAIL_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
      return ResponseEntity.status(429).body(Map.of("error", "Please wait before requesting another code."));
    }

    String code = generateAndStoreVerificationCode(user);
    try {
      emailService.sendChangePasswordCode(user.getEmail(), code);
      emailCooldowns.put(cooldownKey("change_password", user.getEmail()), LocalDateTime.now());
    } catch (Exception e) {
      log.error("[ChangePasswordRequest] Failed to send code to {}: {}", user.getEmail(), e.getMessage());
    }
    recordAuthEvent("PASSWORD_CHANGE_REQUESTED", user, user.getEmail(), "SUCCESS", Map.of("flow", "change_password"));

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
        "Password changed successfully.", "PASSWORD_CHANGE_COMPLETED");
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

  private String cooldownKey(String flow, String email) {
    return flow + ":" + email;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private ResponseEntity<?> buildLoginSuccessResponse(User user, String token) {
    return ResponseEntity.ok(Map.of(
        "id", user.getId() != null ? user.getId().toString() : "",
        "email", user.getEmail() != null ? user.getEmail() : "",
        "nusStudentId", user.getNusStudentId() != null ? user.getNusStudentId() : "",
        "accessToken", token,
        "expiresInSeconds", jwtService.expiresInSeconds()));
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
      User user, String code, String newPassword, String successMessage, String auditEventType) {
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
    recordAuthEvent(auditEventType, user, user.getEmail(), "SUCCESS", Map.of());
    return ResponseEntity.ok(Map.of("message", successMessage));
  }

  private void recordAuthEvent(String eventType,
      User actor,
      String actorEmail,
      String outcome,
      Map<String, Object> details) {
    auditService.record(
        eventType,
        actor != null ? actor.getId() : null,
        actorEmail,
        "USER",
        actor != null ? actor.getId() : null,
        outcome,
        null,
        null,
        details);
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
