package com.example.e_hospital.management.prototype.controller;

import com.example.e_hospital.management.prototype.service.HospitalService;
import com.example.e_hospital.management.prototype.service.TelegramAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Telegram Mini App entry, sign-in and administration. A QR code or deep link only opens the app; access needs verified initData + a linked account. */
@Controller
public class TelegramController {
    private final TelegramAccessService telegram;private final HospitalService hospital;
    public TelegramController(TelegramAccessService telegram,HospitalService hospital){this.telegram=telegram;this.hospital=hospital;}
    private static String role(HttpSession s){return String.valueOf(s.getAttribute("demoRole"));}
    private static void admin(HttpSession s){if(!DemoAccess.admin(role(s)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only administrators can configure Telegram.");}
    private static String base(HttpServletRequest r){return r.getScheme()+"://"+r.getServerName()+(List.of(80,443).contains(r.getServerPort())?"":":"+r.getServerPort());}

    @GetMapping("/settings/telegram") String page(HttpSession s,Model m){admin(s);m.addAttribute("role",role(s));return "ui/telegram";}
    @GetMapping("/api/telegram/settings") @ResponseBody Map<String,Object> settings(HttpSession s,HttpServletRequest r){
        if(!List.of("Administrator","Hospital Administrator","Receptionist").contains(role(s)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Telegram settings are unavailable for this role.");
        var out=new LinkedHashMap<String,Object>(telegram.settings());var arrival=hospital.arrivalCode(false);
        String local=Objects.toString(out.get("publicUrl"),"").isBlank()?base(r):(String)out.get("publicUrl");
        out.put("deepLinks",telegram.deepLinks(arrival.get("code"),local));out.put("arrival",arrival);out.put("localBase",base(r));
        out.put("accounts",telegram.linkedAccounts());out.put("outbox",telegram.outbox());return out;
    }
    @PostMapping("/api/telegram/settings") @ResponseBody Object save(@RequestBody Map<String,String> b,HttpSession s){admin(s);return telegram.updateSettings(b);}
    @PostMapping("/api/telegram/link-code") @ResponseBody Object linkCode(@RequestBody Map<String,String> b,HttpSession s){
        String r=Objects.toString(b.get("role"),""),id=Objects.toString(b.get("identity"),"");
        if(r.equals("Doctor")){admin(s);hospital.get("doctors",id);}
        else{if(!List.of("Administrator","Hospital Administrator","Receptionist").contains(role(s)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Reception issues patient link codes after checking identity.");hospital.get("patients",id);}
        return telegram.createLinkCode(r,id,String.valueOf(s.getAttribute("demoUser")));
    }
    @PostMapping("/api/telegram/unlink") @ResponseBody Object unlink(@RequestBody Map<String,String> b,HttpSession s){admin(s);telegram.unlink(Objects.toString(b.get("identity"),""));return Map.of("message","Telegram link removed.");}

    // ---- Public entry used by the Telegram Mini App ----------------------------------------------------------------
    @GetMapping("/tg") String entry(Model m){m.addAttribute("configured",telegram.tokenConfigured());m.addAttribute("appMode",telegram.integration()?"integration":"demo");return "ui/tg";}
    @PostMapping("/api/telegram/session") @ResponseBody Map<String,String> session(@RequestBody Map<String,String> b,HttpServletRequest req){
        var verified=telegram.verify(b.get("initData"),req.getSession().getId());var account=telegram.linkedAccount(verified.get("telegramUser"));
        if(account==null){var s=req.getSession();s.setAttribute("tgPending",verified.get("telegramUser"));s.setAttribute("tgFirstName",verified.get("firstName"));s.setAttribute("tgStart",verified.get("startParam"));return Map.of("status","link-required","firstName",verified.get("firstName"));}
        return signIn(req,account,verified.get("startParam"));
    }
    @PostMapping("/api/telegram/link") @ResponseBody Map<String,String> link(@RequestBody Map<String,String> b,HttpServletRequest req){
        var s=req.getSession(false);Object pending=s==null?null:s.getAttribute("tgPending");
        if(pending==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Open CarePlus from Telegram before entering a link code.");
        var account=telegram.redeemLinkCode(b.get("code"),pending.toString(),Objects.toString(s.getAttribute("tgFirstName"),""));
        return signIn(req,account,Objects.toString(s.getAttribute("tgStart"),""));
    }
    @PostMapping("/api/telegram/notifications") @ResponseBody Object notifications(@RequestBody Map<String,String> b,HttpSession s){
        Object user=s.getAttribute("telegramUser");if(user==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Telegram notifications are available after opening CarePlus from Telegram.");
        telegram.setNotifications(user.toString(),"true".equals(b.get("enabled")));return Map.of("message","Notification preference saved.");
    }
    private static Map<String,String> signIn(HttpServletRequest req,Map<String,String> account,String start){
        var old=req.getSession();for(String k:List.of("tgPending","tgFirstName","tgStart"))old.removeAttribute(k);req.changeSessionId();var s=req.getSession();
        s.setAttribute("demoUser","telegram:"+account.get("telegramUser"));s.setAttribute("demoRole",account.get("role"));s.setAttribute("authSource","telegram");s.setAttribute("telegramUser",account.get("telegramUser"));
        if(account.get("role").equals("Patient"))s.setAttribute("demoPatient",account.get("identity"));else s.setAttribute("demoDoctor",account.get("identity"));
        String redirect=account.get("role").equals("Patient")?"/mini/patient":"/mini/doctor";
        if(account.get("role").equals("Patient")&&start!=null&&start.startsWith("arrive-"))redirect+="?arrival="+start.substring(7).replaceAll("[^A-Za-z0-9]","");
        return Map.of("status","signed-in","role",account.get("role"),"redirect",redirect);
    }
}
