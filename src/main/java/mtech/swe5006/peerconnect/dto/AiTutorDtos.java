package mtech.swe5006.peerconnect.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class AiTutorDtos {

    public record ChatMessage(String role, String content) {}

    public record AiTutorRequest(
        @NotBlank(message = "Message must not be blank") String message,
        List<ChatMessage> history
    ) {}

    public record AiTutorResponse(String reply) {}

    // OpenAI API request/response shapes
    public record OpenAiRequest(
        String model,
        List<ChatMessage> messages,
        int max_tokens,
        double temperature
    ) {}

    public record OpenAiChoice(ChatMessage message) {}

    public record OpenAiResponse(List<OpenAiChoice> choices) {}
}
