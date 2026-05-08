package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.data.sql.PasswordResetTokenRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.security.JwtService;
import mtech.swe5006.peerconnect.service.AuditService;
import mtech.swe5006.peerconnect.service.EmailService;
import mtech.swe5006.peerconnect.api.AuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import mtech.swe5006.peerconnect.data.sql.PasswordResetToken;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.lang.reflect.Field;
import java.util.Date;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordResetTokenRepository resetTokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthController controller;

    // ── Reusable test data ──────────────────────────────────────────────

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("alice@u.nus.edu");
        savedUser.setNusStudentId("A1234567X");
        savedUser.setFirstName("Alice");
        savedUser.setLastName("Tan");
        savedUser.setPhone("91234567");
        savedUser.setPasswordHash("hashed-pw");
        savedUser.setUserType("student");
        savedUser.setStatus("active");

        clearEmailCooldowns();
    }

    @SuppressWarnings("unchecked")
    private void clearEmailCooldowns() {
        try {
            Field field = AuthController.class.getDeclaredField("emailCooldowns");
            field.setAccessible(true);
            ((Map<String, LocalDateTime>) field.get(null)).clear();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to clear AuthController email cooldowns for tests", ex);
        }
    }

    private void setEmailCooldownSeconds(long seconds) {
        try {
            Field field = AuthController.class.getDeclaredField("EMAIL_COOLDOWN_SECONDS");
            field.setAccessible(true);
            field.setLong(controller, seconds);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to set AuthController email cooldown for tests", ex);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/auth/register
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        private AuthController.RegisterRequest validReq() {
            return new AuthController.RegisterRequest(
                    "A1234567X", "Alice", "Tan",
                    "alice@u.nus.edu", "91234567", "P@ssw0rd");
        }

        @Test
        @DisplayName("201-equivalent OK when all fields valid and unique")
        void register_success() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNusStudentId(anyString())).thenReturn(false);
            when(passwordEncoder.encode("P@ssw0rd")).thenReturn("hashed-pw");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            ResponseEntity<?> res = controller.register(validReq());

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) res.getBody();
            assertThat(body).containsKey("id");
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            verify(auditService).record(
                    eq("USER_REGISTERED"),
                    any(),
                    eq("alice@u.nus.edu"),
                    eq("USER"),
                    any(),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details -> "local".equals(details.get("authProvider"))));
        }

        @Test
        @DisplayName("Saved user has hashed password, not plaintext")
        void register_hashesPassword() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNusStudentId(anyString())).thenReturn(false);
            when(passwordEncoder.encode("P@ssw0rd")).thenReturn("bcrypt$hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            controller.register(validReq());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt$hash");
        }

        @Test
        @DisplayName("Sets default userType=student and status=active")
        void register_setsDefaults() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNusStudentId(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            controller.register(validReq());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getUserType()).isEqualTo("student");
            assertThat(captor.getValue().getStatus()).isEqualTo("active");
        }

        @Test
        @DisplayName("409 when email already registered")
        void register_duplicateEmail() {
            when(userRepository.existsByEmail("alice@u.nus.edu")).thenReturn(true);

            ResponseEntity<?> res = controller.register(validReq());

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(res.getBody().toString()).contains("Email already registered");
            verify(userRepository, never()).save(any());
            verify(auditService).record(
                    eq("REGISTER_REJECTED"),
                    isNull(),
                    eq("alice@u.nus.edu"),
                    eq("USER"),
                    isNull(),
                    eq("FAILURE"),
                    isNull(),
                    isNull(),
                    argThat(details -> "duplicate_email".equals(details.get("reason"))));
        }

        @Test
        @DisplayName("409 when NUS Student ID already registered")
        void register_duplicateNusId() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByNusStudentId("A1234567X")).thenReturn(true);

            ResponseEntity<?> res = controller.register(validReq());

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(res.getBody().toString()).contains("NUS Student ID already registered");
            verify(userRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/auth/login
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("200 + token when logging in with email")
        void login_withEmail_success() {
            when(userRepository.findByEmail("alice@u.nus.edu"))
                    .thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("P@ssw0rd", "hashed-pw")).thenReturn(true);
            when(jwtService.generateAccessToken("alice@u.nus.edu")).thenReturn("jwt-token");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            var req = new AuthController.LoginRequest("alice@u.nus.edu", null, "P@ssw0rd");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("accessToken", "jwt-token");
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            assertThat(body).containsEntry("nusStudentId", "A1234567X");
            assertThat(body).containsEntry("expiresInSeconds", 900L);
            verify(auditService).record(
                    eq("LOGIN_SUCCEEDED"),
                    eq(savedUser.getId()),
                    eq("alice@u.nus.edu"),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details ->
                            "email".equals(details.get("loginMethod"))
                                    && "local".equals(details.get("authProvider"))));
        }

        @Test
        @DisplayName("200 + token when logging in with NUS Student ID")
        void login_withNusId_success() {
            when(userRepository.findByNusStudentId("A1234567X"))
                    .thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("P@ssw0rd", "hashed-pw")).thenReturn(true);
            when(jwtService.generateAccessToken("alice@u.nus.edu")).thenReturn("jwt-token");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            var req = new AuthController.LoginRequest(null, "A1234567X", "P@ssw0rd");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("accessToken", "jwt-token");
        }

        @Test
        @DisplayName("401 when email not found")
        void login_emailNotFound() {
            when(userRepository.findByEmail("unknown@u.nus.edu"))
                    .thenReturn(Optional.empty());

            var req = new AuthController.LoginRequest("unknown@u.nus.edu", null, "P@ssw0rd");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(res.getBody().toString()).contains("Invalid credentials");
        }

        @Test
        @DisplayName("401 when NUS Student ID not found")
        void login_nusIdNotFound() {
            when(userRepository.findByNusStudentId("X9999999Z"))
                    .thenReturn(Optional.empty());

            var req = new AuthController.LoginRequest(null, "X9999999Z", "P@ssw0rd");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("401 when password is wrong")
        void login_wrongPassword() {
            when(userRepository.findByEmail("alice@u.nus.edu"))
                    .thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

            var req = new AuthController.LoginRequest("alice@u.nus.edu", null, "wrong");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(jwtService, never()).generateAccessToken(anyString());
            verify(auditService).record(
                    eq("LOGIN_FAILED"),
                    eq(savedUser.getId()),
                    eq("alice@u.nus.edu"),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("FAILURE"),
                    isNull(),
                    isNull(),
                    argThat(details -> "email".equals(details.get("loginMethod"))));
        }

        @Test
        @DisplayName("400 when password is blank")
        void login_blankPassword() {
            var req = new AuthController.LoginRequest("alice@u.nus.edu", null, "  ");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody().toString()).contains("password is required");
            verify(auditService).record(
                    eq("LOGIN_REJECTED"),
                    isNull(),
                    eq("alice@u.nus.edu"),
                    eq("USER"),
                    isNull(),
                    eq("FAILURE"),
                    isNull(),
                    isNull(),
                    argThat(details ->
                            "missing_password".equals(details.get("reason"))
                                    && "email".equals(details.get("loginMethod"))));
        }

        @Test
        @DisplayName("400 when password is null")
        void login_nullPassword() {
            var req = new AuthController.LoginRequest("alice@u.nus.edu", null, null);
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("400 when neither email nor NUS ID is provided")
        void login_noIdentifier() {
            var req = new AuthController.LoginRequest(null, null, "P@ssw0rd");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody().toString()).contains("Provide exactly one");
        }

        @Test
        @DisplayName("400 when both identifiers are blank strings")
        void login_bothBlank() {
            var req = new AuthController.LoginRequest("", "", "P@ssw0rd");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
    // ════════════════════════════════════════════════════════════════════
    // POST /api/auth/microsoft
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/microsoft")
    class MicrosoftLogin {

        // ── clientPrincipal flow (Azure Static Web Apps) ────────────

        @Test
        @DisplayName("200 + token for existing user via clientPrincipal")
        void clientPrincipal_existingUser_returnsToken() {
            Map<String, Object> cp = Map.of(
                    "identityProvider", "aad",
                    "userId", "swa-uid-123",
                    "userDetails", "alice@u.nus.edu",
                    "userRoles", java.util.List.of("authenticated", "anonymous"));

            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(savedUser));
            when(jwtService.generateAccessToken("alice@u.nus.edu")).thenReturn("jwt-swa-token");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("clientPrincipal", cp));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("accessToken", "jwt-swa-token");
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            assertThat(body).containsEntry("expiresInSeconds", 900L);

            verify(userRepository, never()).save(any());
            verify(auditService).record(
                    eq("LOGIN_SUCCEEDED"),
                    eq(savedUser.getId()),
                    eq("alice@u.nus.edu"),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details -> "microsoft".equals(details.get("authProvider"))));
        }

        @Test
        @DisplayName("200 + creates new user when email not found via clientPrincipal")
        void clientPrincipal_newUser_createsAndReturnsToken() {
            Map<String, Object> cp = Map.of(
                    "identityProvider", "aad",
                    "userId", "swa-uid-456",
                    "userDetails", "bob@example.com",
                    "userRoles", java.util.List.of("authenticated"));

            when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken("bob@example.com")).thenReturn("jwt-new-bob");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("clientPrincipal", cp));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User created = captor.getValue();
            assertThat(created.getEmail()).isEqualTo("bob@example.com");
            assertThat(created.getPasswordHash()).isEmpty();
            assertThat(created.getNusStudentId()).isEqualTo("MS-swa-uid-456");
            assertThat(created.getUserType()).isEqualTo("student");
            assertThat(created.getStatus()).isEqualTo("active");
        }

        @Test
        @DisplayName("400 when clientPrincipal has no userDetails")
        void clientPrincipal_missingUserDetails_returns400() {
            Map<String, Object> cp = Map.of("identityProvider", "aad", "userId", "swa-uid-789");

            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("clientPrincipal", cp));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "No email in clientPrincipal");
            verify(auditService).record(
                    eq("LOGIN_REJECTED"),
                    isNull(),
                    isNull(),
                    eq("USER"),
                    isNull(),
                    eq("FAILURE"),
                    isNull(),
                    isNull(),
                    argThat(details ->
                            "microsoft".equals(details.get("authProvider"))
                                    && "missing_email_in_clientPrincipal".equals(details.get("reason"))));
        }

        // ── Legacy idToken flow (backward compatibility) ────────────

        @Test
        @DisplayName("200 + token for existing user via idToken preferred_username")
        void idToken_existingUser_returnsToken() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "preferred_username", "alice@u.nus.edu",
                    "name", "Alice Tan",
                    "oid", "ms-oid-123"));

            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(savedUser));
            when(jwtService.generateAccessToken("alice@u.nus.edu")).thenReturn("jwt-ms-token");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("accessToken", "jwt-ms-token");
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("200 + creates new user via idToken, sets name from claims")
        void idToken_newUser_createsAndReturnsToken() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "preferred_username", "bob@example.com",
                    "name", "Bob Jones",
                    "oid", "ms-oid-456"));

            when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken("bob@example.com")).thenReturn("jwt-new-bob");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            controller.microsoftLogin(Map.<String, Object>of("idToken", idToken));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getFirstName()).isEqualTo("Bob");
            assertThat(captor.getValue().getLastName()).isEqualTo("Jones");
            assertThat(captor.getValue().getNusStudentId()).isEqualTo("MS-ms-oid-456");
        }

        @Test
        @DisplayName("Falls back to email claim when preferred_username absent")
        void idToken_fallbackToEmailClaim() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "email", "carol@example.com",
                    "name", "Carol",
                    "oid", "ms-oid-789"));

            User carolUser = new User();
            carolUser.setEmail("carol@example.com");
            carolUser.setFirstName("Carol");
            carolUser.setLastName("");
            carolUser.setUserType("student");
            carolUser.setStatus("active");

            when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(carolUser));
            when(jwtService.generateAccessToken("carol@example.com")).thenReturn("jwt-carol");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userRepository).findByEmail("carol@example.com");
        }

        // ── Validation / error tests ────────────────────────────────

        @Test
        @DisplayName("400 when body has neither clientPrincipal nor idToken")
        void microsoftLogin_emptyBody_returns400() {
            ResponseEntity<?> res = controller.microsoftLogin(Map.<String, Object>of());

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "idToken required");
            verify(auditService).record(
                    eq("LOGIN_REJECTED"),
                    isNull(),
                    isNull(),
                    eq("USER"),
                    isNull(),
                    eq("FAILURE"),
                    isNull(),
                    isNull(),
                    argThat(details ->
                            "microsoft".equals(details.get("authProvider"))
                                    && "missing_id_token".equals(details.get("reason"))));
        }

        @Test
        @DisplayName("400 when idToken is blank")
        void idToken_blank_returns400() {
            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("idToken", "   "));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "idToken required");
        }

        @Test
        @DisplayName("400 when idToken has no email claims")
        void idToken_noEmailClaims_returns400() throws Exception {
            String idToken = buildIdToken(Map.of("oid", "ms-oid-no-email"));

            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "No email in token");
        }

        @Test
        @DisplayName("400 when idToken is malformed")
        void idToken_malformed_returns400() {
            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.<String, Object>of("idToken", "not.a.valid.jwt"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "Microsoft login failed. Please try again.");
            verify(auditService).record(
                    eq("LOGIN_FAILED"),
                    isNull(),
                    isNull(),
                    eq("USER"),
                    isNull(),
                    eq("FAILURE"),
                    isNull(),
                    isNull(),
                    argThat(details ->
                            "microsoft".equals(details.get("authProvider"))
                                    && details.get("reason") != null));
        }

        // ── Helper ──────────────────────────────────────────────────

        private static final byte[] TEST_SECRET =
                "super-secret-key-that-is-at-least-32-bytes!!".getBytes();

        private String buildIdToken(Map<String, Object> claims) throws Exception {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                    .audience("test-client-id")
                    .expirationTime(new Date(System.currentTimeMillis() + 300_000));
            claims.forEach(builder::claim);
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256), builder.build());
            signedJWT.sign(new MACSigner(TEST_SECRET));
            return signedJWT.serialize();
        }
    }

    @Nested
    @DisplayName("Password reset and change audit events")
    class PasswordFlows {

        @Test
        void forgotPassword_recordsAuditEvent() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(savedUser));

            ResponseEntity<?> res = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest("alice@u.nus.edu", null));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auditService).record(
                    eq("PASSWORD_RESET_REQUESTED"),
                    eq(savedUser.getId()),
                    eq(savedUser.getEmail()),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details -> "forgot_password".equals(details.get("flow"))));
        }

        @Test
        void forgotPassword_missingIdentifierReturns400() {
            ResponseEntity<?> res = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest(" ", null));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).isEqualTo("Provide email or NUS Student ID.");
            verify(resetTokenRepository, never()).save(any());
            verify(emailService, never()).sendResetCode(anyString(), anyString());
        }

        @Test
        void forgotPassword_unknownUserReturns400() {
            when(userRepository.findByNusStudentId("A9999999Z")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest(null, "A9999999Z"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).isEqualTo("Account does not exist. Please verify the email or NUS Student ID.");
            verify(resetTokenRepository, never()).save(any());
        }

        @Test
        void forgotPassword_secondRequestWithinCooldownReturns429() {
            setEmailCooldownSeconds(120);
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));

            ResponseEntity<?> first = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest(savedUser.getEmail(), null));
            ResponseEntity<?> second = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest(savedUser.getEmail(), null));

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            verify(emailService, times(1)).sendResetCode(eq(savedUser.getEmail()), anyString());
        }

        @Test
        void forgotPassword_returnsOkWhenEmailSendFails() {
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            doThrow(new RuntimeException("smtp down")).when(emailService).sendResetCode(eq(savedUser.getEmail()), anyString());

            ResponseEntity<?> res = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest(savedUser.getEmail(), null));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auditService).record(
                    eq("PASSWORD_RESET_REQUESTED"),
                    eq(savedUser.getId()),
                    eq(savedUser.getEmail()),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details -> "forgot_password".equals(details.get("flow"))));
        }

        @Test
        void resetPassword_recordsAuditEvent() {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(savedUser.getId());
            token.setToken("123456");

            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(savedUser));
            when(resetTokenRepository.findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
                    eq(savedUser.getId()), eq("123456"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(token));
            when(resetTokenRepository.findByUserIdAndUsedAtIsNull(savedUser.getId())).thenReturn(java.util.List.of());
            when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

            ResponseEntity<?> res = controller.resetPassword(
                    new AuthController.ResetPasswordRequest("alice@u.nus.edu", null, "123456", "new-pass"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auditService).record(
                    eq("PASSWORD_RESET_COMPLETED"),
                    eq(savedUser.getId()),
                    eq(savedUser.getEmail()),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    eq(Map.of()));
        }

        @Test
        void resetPassword_invalidInputsReturn400() {
            ResponseEntity<?> missingIdentifier = controller.resetPassword(
                    new AuthController.ResetPasswordRequest(null, "", "123456", "new-pass"));
            assertThat(missingIdentifier.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(missingIdentifier.getBody()).isEqualTo("Provide email or NUS Student ID.");

            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            ResponseEntity<?> missingCode = controller.resetPassword(
                    new AuthController.ResetPasswordRequest(savedUser.getEmail(), null, " ", "new-pass"));
            assertThat(missingCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(missingCode.getBody()).isEqualTo("Verification code is required.");

            ResponseEntity<?> shortPassword = controller.resetPassword(
                    new AuthController.ResetPasswordRequest(savedUser.getEmail(), null, "123456", "12345"));
            assertThat(shortPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(shortPassword.getBody()).isEqualTo("Password must be at least 6 characters.");
        }

        @Test
        void resetPassword_unknownUserOrInvalidTokenReturns400() {
            when(userRepository.findByNusStudentId(savedUser.getNusStudentId())).thenReturn(Optional.empty());
            ResponseEntity<?> unknownUser = controller.resetPassword(
                    new AuthController.ResetPasswordRequest(null, savedUser.getNusStudentId(), "123456", "new-pass"));
            assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(unknownUser.getBody()).isEqualTo("Invalid request.");

            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            when(resetTokenRepository.findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
                    eq(savedUser.getId()), eq("000000"), any(LocalDateTime.class)))
                    .thenReturn(Optional.empty());

            ResponseEntity<?> invalidToken = controller.resetPassword(
                    new AuthController.ResetPasswordRequest(savedUser.getEmail(), null, "000000", "new-pass"));
            assertThat(invalidToken.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(invalidToken.getBody()).isEqualTo("Invalid or expired code.");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void resetPassword_marksUnusedTokensAsUsed() {
            PasswordResetToken submittedToken = new PasswordResetToken();
            submittedToken.setUserId(savedUser.getId());
            submittedToken.setToken("123456");
            PasswordResetToken otherToken = new PasswordResetToken();
            otherToken.setUserId(savedUser.getId());
            otherToken.setToken("222222");

            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            when(resetTokenRepository.findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
                    eq(savedUser.getId()), eq("123456"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(submittedToken));
            when(resetTokenRepository.findByUserIdAndUsedAtIsNull(savedUser.getId()))
                    .thenReturn(java.util.List.of(submittedToken, otherToken));
            when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

            ResponseEntity<?> res = controller.resetPassword(
                    new AuthController.ResetPasswordRequest(savedUser.getEmail(), null, "123456", "new-pass"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(submittedToken.getUsedAt()).isNotNull();
            assertThat(otherToken.getUsedAt()).isNotNull();
            verify(resetTokenRepository).saveAll(java.util.List.of(submittedToken, otherToken));
        }

        @Test
        void changePasswordRequest_recordsAuditEvent() {
            when(jwtService.isValid("token")).thenReturn(true);
            when(jwtService.extractUsername("token")).thenReturn(savedUser.getEmail());
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));

            ResponseEntity<?> res = controller.changePasswordRequest("Bearer token");

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auditService).record(
                    eq("PASSWORD_CHANGE_REQUESTED"),
                    eq(savedUser.getId()),
                    eq(savedUser.getEmail()),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details -> "change_password".equals(details.get("flow"))));
        }

        @Test
        void changePasswordRequest_requiresValidAuthAndExistingUser() {
            ResponseEntity<?> missingAuth = controller.changePasswordRequest(null);
            assertThat(missingAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(missingAuth.getBody()).isEqualTo("Authentication required.");

            ResponseEntity<?> malformedAuth = controller.changePasswordRequest("Token abc");
            assertThat(malformedAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            when(jwtService.isValid("bad")).thenReturn(false);
            ResponseEntity<?> invalidJwt = controller.changePasswordRequest("Bearer bad");
            assertThat(invalidJwt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            when(jwtService.isValid("valid")).thenReturn(true);
            when(jwtService.extractUsername("valid")).thenReturn("missing@u.nus.edu");
            when(userRepository.findByEmail("missing@u.nus.edu")).thenReturn(Optional.empty());
            ResponseEntity<?> missingUser = controller.changePasswordRequest("Bearer valid");
            assertThat(missingUser.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(missingUser.getBody()).isEqualTo("User not found.");
        }

        @Test
        void changePasswordRequest_secondRequestWithinCooldownReturns429() {
            setEmailCooldownSeconds(120);
            when(jwtService.isValid("token")).thenReturn(true);
            when(jwtService.extractUsername("token")).thenReturn(savedUser.getEmail());
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));

            ResponseEntity<?> first = controller.changePasswordRequest("Bearer token");
            ResponseEntity<?> second = controller.changePasswordRequest("Bearer token");

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            verify(emailService, times(1)).sendChangePasswordCode(eq(savedUser.getEmail()), anyString());
        }

        @Test
        void changePasswordRequest_returnsOkWhenEmailSendFails() {
            when(jwtService.isValid("token")).thenReturn(true);
            when(jwtService.extractUsername("token")).thenReturn(savedUser.getEmail());
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            doThrow(new RuntimeException("smtp down")).when(emailService).sendChangePasswordCode(eq(savedUser.getEmail()), anyString());

            ResponseEntity<?> res = controller.changePasswordRequest("Bearer token");

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auditService).record(
                    eq("PASSWORD_CHANGE_REQUESTED"),
                    eq(savedUser.getId()),
                    eq(savedUser.getEmail()),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    argThat(details -> "change_password".equals(details.get("flow"))));
        }

        @Test
        void changePasswordRequest_isNotBlockedByForgotPasswordCooldown() {
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            when(jwtService.isValid("token")).thenReturn(true);
            when(jwtService.extractUsername("token")).thenReturn(savedUser.getEmail());

            ResponseEntity<?> forgotRes = controller.forgotPassword(
                    new AuthController.ForgotPasswordRequest(savedUser.getEmail(), null));
            ResponseEntity<?> changeRes = controller.changePasswordRequest("Bearer token");

            assertThat(forgotRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(changeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void changePasswordConfirm_recordsAuditEvent() {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(savedUser.getId());
            token.setToken("654321");

            when(jwtService.isValid("token")).thenReturn(true);
            when(jwtService.extractUsername("token")).thenReturn(savedUser.getEmail());
            when(userRepository.findByEmail(savedUser.getEmail())).thenReturn(Optional.of(savedUser));
            when(resetTokenRepository.findByUserIdAndTokenAndUsedAtIsNullAndExpiryAfter(
                    eq(savedUser.getId()), eq("654321"), any(LocalDateTime.class)))
                    .thenReturn(Optional.of(token));
            when(resetTokenRepository.findByUserIdAndUsedAtIsNull(savedUser.getId())).thenReturn(java.util.List.of());
            when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

            ResponseEntity<?> res = controller.changePasswordConfirm(
                    new AuthController.ChangePasswordRequest("654321", "new-pass"),
                    "Bearer token");

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(auditService).record(
                    eq("PASSWORD_CHANGE_COMPLETED"),
                    eq(savedUser.getId()),
                    eq(savedUser.getEmail()),
                    eq("USER"),
                    eq(savedUser.getId()),
                    eq("SUCCESS"),
                    isNull(),
                    isNull(),
                    eq(Map.of()));
        }

        @Test
        void changePasswordConfirm_requiresValidAuthAndExistingUser() {
            ResponseEntity<?> missingAuth = controller.changePasswordConfirm(
                    new AuthController.ChangePasswordRequest("123456", "new-pass"), null);
            assertThat(missingAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            when(jwtService.isValid("bad")).thenReturn(false);
            ResponseEntity<?> invalidJwt = controller.changePasswordConfirm(
                    new AuthController.ChangePasswordRequest("123456", "new-pass"), "Bearer bad");
            assertThat(invalidJwt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            when(jwtService.isValid("valid")).thenReturn(true);
            when(jwtService.extractUsername("valid")).thenReturn("missing@u.nus.edu");
            when(userRepository.findByEmail("missing@u.nus.edu")).thenReturn(Optional.empty());
            ResponseEntity<?> missingUser = controller.changePasswordConfirm(
                    new AuthController.ChangePasswordRequest("123456", "new-pass"), "Bearer valid");
            assertThat(missingUser.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(missingUser.getBody()).isEqualTo("User not found.");
        }
    }
}
