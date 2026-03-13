package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "study_groups", indexes = {
    @Index(name = "IX_study_groups_discovery",
           columnList = "course_id, status, study_mode, created_at")
})
public class StudyGroup {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "topic", length = 200)
    private String topic;

<<<<<<< HEAD
    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "module_code", length = 50)
    private String moduleCode;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "preferred_schedule", length = 200)
    private String preferredSchedule;

=======
>>>>>>> e77211ef49674d5296d3b585227dacda65f6cfa3
    @Column(name = "study_mode", length = 20)
    private String studyMode;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "max_members")
    private Short maxMembers;

    @Column(name = "status", length = 20)
    private String status;

<<<<<<< HEAD
    @Column(name = "approval_required")
    private Boolean approvalRequired;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
=======
    @Column(name = "created_at", columnDefinition = "datetime2")
>>>>>>> e77211ef49674d5296d3b585227dacda65f6cfa3
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.status == null) this.status = "active";
<<<<<<< HEAD
        if (this.approvalRequired == null) this.approvalRequired = Boolean.FALSE;
=======
>>>>>>> e77211ef49674d5296d3b585227dacda65f6cfa3
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    // ===== Getters / Setters =====

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

<<<<<<< HEAD
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }

    public String getPreferredSchedule() { return preferredSchedule; }
    public void setPreferredSchedule(String preferredSchedule) { this.preferredSchedule = preferredSchedule; }

=======
>>>>>>> e77211ef49674d5296d3b585227dacda65f6cfa3
    public String getStudyMode() { return studyMode; }
    public void setStudyMode(String studyMode) { this.studyMode = studyMode; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Short getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Short maxMembers) { this.maxMembers = maxMembers; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

<<<<<<< HEAD
    public Boolean getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(Boolean approvalRequired) { this.approvalRequired = approvalRequired; }

=======
>>>>>>> e77211ef49674d5296d3b585227dacda65f6cfa3
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
