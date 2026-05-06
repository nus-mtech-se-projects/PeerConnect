package mtech.swe5006.peerconnect.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfig {

    private static final Logger log = LoggerFactory.getLogger(SentryConfig.class);

    @Value("${sentry.dsn:}")
    private String dsn;

    @Value("${sentry.traces-sample-rate:0.0}")
    private double tracesSampleRate;

    @Value("${sentry.environment:production}")
    private String environment;

    @PostConstruct
    public void init() {
        if (dsn == null || dsn.isBlank()) {
            log.info("Sentry DSN not set — Sentry disabled.");
            return;
        }
        try {
            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setTracesSampleRate(tracesSampleRate);
                options.setEnvironment(environment);
            });
            log.info("Sentry initialized (environment={}).", environment);
        } catch (Exception e) {
            log.error("Sentry init failed — continuing without Sentry.", e);
        }
    }
}
