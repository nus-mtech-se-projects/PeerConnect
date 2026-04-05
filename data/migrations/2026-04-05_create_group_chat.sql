IF OBJECT_ID('dbo.group_chats', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.group_chats (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        group_id UNIQUEIDENTIFIER NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL,
        CONSTRAINT UK_group_chats_group_id UNIQUE (group_id)
    );
END;

IF OBJECT_ID('dbo.group_chat_messages', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.group_chat_messages (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        chat_id UNIQUEIDENTIFIER NOT NULL,
        group_id UNIQUEIDENTIFIER NOT NULL,
        sender_id UNIQUEIDENTIFIER NOT NULL,
        content NVARCHAR(2000) NOT NULL,
        sent_at DATETIME2 NOT NULL,
        CONSTRAINT FK_group_chat_messages_chat FOREIGN KEY (chat_id) REFERENCES dbo.group_chats(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_group_chat_messages_group_sent' AND object_id = OBJECT_ID('dbo.group_chat_messages'))
BEGIN
    CREATE INDEX IX_group_chat_messages_group_sent ON dbo.group_chat_messages(group_id, sent_at);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_group_chat_messages_chat_sent' AND object_id = OBJECT_ID('dbo.group_chat_messages'))
BEGIN
    CREATE INDEX IX_group_chat_messages_chat_sent ON dbo.group_chat_messages(chat_id, sent_at);
END;
