package com.example.e_hospital.management.security;

import jakarta.validation.constraints.*;

public class ProfileForm {
    @NotBlank @Size(max = 100) private String displayName;
    @Pattern(regexp = "[0-9+() .-]{0,30}", message = "Enter a valid contact phone number.") private String phone;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
}
