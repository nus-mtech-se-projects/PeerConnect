package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.security.CustomUserDetailsService;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private CustomUserDetailsService service;

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("bcrypt$hash");
        user.setNusStudentId("A1234567X");
        user.setFirstName("Alice");
        user.setLastName("Tan");
        user.setUserType("student");
        user.setStatus("active");
        return user;
    }

    @Test
    @DisplayName("Returns UserDetails with email as username")
    void loadUserByUsername_success() {
        User user = buildUser("alice@u.nus.edu");
        when(userRepository.findByEmail("alice@u.nus.edu"))
                .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice@u.nus.edu");

        assertThat(details.getUsername()).isEqualTo("alice@u.nus.edu");
    }

    @Test
    @DisplayName("Returns UserDetails with the hashed password from DB")
    void loadUserByUsername_hasCorrectPassword() {
        User user = buildUser("alice@u.nus.edu");
        when(userRepository.findByEmail("alice@u.nus.edu"))
                .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice@u.nus.edu");

        assertThat(details.getPassword()).isEqualTo("bcrypt$hash");
    }

    @Test
    @DisplayName("Returned UserDetails has ROLE_USER authority")
    void loadUserByUsername_hasRoleUser() {
        User user = buildUser("alice@u.nus.edu");
        when(userRepository.findByEmail("alice@u.nus.edu"))
                .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice@u.nus.edu");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Throws UsernameNotFoundException when email not found")
    void loadUserByUsername_notFound() {
        when(userRepository.findByEmail("ghost@u.nus.edu"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost@u.nus.edu"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost@u.nus.edu");
    }
}