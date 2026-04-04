package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "peer_feedback", indexes = {
    @Index(name = "IX_peer_feedback_group_session", columnList = "peer_tutor_group_id, session_id"),
    @Index(name = "IX_peer_feedback_reviewee_session", columnList = "reviewee_id, session_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "UQ_peer_feedback_submission", columnNames = {"session_id", "reviewer_id", "reviewee_id"})
})
public class PeerFeedback {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "peer_tutor_group_id", nullable = false)
    private UUID peerTutorGroupId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private UUID revieweeId;

    @Column(name = "overall_rating", nullable = false)
    private Short overallRating;

    @Column(name = "preparedness", nullable = false)
    private Short preparedness;

    @Column(name = "communication", nullable = false)
    private Short communication;

    @Column(name = "helpfulness", nullable = false)
    private Short helpfulness;

    @Column(name = "reliability", nullable = false)
    private Short reliability;

    @Column(name = "strengths", length = 2000)
    private String strengths;

    @Column(name = "improvements", length = 2000)
    private String improvements;

    @Column(name = "anonymous_to_peer", nullable = false)
    private Boolean anonymousToPeer;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME2")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.anonymousToPeer == null) this.anonymousToPeer = Boolean.FALSE;
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPeerTutorGroupId() { return peerTutorGroupId; }
    public void setPeerTutorGroupId(UUID peerTutorGroupId) { this.peerTutorGroupId = peerTutorGroupId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getReviewerId() { return reviewerId; }
    public void setReviewerId(UUID reviewerId) { this.reviewerId = reviewerId; }

    public UUID getRevieweeId() { return revieweeId; }
    public void setRevieweeId(UUID revieweeId) { this.revieweeId = revieweeId; }

    public Short getOverallRating() { return overallRating; }
    public void setOverallRating(Short overallRating) { this.overallRating = overallRating; }

    public Short getPreparedness() { return preparedness; }
    public void setPreparedness(Short preparedness) { this.preparedness = preparedness; }

    public Short getCommunication() { return communication; }
    public void setCommunication(Short communication) { this.communication = communication; }

    public Short getHelpfulness() { return helpfulness; }
    public void setHelpfulness(Short helpfulness) { this.helpfulness = helpfulness; }

    public Short getReliability() { return reliability; }
    public void setReliability(Short reliability) { this.reliability = reliability; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }

    public Boolean getAnonymousToPeer() { return anonymousToPeer; }
    public void setAnonymousToPeer(Boolean anonymousToPeer) { this.anonymousToPeer = anonymousToPeer; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
