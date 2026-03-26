package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tutoring_classes", indexes = {
    @Index(name = "IX_tutoring_classes_discovery", columnList = "module_code, created_at")
})
public class TutoringClass {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "module_code", nullable = false, length = 50)
    private String moduleCode;

    @Column(name = "topic", length = 200)
    private String topic;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "schedule", nullable = false, length = 200)
    private String schedule;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "max_students")
    private Short maxStudents;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.mode == null) this.mode = "online";
        if (this.maxStudents == null) this.maxStudents = 5;
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }
    public Short getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Short maxStudents) { this.maxStudents = maxStudents; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
