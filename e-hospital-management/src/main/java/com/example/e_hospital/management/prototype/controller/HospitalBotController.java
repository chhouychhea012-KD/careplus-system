package com.example.e_hospital.management.prototype.controller;

import com.example.e_hospital.management.prototype.service.HospitalBotService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
public class HospitalBotController {
    private final HospitalBotService bot;
    public HospitalBotController(HospitalBotService bot){this.bot=bot;}
    @PostMapping("/api/telegram/webhook") Object webhook(@RequestHeader(value="X-Telegram-Bot-Api-Secret-Token",required=false)String secret,@RequestBody Map<String,Object> update){return bot.webhook(secret,update);}
    private void admin(HttpSession s){if(!DemoAccess.admin(Objects.toString(s.getAttribute("demoRole"),"")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only administrators can test the bot.");}
    @GetMapping("/api/telegram/bot-status") Object status(HttpSession s){admin(s);return bot.status();}
    @PostMapping("/api/telegram/bot-preview") Object preview(@RequestBody Map<String,String> b,HttpSession s){admin(s);return bot.preview(b.getOrDefault("command","/help"),b.getOrDefault("language","en"));}
}
