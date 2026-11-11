package com.example.e_hospital.management.prototype.controller;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorPageController implements ErrorController {
    @RequestMapping("/error") String error(HttpServletRequest request,Model model){
        Object code=request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);int status=code instanceof Integer?(Integer)code:500;
        model.addAttribute("code",status);model.addAttribute("heading",status==404?"This page isn’t on the rounds.":"We couldn’t complete that request.");
        model.addAttribute("message",status==404?"The page may have moved, or the address may be incorrect.":"Your workspace is still here. Return to the dashboard and try again.");return "ui/error";
    }
}
