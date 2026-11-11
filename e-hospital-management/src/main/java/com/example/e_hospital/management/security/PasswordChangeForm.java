package com.example.e_hospital.management.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeForm {
    @NotBlank @Size(max = 72) private String currentPassword;
    @NotBlank @Size(min = 12, max = 72) private String newPassword;
    @NotBlank @Size(min = 12, max = 72) private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String value) { currentPassword = value; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String value) { newPassword = value; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String value) { confirmPassword = value; }
}
