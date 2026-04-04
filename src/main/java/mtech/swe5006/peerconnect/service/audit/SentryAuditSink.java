package mtech.swe5006.peerconnect.service.audit;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Order(2)
public class SentryAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(SentryAuditSink.class);
    private static final Set<String> FAILURE_OUTCOMES = Set.of("FAILURE", "REJECTED", "INVALID");

    @Override
    public boolean supports(AuditDispatchContext context) {
        return FAILURE_OUTCOMES.contains(context.outcome());
    }

    @Override
    public void publish(AuditDispatchContext context) {
        try {
            Sentry.withScope(scope -> {
                scope.setTag("eventType", context.eventType());
                scope.setTag("outcome", context.outcome());
                scope.setTag("targetType", nullSafe(context.targetType()));

                scope.setExtra("actorUserId", nullSafe(context.actorUserId()));
                scope.setExtra("actorEmail", nullSafe(context.actorEmail()));
                scope.setExtra("targetId", nullSafe(context.targetId()));
                scope.setExtra("requestId", nullSafe(context.requestId()));
                scope.setExtra("ipAddress", nullSafe(context.ipAddress()));

                if (context.details() != null) {
                    context.details().forEach((key, value) ->
                        scope.setExtra("detail_" + key, value != null ? value.toString() : "null")
                    );
                }

                SentryEvent sentryEvent = new SentryEvent();
                Message msg = new Message();
                msg.setMessage(context.eventType() + " | outcome=" + context.outcome()
                    + " | actor=" + nullSafe(context.actorEmail()));
                sentryEvent.setMessage(msg);
                sentryEvent.setLevel(SentryLevel.WARNING);
                Sentry.captureEvent(sentryEvent);
            });
        } catch (Exception ex) {
            log.warn("Failed to send audit event to Sentry: {}", context.eventType(), ex);
        }
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "unknown";
    }
}
