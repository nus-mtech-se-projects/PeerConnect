package mtech.swe5006.peerconnect;
import mtech.swe5006.peerconnect.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // 256-bit secret, 15-minute access tokens
        jwtService = new JwtService(
                "super-secret-key-for-testing-only-min-32-chars!", 15);
    }

    // ── Token generation ────────────────────────────────────────────────

    @Test
    @DisplayName("generateAccessToken returns a non-blank JWT string")
    void generateAccessToken_returnsNonBlank() {
        String token = jwtService.generateAccessToken("alice@u.nus.edu");

        assertThat(token).isNotBlank();
        // JWTs have 3 dot-separated parts
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Two tokens for different users are different")
    void generateAccessToken_uniquePerUser() {
        String t1 = jwtService.generateAccessToken("alice@u.nus.edu");
        String t2 = jwtService.generateAccessToken("bob@u.nus.edu");

        assertThat(t1).isNotEqualTo(t2);
    }

    // ── Token validation ────────────────────────────────────────────────

    @Test
    @DisplayName("isValid returns true for a freshly generated token")
    void isValid_freshToken() {
        String token = jwtService.generateAccessToken("alice@u.nus.edu");

        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid returns false for a garbage string")
    void isValid_garbageToken() {
        assertThat(jwtService.isValid("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for an empty string")
    void isValid_emptyToken() {
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for token signed with a different secret")
    void isValid_wrongSecret() {
        JwtService other = new JwtService(
                "different-secret-key-also-needs-32-chars!!", 15);
        String foreignToken = other.generateAccessToken("alice@u.nus.edu");

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }

    // ── Subject extraction ──────────────────────────────────────────────

    @Test
    @DisplayName("extractUsername returns the email embedded in the token")
    void extractUsername_returnsSubject() {
        String token = jwtService.generateAccessToken("alice@u.nus.edu");

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@u.nus.edu");
    }

    @Test
    @DisplayName("extractUsername throws on invalid token")
    void extractUsername_throwsOnInvalid() {
        assertThatThrownBy(() -> jwtService.extractUsername("bad-token"))
                .isInstanceOf(Exception.class);
    }

    // ── Expiry config ───────────────────────────────────────────────────

    @Test
    @DisplayName("expiresInSeconds reflects configured minutes × 60")
    void expiresInSeconds_matchesConfig() {
        assertThat(jwtService.expiresInSeconds()).isEqualTo(15 * 60);
    }

    @Test
    @DisplayName("Custom expiry minutes are respected")
    void expiresInSeconds_customMinutes() {
        JwtService custom = new JwtService(
                "super-secret-key-for-testing-only-min-32-chars!", 30);
        assertThat(custom.expiresInSeconds()).isEqualTo(1800L);
    }
}