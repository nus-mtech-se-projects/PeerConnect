package mtech.swe5006.peerconnect.data.sql;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "study_sessions", indexes = {
    @Index(name = "IX_study_sessions_group_date", columnList = "group_id, starts_at")
})
public class StudySession extends BaseEntity {

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "starts_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime startsAt;

    @Column(name = "ends_at", columnDefinition = "DATETIME2")
    private LocalDateTime endsAt;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
