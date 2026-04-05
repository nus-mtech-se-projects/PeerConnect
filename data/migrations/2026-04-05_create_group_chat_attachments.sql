IF OBJECT_ID('dbo.group_chat_attachments', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.group_chat_attachments (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        message_id UNIQUEIDENTIFIER NOT NULL,
        chat_id UNIQUEIDENTIFIER NOT NULL,
        group_id UNIQUEIDENTIFIER NOT NULL,
        original_file_name NVARCHAR(260) NOT NULL,
        content_type NVARCHAR(120) NOT NULL,
        file_size BIGINT NOT NULL,
        storage_path NVARCHAR(500) NOT NULL,
        file_data VARBINARY(MAX) NOT NULL,
        uploaded_at DATETIME2 NOT NULL,
        CONSTRAINT UK_group_chat_attachments_message UNIQUE (message_id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_group_chat_attachments_chat' AND object_id = OBJECT_ID('dbo.group_chat_attachments'))
BEGIN
    CREATE INDEX IX_group_chat_attachments_chat ON dbo.group_chat_attachments(chat_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_group_chat_attachments_group' AND object_id = OBJECT_ID('dbo.group_chat_attachments'))
BEGIN
    CREATE INDEX IX_group_chat_attachments_group ON dbo.group_chat_attachments(group_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_group_chat_attachments_message' AND object_id = OBJECT_ID('dbo.group_chat_attachments'))
BEGIN
    CREATE INDEX IX_group_chat_attachments_message ON dbo.group_chat_attachments(message_id);
END;
