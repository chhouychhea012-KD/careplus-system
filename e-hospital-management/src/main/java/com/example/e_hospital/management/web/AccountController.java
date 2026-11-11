package com.example.e_hospital.management.web;

import com.example.e_hospital.management.security.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AccountService accounts;
    private final SessionRevocationService sessions;
    public AccountController(AccountService accounts, SessionRevocationService sessions) { this.accounts = accounts; this.sessions = sessions; }
    @InitBinder("form") public void restrictFields(WebDataBinder binder) {
        binder.setAllowedFields("displayName", "phone", "currentPassword", "newPassword", "confirmPassword", "_csrf");
    }
    @GetMapping("/profile") public String profile(Authentication auth, Model model) {
        var profile = accounts.profile(auth.getName());
        var form = new ProfileForm(); form.setDisplayName(profile.displayName()); form.setPhone(profile.phone());
        model.addAttribute("profile", profile); model.addAttribute("form", form); return "profile";
    }
    @PostMapping("/profile") public String update(@Valid @ModelAttribute("form") ProfileForm form, BindingResult errors,
            Authentication auth, Model model, RedirectAttributes flash) {
        if (errors.getSuppressedFields().length > 0) errors.reject("fields", "Only your name and phone number can be changed here.");
        if (!errors.hasErrors()) {
            try { accounts.updateProfile(auth.getName(), form); }
            catch (IllegalArgumentException ex) { errors.reject("profile", ex.getMessage()); }
        }
        if (errors.hasErrors()) { model.addAttribute("profile", accounts.profile(auth.getName())); return "profile"; }
        flash.addFlashAttribute("success", "Your profile has been updated."); return "redirect:/profile";
    }
    @GetMapping("/profile/password") public String password(Model model) { model.addAttribute("form", new PasswordChangeForm()); return "password"; }
    @PostMapping("/profile/password") public String change(@Valid @ModelAttribute("form") PasswordChangeForm form,
            BindingResult errors, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        if (errors.hasErrors()) return "password";
        try { accounts.changePassword(auth.getName(), form); }
        catch (IllegalArgumentException ex) { errors.reject("password", ex.getMessage()); return "password"; }
        sessions.expire(auth.getName()); new SecurityContextLogoutHandler().logout(request, response, auth);
        return "redirect:/login?changed";
    }
}
