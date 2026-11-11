package com.example.e_hospital.management.security;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class SessionRevocationService {
    private final SessionRegistry sessions;
    public SessionRevocationService(SessionRegistry sessions) { this.sessions = sessions; }
    public void expire(String username) {
        sessions.getAllPrincipals().stream().filter(p -> p instanceof UserDetails u && u.getUsername().equals(username))
            .forEach(p -> sessions.getAllSessions(p, false).forEach(s -> s.expireNow()));
    }
}
