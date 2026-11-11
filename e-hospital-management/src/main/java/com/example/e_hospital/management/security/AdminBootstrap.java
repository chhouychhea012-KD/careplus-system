package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"dev", "test", "provision-admin"})
public class AdminBootstrap implements ApplicationRunner {
    private final HospitalUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String username;
    private final String password;

    public AdminBootstrap(HospitalUserRepository users, PasswordEncoder encoder, AuditService audit,
            @Value("${hospital.bootstrap.username:}") String username,
            @Value("${hospital.bootstrap.password:}") String password) {
        this.users = users; this.encoder = encoder; this.audit = audit;
        this.username = username; this.password = password;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() != 0) return;
        if (!username.matches("[a-zA-Z0-9._@-]{3,100}")) {
            throw new IllegalStateException("Set HOSPITAL_ADMIN_USERNAME and HOSPITAL_ADMIN_PASSWORD to initialize the first account.");
        }
        PasswordPolicy.validate(password);
        HospitalUser admin = users.save(new HospitalUser(username, "Hospital Administrator", encoder.encode(password), Role.ADMIN));
        audit.record("SYSTEM", "ADMIN_INITIALIZED", "user:" + admin.getId());
    }
}
