package mtech.swe5006.peerconnect.service.audit;

public interface AuditSink {

    default boolean supports(AuditDispatchContext context) {
        return true;
    }

    void publish(AuditDispatchContext context);
}
