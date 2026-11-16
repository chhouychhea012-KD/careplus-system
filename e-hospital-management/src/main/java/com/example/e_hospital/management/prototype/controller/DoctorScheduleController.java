package com.example.e_hospital.management.prototype.controller;
import com.example.e_hospital.management.prototype.service.HospitalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Controller
public class DoctorScheduleController {
    private final HospitalService service;
    public DoctorScheduleController(HospitalService service){this.service=service;}
    private String role(HttpSession s){return Objects.toString(s.getAttribute("demoRole"),"");}
    private void scope(HttpSession s,String doctor){if(!DemoAccess.admin(role(s))&&!role(s).equals("Receptionist")&&!role(s).equals("Hospital Director")&&!(role(s).equals("Doctor")&&CareController.doctorIdentity(s).equals(doctor)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Schedule access is unavailable for this role.");}
    @GetMapping("/care/schedules") String page(HttpSession s,Model m){scope(s,CareController.doctorIdentity(s));m.addAttribute("role",role(s));m.addAttribute("doctorId",CareController.doctorIdentity(s));return "ui/schedules";}
    @GetMapping("/api/care/schedule") @ResponseBody Object schedule(@RequestParam String doctor,@RequestParam String date,HttpSession s){scope(s,doctor);return Map.of("schedule",service.doctorSchedule(doctor,date),"slots",service.availableSlots(doctor,date));}
    @PostMapping("/api/care/schedule") @ResponseBody Object save(@RequestBody Map<String,String> b,HttpSession s){if(!DemoAccess.admin(role(s)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only administrators can change doctor schedules.");return service.saveDoctorSchedule(b,Objects.toString(s.getAttribute("demoUser"),""));}
}
