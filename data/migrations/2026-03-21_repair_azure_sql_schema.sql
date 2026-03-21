/*
  Manual repair migration for shared Azure SQL environments that were created
  before the peer feedback and preferred schedule schema changes landed.

  What this script does:
  1. Preserves an old peer_feedback table as peer_feedback_legacy when it uses
     the previous column layout.
  2. Creates the current peer_feedback table expected by the backend.
  3. Converts study_groups.preferred_schedule from VARCHAR to DATETIME2.

  Notes:
  - Run this only after reviewing the current database state.
  - The legacy peer_feedback table is kept for manual data recovery.
*/

IF OBJECT_ID('dbo.peer_feedback', 'U') IS NOT NULL
    AND COL_LENGTH('dbo.peer_feedback', 'group_id') IS NULL
BEGIN
    EXEC sp_rename 'dbo.peer_feedback', 'peer_feedback_legacy';
END
GO

IF OBJECT_ID('dbo.peer_feedback', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.peer_feedback (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        group_id UNIQUEIDENTIFIER NOT NULL,
        session_id UNIQUEIDENTIFIER NOT NULL,
        reviewer_id UNIQUEIDENTIFIER NOT NULL,
        reviewee_id UNIQUEIDENTIFIER NOT NULL,
        overall_rating TINYINT NOT NULL,
        preparedness TINYINT NOT NULL,
        communication TINYINT NOT NULL,
        helpfulness TINYINT NOT NULL,
        reliability TINYINT NOT NULL,
        strengths NVARCHAR(2000) NULL,
        improvements NVARCHAR(2000) NULL,
        anonymous_to_peer BIT NOT NULL CONSTRAINT DF_peer_feedback_new_anonymous DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_peer_feedback_new_created_at DEFAULT SYSUTCDATETIME()
    );

    CREATE UNIQUE INDEX UQ_peer_feedback_submission
        ON dbo.peer_feedback (session_id, reviewer_id, reviewee_id);

    CREATE INDEX IX_peer_feedback_group_session
        ON dbo.peer_feedback (group_id, session_id);

    CREATE INDEX IX_peer_feedback_reviewee_session
        ON dbo.peer_feedback (reviewee_id, session_id);
END
GO

IF COL_LENGTH('dbo.study_groups', 'preferred_schedule') IS NOT NULL
BEGIN
    ALTER TABLE dbo.study_groups
    ALTER COLUMN preferred_schedule DATETIME2 NULL;
END
GO
