package mtech.swe5006.peerconnect;
import mtech.swe5006.peerconnect.security.JwtAuthFilter;
import mtech.swe5006.peerconnect.security.JwtService;
import mtech.swe5006.peerconnect.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private CustomUserDetailsService userDetailsService;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthFilter filter;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── No Authorization header ─────────────────────────────────────────

    @Test
    @DisplayName("Proceeds without auth when no Authorization header")
    void noHeader_continuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Proceeds without auth when header doesn't start with Bearer")
    void nonBearerHeader_continuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── Invalid token ───────────────────────────────────────────────────

    @Test
    @DisplayName("Proceeds without auth when token is invalid")
    void invalidToken_continuesChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token.here");
        when(jwtService.isValid("bad.token.here")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ── Valid token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Sets SecurityContext authentication for valid token")
    void validToken_setsAuth() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("alice@u.nus.edu");

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("alice@u.nus.edu")
                .password("hash")
                .authorities("ROLE_USER")
                .build();
        when(userDetailsService.loadUserByUsername("alice@u.nus.edu"))
                .thenReturn(userDetails);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice@u.nus.edu");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Does not overwrite existing authentication in SecurityContext")
    void validToken_doesNotOverwriteExistingAuth() throws Exception {
        // Pre-populate the security context
        var existingAuth = new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken("bob@u.nus.edu", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.isValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("alice@u.nus.edu");

        filter.doFilter(request, response, filterChain);

        // Should still be Bob, not Alice
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("bob@u.nus.edu");
    }
}