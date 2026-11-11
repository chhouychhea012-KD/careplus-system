package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }
    @Bean HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean UserDetailsService userDetailsService(HospitalUserRepository users) {
        return username -> users.findByUsername(username)
            .map(HospitalPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean HospitalAuthenticationProvider hospitalAuthenticationProvider(UserDetailsService users,
            PasswordEncoder encoder, LoginThrottle throttle, AuditService audit) {
        return new HospitalAuthenticationProvider(users, encoder, throttle, audit);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuditService audit,
            SessionRegistry registry, HospitalAuthenticationProvider provider, HospitalUserRepository users) throws Exception {
        http.authenticationManager(new org.springframework.security.authentication.ProviderManager(provider)).authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/login", "/forgot-password", "/reset-password", "/css/**", "/js/**", "/webjars/**", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?error"))
            .logout(logout -> logout.logoutSuccessHandler((request, response, auth) -> {
                if (auth != null) audit.record(auth.getName(), "LOGOUT", "authentication");
                response.sendRedirect(request.getContextPath() + "/login?logout");
            }).deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(3).expiredUrl("/login?expired").sessionRegistry(registry))
            .exceptionHandling(errors -> errors.accessDeniedPage("/access-denied"))
            .addFilterBefore(new SessionValidityFilter(users), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }
}
