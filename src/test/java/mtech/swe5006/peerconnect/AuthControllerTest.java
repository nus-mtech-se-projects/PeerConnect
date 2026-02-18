package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import mtech.swe5006.peerconnect.security.JwtService;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthController controller;

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
    //  POST /api/auth/register
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
    //  POST /api/auth/login
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
}