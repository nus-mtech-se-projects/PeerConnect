package mtech.swe5006.peerconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAnnouncementRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String content
) {
}
