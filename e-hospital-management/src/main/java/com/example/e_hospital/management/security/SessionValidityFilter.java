package com.example.e_hospital.management.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionValidityFilter extends OncePerRequestFilter {
    private final HospitalUserRepository users;
    public SessionValidityFilter(HospitalUserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicPath = java.util.Set.of("/login", "/forgot-password", "/reset-password", "/error").contains(path)
            || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/webjars/");
        if (!publicPath && request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()
                && (authentication == null || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            var cookie = new Cookie("JSESSIONID", ""); cookie.setMaxAge(0);
            cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
            cookie.setHttpOnly(true); cookie.setSecure(request.isSecure()); response.addCookie(cookie);
            response.sendRedirect(request.getContextPath() + "/login?expired"); return;
        }
        if (authentication != null && authentication.getPrincipal() instanceof HospitalPrincipal principal) {
            var user = users.findByUsername(principal.getUsername());
            if (user.isEmpty() || !user.get().isActive() || user.get().getCredentialVersion() != principal.getCredentialVersion()) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
