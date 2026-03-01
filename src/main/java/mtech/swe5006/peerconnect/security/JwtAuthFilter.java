package mtech.swe5006.peerconnect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final CustomUserDetailsService userDetailsService;

  public JwtAuthFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    System.out.println("[JWT] Path: " + request.getRequestURI() + " | Header: " + (header != null ? "present" : "missing"));

    if (header == null || !header.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }

    String token = header.substring(7);
    boolean valid = jwtService.isValid(token);
    System.out.println("[JWT] Token valid: " + valid);

    if (!valid) {
      chain.doFilter(request, response);
      return;
    }

    String username = jwtService.extractUsername(token);
    System.out.println("[JWT] Username: " + username);

    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        UserDetails ud = userDetailsService.loadUserByUsername(username);
        System.out.println("[JWT] User loaded: " + ud.getUsername());
        var auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        System.out.println("[JWT] Auth set successfully");
      } catch (Exception e) {
        System.out.println("[JWT] ERROR: " + e.getMessage());
        e.printStackTrace();
      }
    }

    chain.doFilter(request, response);
  }
}
