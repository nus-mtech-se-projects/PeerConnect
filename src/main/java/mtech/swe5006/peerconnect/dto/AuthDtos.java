package mtech.swe5006.peerconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

  public record RegisterRequest(
      @NotBlank @Size(min = 3, max = 50) String username,
      @NotBlank @Size(min = 8, max = 72) String password
  ) {}

  public record LoginRequest(
      @NotBlank String username,
      @NotBlank String password
  ) {}

  public record TokenResponse(
      String accessToken,
      String tokenType,
      long expiresInSeconds
  ) {}

  public record MeResponse(Long id, String username) {}
}
