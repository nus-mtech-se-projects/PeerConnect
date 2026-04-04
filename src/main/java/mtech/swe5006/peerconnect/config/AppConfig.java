package mtech.swe5006.peerconnect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Value("${openai.connect-timeout.seconds:5}")
    private long openAiConnectTimeoutSeconds;

    @Value("${openai.read-timeout.seconds:30}")
    private long openAiReadTimeoutSeconds;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) (openAiConnectTimeoutSeconds * 1000));
        factory.setReadTimeout((int) (openAiReadTimeoutSeconds * 1000));
        return new RestTemplate(factory);
    }
}
