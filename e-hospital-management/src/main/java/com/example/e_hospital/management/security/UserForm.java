package com.example.e_hospital.management.security;

import jakarta.validation.constraints.*;

public class UserForm {
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{3,100}", message = "Use 3–100 letters, numbers, dots, underscores, @ or hyphens.") private String username;
    @NotBlank @Size(max = 100) private String displayName;
    @Email @Size(max = 254) private String email;
    @NotNull private Role role = Role.RECEPTIONIST;
    private boolean active = true;
    @Size(max = 72) private String initialPassword;
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public Role getRole() { return role; }
    public void setRole(Role value) { role = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String value) { initialPassword = value; }
}
