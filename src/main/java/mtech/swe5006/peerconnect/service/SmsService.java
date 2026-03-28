package mtech.swe5006.peerconnect.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final boolean enabled;
    private final String provider;
    private final String twilioAccountSid;
    private final String twilioAuthToken;
    private final String twilioFromNumber;
    private final HttpClient httpClient;

    public SmsService(
        @Value("${notification.sms.enabled:false}") boolean enabled,
        @Value("${notification.sms.provider:twilio}") String provider,
        @Value("${notification.sms.twilio.account-sid:}") String twilioAccountSid,
        @Value("${notification.sms.twilio.auth-token:}") String twilioAuthToken,
        @Value("${notification.sms.twilio.from-number:}") String twilioFromNumber) {

        this.enabled = enabled;
        this.provider = provider == null ? "twilio" : provider.trim().toLowerCase();
        this.twilioAccountSid = twilioAccountSid == null ? "" : twilioAccountSid.trim();
        this.twilioAuthToken = twilioAuthToken == null ? "" : twilioAuthToken.trim();
        this.twilioFromNumber = twilioFromNumber == null ? "" : twilioFromNumber.trim();
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean canSend() {
        return enabled && isTwilioConfigured();
    }

    public void sendSms(String toPhone, String message) {
        if (!enabled) {
            log.info("SMS disabled. Skipping send to {}", toPhone);
            return;
        }

        if (toPhone == null || toPhone.isBlank()) {
            throw new IllegalArgumentException("Recipient phone number is required for SMS.");
        }

        if (!isTwilioConfigured()) {
            throw new IllegalStateException("Twilio SMS is enabled but not configured.");
        }

        sendViaTwilio(toPhone.trim(), message == null ? "" : message);
    }

    private boolean isTwilioConfigured() {
        return "twilio".equals(provider)
            && !twilioAccountSid.isBlank()
            && !twilioAuthToken.isBlank()
            && !twilioFromNumber.isBlank();
    }

    private void sendViaTwilio(String toPhone, String message) {
        String endpoint = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", twilioAccountSid);
        String body =
            "To=" + urlEncode(toPhone)
                + "&From=" + urlEncode(twilioFromNumber)
                + "&Body=" + urlEncode(message);

        String basicAuth = Base64.getEncoder()
            .encodeToString((twilioAccountSid + ":" + twilioAuthToken).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Basic " + basicAuth)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Twilio SMS failed with status " + status + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SMS send was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send SMS", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
