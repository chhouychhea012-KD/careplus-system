package com.example.e_hospital.management.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class SharedModelAdvice {
    @ModelAttribute
    public void identity(Authentication authentication, Model model) {
        boolean signedIn = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("signedIn", signedIn);
        model.addAttribute("currentUsername", signedIn ? authentication.getName() : "Guest");
        model.addAttribute("administrator", signedIn && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }
}
