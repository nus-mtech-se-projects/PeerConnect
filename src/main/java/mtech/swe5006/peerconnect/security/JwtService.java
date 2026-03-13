package mtech.swe5006.peerconnect.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

  private final Algorithm algorithm;
  private final JWTVerifier verifier;
  private final long accessTokenMinutes;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access.token.minutes:15}") long accessTokenMinutes
  ) {
    this.algorithm = Algorithm.HMAC256(secret);
    this.verifier = JWT.require(this.algorithm).build();
    this.accessTokenMinutes = accessTokenMinutes;
  }

  public long expiresInSeconds() {
    return accessTokenMinutes * 60L;
  }

  public String generateAccessToken(String username) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(expiresInSeconds());

    return JWT.create()
        .withSubject(username)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(exp))
        .sign(algorithm);
  }

  public String extractUsername(String token) {
    return verify(token).getSubject();
  }

  public boolean isValid(String token) {
    try {
      verify(token);
      return true;
    } catch (JWTVerificationException e) {
      return false;
    }
  }

  private DecodedJWT verify(String token) {
    return verifier.verify(token);
  }
}

