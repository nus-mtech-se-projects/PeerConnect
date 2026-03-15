package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.UserController;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserController controller;

    private User user;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@u.nus.edu");
        user.setFirstName("Alice");
        user.setLastName("Tan");
        user.setNusStudentId("A0123456X");
        user.setPhone("91234567");
        user.setUserType("student");
        user.setStatus("active");
    }

    private Authentication authFor(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        return auth;
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetCurrentUser {

        @Test
        void success_returnsAllFields() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(user));

            ResponseEntity<?> res = controller.getCurrentUser(authFor("alice@u.nus.edu"));

            assertThat(res.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("id", user.getId().toString());
            assertThat(body).containsEntry("email", "alice@u.nus.edu");
            assertThat(body).containsEntry("firstName", "Alice");
            assertThat(body).containsEntry("lastName", "Tan");
            assertThat(body).containsEntry("nusStudentId", "A0123456X");
            assertThat(body).containsEntry("phone", "91234567");
            assertThat(body).containsEntry("userType", "student");
            assertThat(body).containsEntry("status", "active");
        }

        @Test
        void nullOptionalFields_returnEmptyStrings() {
            user.setFirstName(null);
            user.setLastName(null);
            user.setNusStudentId(null);
            user.setPhone(null);
            user.setUserType(null);
            user.setStatus(null);
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(user));

            ResponseEntity<?> res = controller.getCurrentUser(authFor("alice@u.nus.edu"));

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body).containsEntry("firstName", "");
            assertThat(body).containsEntry("lastName", "");
            assertThat(body).containsEntry("nusStudentId", "");
            assertThat(body).containsEntry("phone", "");
            assertThat(body).containsEntry("userType", "");
            assertThat(body).containsEntry("status", "");
        }

        @Test
        void userNotFound_returns404() {
            when(userRepository.findByEmail("ghost@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getCurrentUser(authFor("ghost@u.nus.edu"));

            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }
    }
}
