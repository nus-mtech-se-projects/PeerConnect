package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.data.sql.PasswordResetTokenRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.security.JwtService;
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

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        }

        @Test
        @DisplayName("400 when password is blank")
        void login_blankPassword() {
            var req = new AuthController.LoginRequest("alice@u.nus.edu", null, "  ");
            ResponseEntity<?> res = controller.login(req);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody().toString()).contains("password is required");
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
        // 256-bit minimum secret for HS256 test JWTs
        private static final byte[] TEST_SECRET = "super-secret-key-that-is-at-least-32-bytes!!".getBytes();

        /**
         * Helper: builds a signed JWT with the given claims.
         * The controller only parses (no signature verification), so HS256 + dummy
         * secret works.
         */
        private String buildIdToken(Map<String, Object> claims) throws Exception {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                    .audience("test-client-id")
                    .expirationTime(new Date(System.currentTimeMillis() + 300_000));

            claims.forEach(builder::claim);

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    builder.build());
            signedJWT.sign(new MACSigner(TEST_SECRET));
            return signedJWT.serialize();
        }

        // ── Happy-path tests ────────────────────────────────────────

        @Test
        @DisplayName("200 + token for existing user via preferred_username")
        void microsoftLogin_existingUser_returnsToken() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "preferred_username", "alice@u.nus.edu",
                    "name", "Alice Tan",
                    "oid", "ms-oid-123"));

            when(userRepository.findByEmail("alice@u.nus.edu"))
                    .thenReturn(Optional.of(savedUser));
            when(jwtService.generateAccessToken("alice@u.nus.edu"))
                    .thenReturn("jwt-ms-token");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(Map.of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("accessToken", "jwt-ms-token");
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            assertThat(body).containsEntry("expiresInSeconds", 900L);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("200 + creates new user when email not found")
        void microsoftLogin_newUser_createsAndReturnsToken() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "preferred_username", "bob@example.com",
                    "name", "Bob Jones",
                    "oid", "ms-oid-456"));

            when(userRepository.findByEmail("bob@example.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken("bob@example.com"))
                    .thenReturn("jwt-new-bob");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(Map.of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User created = captor.getValue();
            assertThat(created.getEmail()).isEqualTo("bob@example.com");
            assertThat(created.getFirstName()).isEqualTo("Bob");
            assertThat(created.getLastName()).isEqualTo("Jones");
            assertThat(created.getPasswordHash()).isEmpty();
            assertThat(created.getNusStudentId()).isEqualTo("MS-ms-oid-456");
            assertThat(created.getUserType()).isEqualTo("student");
            assertThat(created.getStatus()).isEqualTo("active");
        }

        @Test
        @DisplayName("Falls back to email claim when preferred_username absent")
        void microsoftLogin_fallbackToEmailClaim() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "email", "carol@example.com",
                    "name", "Carol",
                    "oid", "ms-oid-789"
            ));

            User carolUser = new User();
            carolUser.setEmail("carol@example.com");
            carolUser.setFirstName("Carol");
            carolUser.setLastName("");
            carolUser.setUserType("student");
            carolUser.setStatus("active");

            when(userRepository.findByEmail("carol@example.com"))
                    .thenReturn(Optional.of(carolUser));
            when(jwtService.generateAccessToken("carol@example.com"))
                    .thenReturn("jwt-carol");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            ResponseEntity<?> res = controller.microsoftLogin(Map.of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            // Key assertion: the controller resolved "carol@example.com" from the
            // "email" claim (not "preferred_username") and used it for lookup + token
            verify(userRepository).findByEmail("carol@example.com");
            verify(jwtService).generateAccessToken("carol@example.com");
        }

        @Test
        @DisplayName("Single-word name sets lastName to empty")
        void microsoftLogin_singleName_emptyLastName() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "preferred_username", "dan@example.com",
                    "name", "Dan",
                    "oid", "ms-oid-999"));

            when(userRepository.findByEmail("dan@example.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken("dan@example.com")).thenReturn("jwt-dan");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            controller.microsoftLogin(Map.of("idToken", idToken));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getFirstName()).isEqualTo("Dan");
            assertThat(captor.getValue().getLastName()).isEmpty();
        }

        @Test
        @DisplayName("Null name sets firstName and lastName to empty")
        void microsoftLogin_nullName_emptyNames() throws Exception {
            String idToken = buildIdToken(Map.of(
                    "preferred_username", "noname@example.com",
                    "oid", "ms-oid-000"));

            when(userRepository.findByEmail("noname@example.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken("noname@example.com")).thenReturn("jwt-nn");
            when(jwtService.expiresInSeconds()).thenReturn(900L);

            controller.microsoftLogin(Map.of("idToken", idToken));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getFirstName()).isEmpty();
            assertThat(captor.getValue().getLastName()).isEmpty();
        }

        // ── Validation / error tests ────────────────────────────────

        @Test
        @DisplayName("400 when idToken key is missing")
        void microsoftLogin_missingIdToken() {
            ResponseEntity<?> res = controller.microsoftLogin(Map.of());

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "idToken required");
        }

        @Test
        @DisplayName("400 when idToken is blank")
        void microsoftLogin_blankIdToken() {
            ResponseEntity<?> res = controller.microsoftLogin(Map.of("idToken", "   "));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "idToken required");
        }

        @Test
        @DisplayName("400 when token has no email claims")
        void microsoftLogin_noEmailInToken() throws Exception {
            String idToken = buildIdToken(Map.of("oid", "ms-oid-no-email"));

            ResponseEntity<?> res = controller.microsoftLogin(Map.of("idToken", idToken));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "No email in token");
        }

        @Test
        @DisplayName("400 when token is malformed")
        void microsoftLogin_malformedToken() {
            ResponseEntity<?> res = controller.microsoftLogin(
                    Map.of("idToken", "not.a.valid.jwt"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("error", "Microsoft login failed. Please try again.");
        }
    }
}
