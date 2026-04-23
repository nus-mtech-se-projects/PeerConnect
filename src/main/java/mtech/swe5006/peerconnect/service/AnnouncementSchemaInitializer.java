package mtech.swe5006.peerconnect.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures the announcement tables exist when the app runs against SQL Server.
 * <p>
 * When Hibernate ddl-auto creates the schema (e.g. local H2 dev), the JPA entities
 * already own the schema and this runner is a no-op. When running against SQL Server
 * with ddl-auto disabled, the {@code IF OBJECT_ID} guards create the tables idempotently.
 * Any SQL failure here is logged and swallowed so it never blocks application startup.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                IF OBJECT_ID('dbo.announcements', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.announcements (
                        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                        group_id UNIQUEIDENTIFIER NOT NULL,
                        title NVARCHAR(200) NOT NULL,
                        content NVARCHAR(4000) NOT NULL,
                        created_by UNIQUEIDENTIFIER NOT NULL,
                        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                        updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
                    );
                    CREATE INDEX idx_announcements_group_id ON dbo.announcements(group_id);
                    CREATE INDEX idx_announcements_created_at ON dbo.announcements(created_at DESC);
                    CREATE INDEX idx_announcements_created_by ON dbo.announcements(created_by);
                END
            """);

            jdbcTemplate.execute("""
                IF OBJECT_ID('dbo.announcement_archives', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.announcement_archives (
                        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                        announcement_id UNIQUEIDENTIFIER NOT NULL,
                        user_id UNIQUEIDENTIFIER NOT NULL,
                        archived_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                        CONSTRAINT UK_announcement_archive_user UNIQUE (announcement_id, user_id)
                    );
                    CREATE INDEX idx_announcement_archives_user ON dbo.announcement_archives(user_id);
                    CREATE INDEX idx_announcement_archives_announcement ON dbo.announcement_archives(announcement_id);
                END
            """);
        } catch (Exception ex) {
            // On H2 dev, Hibernate already created the tables from the JPA entities, so
            // this step may fail (e.g. 'dbo' schema doesn't exist) and that's fine.
            log.debug("[AnnouncementSchema] Initialization skipped or failed (expected on H2 dev): {}", ex.getMessage());
        }
    }
}
