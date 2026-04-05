IF OBJECT_ID('dbo.group_chat_messages', 'U') IS NOT NULL
BEGIN
    BEGIN TRY
        ALTER TABLE dbo.group_chat_messages ALTER COLUMN content NVARCHAR(2000) NOT NULL;
    END TRY
    BEGIN CATCH
        -- no-op for environments where column already matches type
    END CATCH
END;
