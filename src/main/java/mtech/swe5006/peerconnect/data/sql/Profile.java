package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @PrePersist
    void prePersist() {
        if (this.fullTimeInd == null) this.fullTimeInd = "N";
    }

    @Column(name = "faculty", length = 100)
    private String faculty;

    @Column(name = "major", length = 100)
    private String major;

    @Column(name = "year_of_study")
    private Short yearOfStudy;

    @Column(name = "bio", columnDefinition = "nvarchar(max)")
    private String bio;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "full_time_ind", length = 1)
    private String fullTimeInd;

    // ===== Getters / Setters =====

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public Short getYearOfStudy() { return yearOfStudy; }
    public void setYearOfStudy(Short yearOfStudy) { this.yearOfStudy = yearOfStudy; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getFullTimeInd() { return fullTimeInd; }
    public void setFullTimeInd(String fullTimeInd) { this.fullTimeInd = fullTimeInd; }
}
