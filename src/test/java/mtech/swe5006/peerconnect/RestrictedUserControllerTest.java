package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.RestrictedUserController;
import mtech.swe5006.peerconnect.data.sql.RestrictedUser;
import mtech.swe5006.peerconnect.data.sql.RestrictedUserRepository;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestrictedUserControllerTest {

    @Mock
    RestrictedUserRepository restrictedUserRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    RestrictedUserController controller;

    private User alice;
    private User bob;

    @BeforeEach
    void setup() {
        alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail("alice@u.nus.edu");
        alice.setFirstName("Alice");
        alice.setLastName("Tan");

        bob = new User();
        bob.setId(UUID.randomUUID());
        bob.setEmail("bob@u.nus.edu");
        bob.setFirstName("Bob");
        bob.setLastName("Lee");
    }

    private Authentication authFor(User u) {
        Authentication a = mock(Authentication.class);
        when(a.getName()).thenReturn(u.getEmail());
        return a;
    }

    @Nested
    @DisplayName("GET /api/restricted-users")
    class GetRestrictedUsers {

        @Test
        @DisplayName("returns empty list when no users restricted")
        void emptyList() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(restrictedUserRepository.findByBlockerId(alice.getId())).thenReturn(Collections.emptyList());

            ResponseEntity<?> res = controller.getRestrictedUsers(authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat((List<?>) res.getBody()).isEmpty();
        }

        @Test
        @DisplayName("returns restricted user details")
        void withRestrictedUsers() {
            RestrictedUser entry = new RestrictedUser();
            entry.setBlockerId(alice.getId());
            entry.setBlockedId(bob.getId());
            entry.setCreatedAt(LocalDateTime.now());

            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(restrictedUserRepository.findByBlockerId(alice.getId())).thenReturn(List.of(entry));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));

            ResponseEntity<?> res = controller.getRestrictedUsers(authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) res.getBody();
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("email")).isEqualTo("bob@u.nus.edu");
            assertThat(body.get(0).get("firstName")).isEqualTo("Bob");
        }

        @Test
        @DisplayName("returns 404 when user not found")
        void userNotFound() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.empty());

            ResponseEntity<?> res = controller.getRestrictedUsers(authFor(alice));
            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /api/restricted-users")
    class RestrictUser {

        @Test
        @DisplayName("successfully restricts a user")
        void restrictSuccess() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(false);

            Map<String, Object> body = Map.of("userId", bob.getId().toString());
            ResponseEntity<?> res = controller.restrictUser(authFor(alice), body);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            ArgumentCaptor<RestrictedUser> cap = ArgumentCaptor.forClass(RestrictedUser.class);
            verify(restrictedUserRepository).save(cap.capture());
            assertThat(cap.getValue().getBlockerId()).isEqualTo(alice.getId());
            assertThat(cap.getValue().getBlockedId()).isEqualTo(bob.getId());
        }

        @Test
        @DisplayName("returns alreadyRestricted when duplicate")
        void alreadyRestricted() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(userRepository.findById(bob.getId())).thenReturn(Optional.of(bob));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(true);

            Map<String, Object> body = Map.of("userId", bob.getId().toString());
            ResponseEntity<?> res = controller.restrictUser(authFor(alice), body);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) res.getBody();
            assertThat(responseBody.get("alreadyRestricted")).isEqualTo(true);
            verify(restrictedUserRepository, never()).save(any());
        }

        @Test
        @DisplayName("cannot restrict yourself")
        void cannotRestrictSelf() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));

            Map<String, Object> body = Map.of("userId", alice.getId().toString());
            ResponseEntity<?> res = controller.restrictUser(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 400 when userId missing")
        void missingUserId() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));

            Map<String, Object> body = new HashMap<>();
            ResponseEntity<?> res = controller.restrictUser(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 400 when userId is invalid UUID")
        void invalidUuid() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));

            Map<String, Object> body = Map.of("userId", "not-a-uuid");
            ResponseEntity<?> res = controller.restrictUser(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 404 when target user not found")
        void targetNotFound() {
            UUID fakeId = UUID.randomUUID();
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

            Map<String, Object> body = Map.of("userId", fakeId.toString());
            ResponseEntity<?> res = controller.restrictUser(authFor(alice), body);
            assertThat(res.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("DELETE /api/restricted-users/{userId}")
    class AllowUser {

        @Test
        @DisplayName("successfully allows a restricted user")
        void allowSuccess() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(true);

            ResponseEntity<?> res = controller.allowUser(bob.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            verify(restrictedUserRepository).deleteByBlockerIdAndBlockedId(alice.getId(), bob.getId());
        }

        @Test
        @DisplayName("returns ok when user was not restricted")
        void wasNotRestricted() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(false);

            ResponseEntity<?> res = controller.allowUser(bob.getId(), authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) res.getBody();
            assertThat(body.get("wasNotRestricted")).isEqualTo(true);
            verify(restrictedUserRepository, never()).deleteByBlockerIdAndBlockedId(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/restricted-users/search")
    class SearchUsers {

        @Test
        @DisplayName("returns empty list when query too short")
        void queryTooShort() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));

            ResponseEntity<?> res = controller.searchUsers("a", authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat((List<?>) res.getBody()).isEmpty();
        }

        @Test
        @DisplayName("returns search results with restricted flag")
        void searchWithResults() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(userRepository.searchByEmailOrName("bob")).thenReturn(List.of(bob));
            when(restrictedUserRepository.existsByBlockerIdAndBlockedId(alice.getId(), bob.getId())).thenReturn(true);

            ResponseEntity<?> res = controller.searchUsers("bob", authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) res.getBody();
            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("email")).isEqualTo("bob@u.nus.edu");
            assertThat(body.get(0).get("restricted")).isEqualTo(true);
        }

        @Test
        @DisplayName("excludes current user from search results")
        void excludesSelf() {
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(userRepository.searchByEmailOrName("alice")).thenReturn(List.of(alice));

            ResponseEntity<?> res = controller.searchUsers("alice", authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat((List<?>) res.getBody()).isEmpty();
        }

        @Test
        @DisplayName("limits results to 20")
        void maxResults() {
            List<User> manyUsers = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                User u = new User();
                u.setId(UUID.randomUUID());
                u.setEmail("user" + i + "@u.nus.edu");
                u.setFirstName("User");
                u.setLastName(String.valueOf(i));
                manyUsers.add(u);
            }
            when(userRepository.findByEmail("alice@u.nus.edu")).thenReturn(Optional.of(alice));
            when(userRepository.searchByEmailOrName("user")).thenReturn(manyUsers);

            ResponseEntity<?> res = controller.searchUsers("user", authFor(alice));
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat((List<?>) res.getBody()).hasSize(20);
        }
    }
}
