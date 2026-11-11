package com.example.e_hospital.management.security;

public interface ResetDelivery {
    boolean available();
    void send(String email, String token);
}
