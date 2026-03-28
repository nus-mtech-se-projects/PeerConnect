package mtech.swe5006.peerconnect.service.audit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AzureMonitorAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(AzureMonitorAuditSink.class);
    private static final io.opentelemetry.api.logs.Logger azureAuditLogger =
        GlobalOpenTelemetry.get().getLogsBridge().get("peerconnect-audit");
    private static final AttributeKey<String> CUSTOM_EVENT_NAME =
        AttributeKey.stringKey("microsoft.custom_event.name");

    @Override
    public void publish(AuditDispatchContext context) {
        try {
            var logRecord = azureAuditLogger.logRecordBuilder()
                .setSeverity(Severity.INFO)
                .setBody("audit-event")
                .setAttribute(CUSTOM_EVENT_NAME, context.eventType())
                .setAttribute(AttributeKey.stringKey("eventType"), context.eventType())
                .setAttribute(AttributeKey.stringKey("outcome"), context.outcome())
                .setAttribute(AttributeKey.stringKey("actorEmail"), nullSafe(context.actorEmail()))
                .setAttribute(AttributeKey.stringKey("actorUserId"), nullSafe(context.actorUserId()))
                .setAttribute(AttributeKey.stringKey("targetType"), nullSafe(context.targetType()))
                .setAttribute(AttributeKey.stringKey("targetId"), nullSafe(context.targetId()))
                .setAttribute(AttributeKey.stringKey("requestId"), nullSafe(context.requestId()))
                .setAttribute(AttributeKey.stringKey("ipAddress"), nullSafe(context.ipAddress()));

            if (context.details() != null) {
                context.details().forEach((key, value) ->
                    logRecord.setAttribute(AttributeKey.stringKey("detail_" + key), value != null ? value.toString() : "null")
                );
            }

            logRecord.emit();
        } catch (Exception ex) {
            log.warn("Failed to send audit event to Azure Monitor: {}", context.eventType(), ex);
        }
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "unknown";
    }
}
