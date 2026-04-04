package mtech.swe5006.peerconnect.api;

import jakarta.validation.Valid;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorRequest;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorResponse;
import mtech.swe5006.peerconnect.service.AiTutorService;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> handleAiUnavailable(AiTutorService.AiUnavailableException ex) {
        HttpStatus status = "AI service is not configured".equals(ex.getMessage())
            ? HttpStatus.SERVICE_UNAVAILABLE
            : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
    }
}
