package mtech.swe5006.peerconnect.data.sql;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nus_student_id", nullable = false, unique = true)
    private String nusStudentId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "user_type", nullable = false, length = 30)
    private String userType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();

        // Provide safe defaults so inserts never violate NOT NULL
        if (this.firstName == null) this.firstName = "";
        if (this.lastName == null) this.lastName = "";
        if (this.userType == null) this.userType = "student";
        if (this.status == null) this.status = "active";

        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===== Getters / Setters =====

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getNusStudentId() { return nusStudentId; }
    public void setNusStudentId(String nusStudentId) { this.nusStudentId = nusStudentId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
