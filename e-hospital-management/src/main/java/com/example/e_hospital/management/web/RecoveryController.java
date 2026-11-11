package com.example.e_hospital.management.web;

import com.example.e_hospital.management.security.*;
import com.example.e_hospital.management.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RecoveryController {
    public static class RequestForm {
        @NotBlank @Size(max = 100) private String username;
        public String getUsername() { return username; }
        public void setUsername(String value) { username = value; }
    }
    public static class ResetForm {
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Open a valid reset link from your email.") private String token;
        @NotBlank @Size(min = 12, max = 72) private String newPassword;
        @NotBlank @Size(min = 12, max = 72) private String confirmPassword;
        public String getToken() { return token; }
        public void setToken(String value) { token = value; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String value) { newPassword = value; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String value) { confirmPassword = value; }
    }
    private final RecoveryDispatcher dispatcher;
    private final PasswordRecoveryService recovery;
    private final LoginThrottle throttle;
    private final SessionRevocationService sessions;
    private final AuditService audit;
    public RecoveryController(RecoveryDispatcher dispatcher, PasswordRecoveryService recovery, LoginThrottle throttle,
            SessionRevocationService sessions, AuditService audit) {
        this.dispatcher = dispatcher; this.recovery = recovery; this.throttle = throttle; this.sessions = sessions; this.audit = audit;
    }
    @GetMapping("/forgot-password") public String forgot(Model model) { model.addAttribute("form", new RequestForm()); return "forgot-password"; }
    @PostMapping("/forgot-password") public String request(@Valid @ModelAttribute("form") RequestForm form,
            BindingResult errors, HttpServletRequest request, Model model) {
        if (errors.hasErrors()) return "forgot-password";
        if (throttle.allowRecovery(request.getRemoteAddr())) dispatcher.submit(form.getUsername());
        model.addAttribute("sent", true); return "forgot-password";
    }
    @GetMapping("/reset-password") public String reset(Model model) { model.addAttribute("form", new ResetForm()); return "reset-password"; }
    @PostMapping("/reset-password") public String reset(@Valid @ModelAttribute("form") ResetForm form, BindingResult errors) {
        if (errors.hasErrors()) return "reset-password";
        try { sessions.expire(recovery.reset(form.getToken(), form.getNewPassword(), form.getConfirmPassword())); }
        catch (IllegalArgumentException ex) {
            audit.record("ANONYMOUS", "PASSWORD_RESET_REJECTED", "authentication");
            errors.reject("reset", ex.getMessage()); return "reset-password";
        }
        return "redirect:/login?reset";
    }
}
