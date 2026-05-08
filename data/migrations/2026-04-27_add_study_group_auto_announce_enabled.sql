/*
  Adds the auto announcement opt-in flag expected by the StudyGroup entity.

  Existing Azure SQL databases may not have this column yet. Without it,
  Hibernate queries against dbo.study_groups fail with "Invalid column name
  'auto_announce_enabled'", causing /api/groups to return 500.
*/

IF OBJECT_ID('dbo.study_groups', 'U') IS NOT NULL
    AND COL_LENGTH('dbo.study_groups', 'auto_announce_enabled') IS NULL
BEGIN
    ALTER TABLE dbo.study_groups
    ADD auto_announce_enabled BIT NOT NULL
        CONSTRAINT DF_study_groups_auto_announce_enabled DEFAULT 0;
END
GO
