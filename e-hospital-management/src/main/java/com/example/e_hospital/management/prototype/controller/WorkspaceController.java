package com.example.e_hospital.management.prototype.controller;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.HospitalService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.*;

@Controller
public class WorkspaceController {
    private static final String MODULE_PATH="/{module:patients|appointments|doctors|departments|facilities|opd|visits|queues|emr|consultations|laboratory|imaging|pharmacy|medicines|stock|procurement|billing|payments|insurance|staff|access|notifications|reports|settings|audit|specialties|branches|buildings|floors|wards|rooms|room-types|lab-tests|report-templates|pharmacy-locations|medicine-categories|brands|units|stock-locations|suppliers|manufacturers|billable-services|insurance-providers|shifts|leave-requests|attendance|notification-templates|profile|appearance|change-password|queue-display}";
    public static final List<String> ROLES=List.of("Administrator","Hospital Administrator","Hospital Director","Receptionist","Doctor","Nurse","Laboratory Staff","Imaging Staff","Pharmacist","Cashier","Patient");
    private final ModuleCatalog catalog;private final com.example.e_hospital.management.prototype.service.TelegramAccessService telegram;
    public WorkspaceController(ModuleCatalog catalog,com.example.e_hospital.management.prototype.service.TelegramAccessService telegram){this.catalog=catalog;this.telegram=telegram;}
    @GetMapping({"/login","/forgot-password","/reset-password"}) String login(HttpServletRequest req,Model model){model.addAttribute("mode",req.getRequestURI().substring(req.getContextPath().length()+1));model.addAttribute("roles",ROLES);return "ui/login";}
    @PostMapping("/login") String signIn(@RequestParam String email,@RequestParam String role,@RequestParam(defaultValue="PAT-0001")String patientIdentity,@RequestParam(defaultValue="")String next,HttpServletRequest req){
        if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")||!ROLES.contains(role))return "redirect:/login?error";
        if(role.equals("Patient")&&!List.of("PAT-0001","PAT-0018").contains(patientIdentity))return "redirect:/login?error";
        // Integration mode: patients must come through Telegram sign-in; synthetic patient identities are refused.
        if(role.equals("Patient")&&telegram.integration())return "redirect:/login?error";req.getSession().setAttribute("demoPatient",patientIdentity);req.changeSessionId();req.getSession().setAttribute("demoUser",email);req.getSession().setAttribute("demoRole",role);if(role.equals("Doctor"))req.getSession().setAttribute("demoDoctor","DOC-0001");// Only same-site Mini App paths are accepted as a return target (no open redirect).
        if(next.matches("^/mini/(patient|doctor)(\\?[A-Za-z0-9=&%_-]{0,120})?$")&&(next.startsWith("/mini/patient")==role.equals("Patient")))return "redirect:"+next;
        return role.equals("Patient")?"redirect:/mini/patient":"redirect:/";
    }
    @PostMapping("/logout") String logout(HttpServletRequest req){req.getSession().invalidate();return "redirect:/login?logout";}
    @GetMapping({"/overview",MODULE_PATH,MODULE_PATH+"/{id}"}) String page(@PathVariable(required=false) String module,@PathVariable(required=false) String id,HttpServletRequest req,Model model){
        String key=module==null||module.equals("dashboard")?"dashboard":module;
        if(!List.of("dashboard","profile","appearance","change-password","queue-display").contains(key))catalog.get(key);
        model.addAttribute("modules",catalog.all());model.addAttribute("module",key);model.addAttribute("selectedId",id==null?"":id);
        model.addAttribute("parentModule",catalog.all().stream().filter(m->m.key().equals(key)).map(m->m.parent()).findFirst().orElse(""));
        model.addAttribute("pageTitle",switch(key){case "dashboard"->"Hospital analytics";case "profile"->"My profile";case "appearance"->"Appearance";case "change-password"->"Change password";case "queue-display"->"Live queue display";default->catalog.get(key).title();});
        model.addAttribute("roles",ROLES);model.addAttribute("role",req.getSession().getAttribute("demoRole"));model.addAttribute("user",req.getSession().getAttribute("demoUser"));
        return "ui/workspace";
    }
    @Configuration static class DemoConfiguration implements WebMvcConfigurer {
        private final com.example.e_hospital.management.prototype.service.TelegramAccessService telegram;
        DemoConfiguration(com.example.e_hospital.management.prototype.service.TelegramAccessService telegram){this.telegram=telegram;}
        @Bean org.springframework.security.core.userdetails.UserDetailsService demoUsers(){return username->{throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Demonstration login does not store credentials");};}
        @Bean SecurityFilterChain demoSecurity(HttpSecurity http)throws Exception {
            // These pages demonstrate staff experiences; this is deliberately not real authentication.
            return http.csrf(c->c.ignoringRequestMatchers("/api/telegram/webhook")).authorizeHttpRequests(a->a.anyRequest().permitAll()).formLogin(f->f.disable()).httpBasic(b->b.disable()).logout(l->l.disable())
                .headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny())).build();
        }
        @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
            @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler)throws Exception {
                if(req.getSession(false)!=null&&req.getSession(false).getAttribute("demoUser")!=null){
                    String path=req.getRequestURI();String role=String.valueOf(req.getSession().getAttribute("demoRole"));
                    boolean denied=telegram.integration()&&path.equals("/api/role");
                    if(role.equals("Hospital Director")&&path.startsWith("/api/")&&!req.getMethod().equals("GET")&&!path.equals("/api/role")&&!path.equals("/api/profile"))denied=true;
                    if(role.equals("Patient"))denied=!(path.startsWith("/ui/")||path.startsWith("/api/care/mobile")||path.startsWith("/api/care/booking")||path.startsWith("/api/care/slots")||path.startsWith("/api/care/checkin")||path.startsWith("/api/care/patient-appointment")||path.startsWith("/api/care/telegram/")||path.startsWith("/api/care/journey")||path.startsWith("/api/care/arrival-checkin")||path.equals("/api/telegram/notifications")||path.equals("/mini/patient")||path.equals("/logout"));
                    if(path.startsWith("/api/records/")&&!List.of("Administrator","Hospital Administrator").contains(role)&&!req.getMethod().equals("GET")) {
                        String module=path.substring(13).split("/")[0];
                        var allowed=switch(role){case "Doctor"->List.of("patients","appointments","visits","queues","emr","consultations","laboratory","imaging","pharmacy");case "Nurse"->List.of("patients","opd","queues","emr");case "Receptionist"->List.of("patients","appointments","opd","queues");case "Laboratory Staff"->List.of("laboratory","imaging");case "Imaging Staff"->List.of("imaging");case "Pharmacist"->List.of("pharmacy","medicines","stock","procurement");case "Cashier"->List.of("billing","payments","insurance");default->List.<String>of();};
                        denied|=!allowed.contains(module);
                        if((path.endsWith("/actions")||req.getMethod().equals("PUT"))&&role.equals("Doctor")&&List.of("laboratory","imaging","pharmacy").contains(module))denied=true;
                    }
                    if((path.startsWith("/api/records/")||path.startsWith("/api/export/"))&&!DemoAccess.admin(role)){
                        String module=path.substring(path.startsWith("/api/records/")?13:12).split("/")[0];denied|=!DemoAccess.modules(role).contains(module);
                        if(role.equals("Pharmacist")&&module.equals("pharmacy")&&req.getMethod().equals("POST")&&!path.endsWith("/actions"))denied=true;
                    }
                    if(path.equals("/api/records/profile/avatar/files")&&!role.equals("Patient"))denied=false;
                    if(denied){res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"message\":\"This action is unavailable for your demo role.\"}");return false;}
                    return true;
                }
                if(req.getRequestURI().substring(req.getContextPath().length()).startsWith("/api/")){res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Your demo session expired. Sign in again.\"}");}
                else{String p=req.getRequestURI();res.sendRedirect(p.startsWith("/mini/")&&req.getQueryString()!=null&&req.getQueryString().length()<120?"/login?next="+java.net.URLEncoder.encode(p+"?"+req.getQueryString(),java.nio.charset.StandardCharsets.UTF_8):p.startsWith("/mini/")?"/login?next="+java.net.URLEncoder.encode(p,java.nio.charset.StandardCharsets.UTF_8):"/login");}return false;
            }
        }).addPathPatterns("/**").excludePathPatterns("/login","/forgot-password","/reset-password","/ui/**","/favicon.ico","/error","/tg","/api/telegram/session","/api/telegram/link","/api/telegram/webhook");}
    }
}
