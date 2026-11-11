package com.example.e_hospital.management.security;

import java.util.ArrayList;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class HospitalPrincipal extends User {
    private final long credentialVersion;
    public HospitalPrincipal(HospitalUser user) {
        super(user.getUsername(), user.getPasswordHash(), user.isActive(), true, true, true, authorities(user.getRole()));
        credentialVersion = user.getCredentialVersion();
    }
    private static java.util.List<SimpleGrantedAuthority> authorities(Role role) {
        var result = new ArrayList<SimpleGrantedAuthority>();
        result.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.permissions().forEach(p -> result.add(new SimpleGrantedAuthority(p.name())));
        return result;
    }
    public long getCredentialVersion() { return credentialVersion; }
}
