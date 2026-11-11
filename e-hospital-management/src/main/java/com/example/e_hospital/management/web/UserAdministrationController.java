package com.example.e_hospital.management.web;

import com.example.e_hospital.management.security.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserAdministrationController {
    private final UserAdministrationService users;
    private final SessionRevocationService sessions;
    public UserAdministrationController(UserAdministrationService users, SessionRevocationService sessions) { this.users = users; this.sessions = sessions; }
    @ModelAttribute("roles") public Role[] roles() { return Role.values(); }
    @GetMapping public String list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        q = q.substring(0, Math.min(q.length(), 100)); model.addAttribute("q", q);
        model.addAttribute("users", users.list(q, page)); return "users";
    }
    @GetMapping("/new") public String create(Model model) { model.addAttribute("form", new UserForm()); model.addAttribute("creating", true); return "user-form"; }
    @PostMapping("/new") public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult errors, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { users.create(form); } catch (IllegalArgumentException ex) { errors.reject("user", ex.getMessage()); }
        }
        if (errors.hasErrors()) { model.addAttribute("creating", true); return "user-form"; }
        flash.addFlashAttribute("success", "Staff account created. Share its initial password through a secure channel."); return "redirect:/admin/users";
    }
    @GetMapping("/{id}") public String edit(@PathVariable Long id, Model model) {
        var user = users.get(id); var form = new UserForm();
        form.setUsername(user.username()); form.setDisplayName(user.displayName()); form.setEmail(user.email()); form.setRole(user.role()); form.setActive(user.active());
        model.addAttribute("form", form); model.addAttribute("creating", false); model.addAttribute("userId", id); return "user-form";
    }
    @PostMapping("/{id}") public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UserForm form,
            BindingResult errors, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { sessions.expire(users.update(id, form)); }
            catch (IllegalArgumentException ex) { errors.reject("user", ex.getMessage()); }
        }
        if (errors.hasErrors()) { model.addAttribute("creating", false); model.addAttribute("userId", id); return "user-form"; }
        flash.addFlashAttribute("success", "Staff account updated. Existing sessions will be checked against the new access settings."); return "redirect:/admin/users";
    }
}
