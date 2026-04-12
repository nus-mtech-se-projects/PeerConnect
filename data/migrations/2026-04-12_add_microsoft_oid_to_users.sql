IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.users') AND name = 'microsoft_oid'
)
BEGIN
    ALTER TABLE dbo.users ADD microsoft_oid NVARCHAR(36) NULL;

    CREATE INDEX IX_users_microsoft_oid
        ON dbo.users (microsoft_oid)
        WHERE microsoft_oid IS NOT NULL;
END;
