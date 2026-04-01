IF OBJECT_ID('dbo.audit_event', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.audit_event (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        event_type NVARCHAR(100) NOT NULL,
        actor_user_id UNIQUEIDENTIFIER NULL,
        actor_email NVARCHAR(255) NULL,
        target_type NVARCHAR(100) NULL,
        target_id UNIQUEIDENTIFIER NULL,
        outcome NVARCHAR(20) NOT NULL CONSTRAINT DF_audit_event_outcome DEFAULT 'SUCCESS',
        event_time DATETIME2 NOT NULL CONSTRAINT DF_audit_event_event_time DEFAULT SYSUTCDATETIME(),
        request_id NVARCHAR(100) NULL,
        ip_address NVARCHAR(64) NULL,
        details_json NVARCHAR(4000) NULL
    );

    CREATE INDEX IX_audit_event_event_time
        ON dbo.audit_event (event_time);

    CREATE INDEX IX_audit_event_event_type_event_time
        ON dbo.audit_event (event_type, event_time);

    CREATE INDEX IX_audit_event_actor_user_id_event_time
        ON dbo.audit_event (actor_user_id, event_time);

    CREATE INDEX IX_audit_event_target_type_target_id
        ON dbo.audit_event (target_type, target_id);
END;
