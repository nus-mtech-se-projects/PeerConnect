IF OBJECT_ID('dbo.peer_feedback', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.peer_feedback (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        peer_tutor_group_id UNIQUEIDENTIFIER NOT NULL,
        session_id UNIQUEIDENTIFIER NOT NULL,
        reviewer_id UNIQUEIDENTIFIER NOT NULL,
        reviewee_id UNIQUEIDENTIFIER NOT NULL,
        overall_rating SMALLINT NOT NULL,
        preparedness SMALLINT NOT NULL,
        communication SMALLINT NOT NULL,
        helpfulness SMALLINT NOT NULL,
        reliability SMALLINT NOT NULL,
        strengths NVARCHAR(2000) NULL,
        improvements NVARCHAR(2000) NULL,
        anonymous_to_peer BIT NOT NULL CONSTRAINT DF_peer_feedback_anonymous_to_peer DEFAULT 0,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_peer_feedback_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_peer_feedback_tutoring_class FOREIGN KEY (peer_tutor_group_id) REFERENCES dbo.tutoring_courses(id),
        CONSTRAINT FK_peer_feedback_reviewer FOREIGN KEY (reviewer_id) REFERENCES dbo.users(id),
        CONSTRAINT FK_peer_feedback_reviewee FOREIGN KEY (reviewee_id) REFERENCES dbo.users(id),
        CONSTRAINT UQ_peer_feedback_submission UNIQUE (session_id, reviewer_id, reviewee_id),
        CONSTRAINT CK_peer_feedback_overall_rating CHECK (overall_rating BETWEEN 1 AND 5),
        CONSTRAINT CK_peer_feedback_preparedness CHECK (preparedness BETWEEN 1 AND 5),
        CONSTRAINT CK_peer_feedback_communication CHECK (communication BETWEEN 1 AND 5),
        CONSTRAINT CK_peer_feedback_helpfulness CHECK (helpfulness BETWEEN 1 AND 5),
        CONSTRAINT CK_peer_feedback_reliability CHECK (reliability BETWEEN 1 AND 5),
        CONSTRAINT CK_peer_feedback_not_self CHECK (reviewer_id <> reviewee_id)
    );

    CREATE INDEX IX_peer_feedback_group_session
        ON dbo.peer_feedback (peer_tutor_group_id, session_id);

    CREATE INDEX IX_peer_feedback_reviewee_session
        ON dbo.peer_feedback (reviewee_id, session_id);
END;
