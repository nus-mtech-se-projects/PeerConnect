package mtech.swe5006.peerconnect;

import mtech.swe5006.peerconnect.api.AiTutorController;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorRequest;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorResponse;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.ChatMessage;
import mtech.swe5006.peerconnect.service.AiTutorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTutorControllerTest {

    @Mock
    private AiTutorService aiTutorService;

    @InjectMocks
    private AiTutorController controller;

    @Nested
    @DisplayName("POST /api/ai-tutor/chat")
    class Chat {

        @Test
        void returnsAiTutorReply() {
            AiTutorRequest request = new AiTutorRequest(
                "Explain binary search",
                List.of(new ChatMessage("assistant", "Let's review searching."))
            );
            when(aiTutorService.chat(request)).thenReturn("Binary search halves the search space.");

            ResponseEntity<?> response = controller.chat(request);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isEqualTo(new AiTutorResponse("Binary search halves the search space."));
            verify(aiTutorService).chat(request);
        }
    }

    @Nested
    @DisplayName("AI unavailable handling")
    class AiUnavailableHandling {

        @Test
        void returnsInternalServerErrorPayload() {
            ResponseEntity<?> response = controller.handleAiUnavailable();

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isEqualTo(Map.of("error", "AI service unavailable"));
        }
    }
}
