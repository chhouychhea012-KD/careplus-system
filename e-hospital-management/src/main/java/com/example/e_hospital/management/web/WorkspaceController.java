package com.example.e_hospital.management.web;

import com.example.e_hospital.management.audit.AuditReadService;
import com.example.e_hospital.management.security.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class WorkspaceController {
    private final AccountService accounts;
    private final UserAdministrationService users;
    private final AuditReadService audit;
    public WorkspaceController(AccountService accounts, UserAdministrationService users, AuditReadService audit) {
        this.accounts = accounts; this.users = users; this.audit = audit;
    }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/") public String dashboard(Authentication auth, Model model) {
        model.addAttribute("profile", accounts.profile(auth.getName()));
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("USERS_MANAGE"))) {
            model.addAttribute("userCount", users.list("", 0).getTotalElements());
            model.addAttribute("auditCount", audit.count());
            model.addAttribute("recentEvents", audit.list("", 0).getContent().stream().limit(5).toList());
        }
        return "dashboard";
    }
    @GetMapping("/help") public String help() { return "help"; }
    @GetMapping("/access-denied") public String denied(HttpServletResponse response) { response.setStatus(403); return "error/403"; }
    @GetMapping("/admin/audit") public String audit(@RequestParam(defaultValue = "") String actor,
            @RequestParam(defaultValue = "0") int page, Model model) {
        actor = actor.substring(0, Math.min(actor.length(), 100));
        model.addAttribute("actor", actor); model.addAttribute("events", audit.list(actor, page)); return "audit";
    }
}
