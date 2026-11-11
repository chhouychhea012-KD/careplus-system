package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class HospitalAuthenticationProvider implements AuthenticationProvider {
    private final DaoAuthenticationProvider delegate;
    private final LoginThrottle throttle;
    private final AuditService audit;
    public HospitalAuthenticationProvider(UserDetailsService users, PasswordEncoder encoder, LoginThrottle throttle, AuditService audit) {
        delegate = new DaoAuthenticationProvider(users); delegate.setPasswordEncoder(encoder);
        this.throttle = throttle; this.audit = audit;
    }
    @Override public Authentication authenticate(Authentication authentication) {
        String name = authentication.getName();
        String address = authentication.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : "local";
        if (throttle.blocked(name, address)) {
            audit.record("ANONYMOUS", "LOGIN_THROTTLED", "authentication");
            throw new BadCredentialsException("Unable to sign in");
        }
        try {
            if (name.length() > 100 || !(authentication.getCredentials() instanceof String password)
                    || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                throw new BadCredentialsException("Unable to sign in");
            }
            Authentication result = delegate.authenticate(authentication);
            throttle.succeeded(name);
            audit.record(result.getName(), "LOGIN_SUCCEEDED", "authentication");
            return result;
        } catch (AuthenticationException ex) {
            throttle.failed(name, address);
            audit.record("ANONYMOUS", "LOGIN_FAILED", "authentication");
            throw new BadCredentialsException("Unable to sign in");
        }
    }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}
