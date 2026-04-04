package mtech.swe5006.peerconnect.api;

import jakarta.validation.Valid;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorRequest;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorResponse;
import mtech.swe5006.peerconnect.service.AiTutorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai-tutor")
public class AiTutorController {

    private final AiTutorService aiTutorService;

    public AiTutorController(AiTutorService aiTutorService) {
        this.aiTutorService = aiTutorService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AiTutorRequest request) {
        String reply = aiTutorService.chat(request);
        return ResponseEntity.ok(new AiTutorResponse(reply));
    }

    @ExceptionHandler(AiTutorService.AiUnavailableException.class)
    public ResponseEntity<?> handleAiUnavailable() {
        return ResponseEntity.internalServerError().body(Map.of("error", "AI service unavailable"));
    }
}
