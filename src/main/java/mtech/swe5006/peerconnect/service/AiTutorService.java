package mtech.swe5006.peerconnect.service;

import mtech.swe5006.peerconnect.dto.AiTutorDtos.AiTutorRequest;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.ChatMessage;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.OpenAiRequest;
import mtech.swe5006.peerconnect.dto.AiTutorDtos.OpenAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiTutorService {

    private static final Logger log = LoggerFactory.getLogger(AiTutorService.class);

    private static final String SYSTEM_PROMPT = """
        You are an AI Study Tutor for PeerConnect, a university study collaboration platform for NUS students.
        You help students understand academic topics, generate practice quizzes, create study plans,
        make flashcards, and produce exam-style questions with model answers.
        You also guide users around PeerConnect's features:

        - Study Groups: Students can browse, create, and join study groups organised by module.
          Group owners manage members, schedule study sessions, and invite peers.

        - Peer Tutoring: Tutors (students) create tutoring classes for a specific module.
          Tutees browse available classes and enroll. Lessons are conducted via Microsoft Teams
          meeting links provided by the tutor. After attending, tutees can submit feedback
          (star ratings and written comments) on the tutor through the portal.
          Tutors and tutees can also message each other directly via the in-app chat.

        - Well-being Support: The platform provides curated mental health and well-being resources.
          NUS students and staff are directed to NUS-specific services such as University Counselling
          Services (UCS, 6516 1972), University Health Centre Mental Health Clinic (6779 5930),
          NUS Peer Helpers Programme, and OSA Campus Life & Well-being events.
          Non-NUS users are directed to national resources including Samaritans of Singapore (SOS, 1767),
          HealthHub Mental Well-being, MindSG, NCSS Mental Health Resources, and the IMH CARE Line.
          All resources are accessible from the Well-being section in the navigation menu.

        - Profile: Students manage their academic details, contact information, and profile avatar.

        Keep answers concise, structured, and student-friendly.
        Use bullet points or numbered lists where appropriate.
        Only describe features listed above — do not invent features that are not part of PeerConnect.
        """;

    private final RestTemplate restTemplate;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.max-tokens:3000}")
    private int maxTokens;

    @Value("${openai.temperature:0.4}")
    private double temperature;

    @Value("${openai.max-history-messages:6}")
    private int maxHistoryMessages;

    @Value("${openai.max-message-chars:1500}")
    private int maxMessageChars;

    public AiTutorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String chat(AiTutorRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OpenAI API key is not configured");
            throw new AiUnavailableException("AI service is not configured");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            log.error("OpenAI API URL is not configured");
            throw new AiUnavailableException("AI service is not configured");
        }
        if (model == null || model.isBlank()) {
            log.error("OpenAI model is not configured");
            throw new AiUnavailableException("AI service is not configured");
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT));

        if (request.history() != null && !request.history().isEmpty()) {
            List<ChatMessage> trimmedHistory = trimHistory(request.history());
            messages.addAll(trimmedHistory);
        }
        messages.add(new ChatMessage("user", truncate(request.message())));

        OpenAiRequest body = new OpenAiRequest(model, messages, maxTokens, temperature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            OpenAiResponse response = restTemplate.postForObject(
                apiUrl,
                new HttpEntity<>(body, headers),
                OpenAiResponse.class
            );

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiUnavailableException("AI service returned an empty response");
            }
            return response.choices().get(0).message().content();

        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            log.error("OpenAI call failed [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiUnavailableException("AI service request was rejected");
        } catch (org.springframework.web.client.HttpServerErrorException ex) {
            log.error("OpenAI server error [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AiUnavailableException("AI service is temporarily unavailable");
        } catch (RestClientException ex) {
            log.error("OpenAI call failed: {}", ex.getMessage(), ex);
            throw new AiUnavailableException("AI service request failed");
        }
    }

    private List<ChatMessage> trimHistory(List<ChatMessage> history) {
        int fromIndex = Math.max(0, history.size() - maxHistoryMessages);
        return history.subList(fromIndex, history.size()).stream()
            .map(message -> new ChatMessage(message.role(), truncate(message.content())))
            .toList();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxMessageChars) {
            return text;
        }
        return text.substring(0, maxMessageChars);
    }

    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }

        public AiUnavailableException() {
            super("AI service unavailable");
        }
    }
}
