package com.example.e_hospital.management.security;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "hospital_users", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
public class HospitalUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version private long version;
    @Column(nullable = false, length = 100) private String username;
    @Column(nullable = false, length = 100) private String displayName;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30) private Role role;
    @Column(nullable = false) private boolean active = true;
    @Column(length = 254, unique = true) private String email;
    @Column(length = 30) private String phone;
    @Column(nullable = false) private long credentialVersion;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected HospitalUser() {}

    public HospitalUser(String username, String displayName, String passwordHash, Role role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public long getCredentialVersion() { return credentialVersion; }
    public void updateProfile(String name, String phone) { this.displayName = name; this.phone = phone; }
    public void configureAccess(Role role, boolean active, String email) {
        if (this.role != role || this.active != active || !java.util.Objects.equals(this.email, email)) credentialVersion++;
        this.role = role; this.active = active; this.email = email;
    }
    public void changePassword(String hash) { passwordHash = hash; credentialVersion++; }
}
