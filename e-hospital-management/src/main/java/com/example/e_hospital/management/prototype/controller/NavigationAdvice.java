package com.example.e_hospital.management.prototype.controller;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.*;

/**
 * One role-based navigation for every server-rendered page (workspace modules and clinical pages alike).
 * The menu only shows where a role usually works; the server still enforces every permission on each request.
 */
@ControllerAdvice
public class NavigationAdvice {
    public record NavItem(String label, String icon, String href, boolean active) {}
    public record NavGroup(String label, List<NavItem> items, boolean collapsible) {}

    private final ModuleCatalog catalog;
    public NavigationAdvice(ModuleCatalog catalog){this.catalog=catalog;}

    @ModelAttribute("navGroups")
    public List<NavGroup> navGroups(HttpServletRequest req){
        HttpSession s=req.getSession(false);
        if(s==null||s.getAttribute("demoRole")==null)return List.of();
        String role=String.valueOf(s.getAttribute("demoRole"));
        String doctor=Objects.toString(s.getAttribute("demoDoctor"),"DOC-0001");
        String current=req.getRequestURI()+(req.getQueryString()==null?"":"?"+req.getQueryString());
        return build(role,doctor,current);
    }

    @ModelAttribute("navRole")
    public String navRole(HttpServletRequest req){HttpSession s=req.getSession(false);return s==null?"":Objects.toString(s.getAttribute("demoRole"),"");}

    @ModelAttribute("navUser")
    public String navUser(HttpServletRequest req){HttpSession s=req.getSession(false);return s==null?"":Objects.toString(s.getAttribute("demoUser"),"");}

    /** Package-visible for tests: the primary links per role, then a collapsed "More modules" group. */
    List<NavGroup> build(String role,String doctor,String current){
        List<String[]> primary=switch(role){
            case "Hospital Director"->List.of(i("Dashboard","layout-dashboard","/"),i("Hospital Analytics","chart-line","/overview"),i("Reports","chart-column","/reports"),i("Departments","building-2","/departments"),i("Staff","id-card","/staff"),i("Audit Logs","shield-check","/audit"));
            case "Imaging Staff"->List.of(i("Dashboard","layout-dashboard","/"),i("Imaging Worklist","scan-line","/imaging"),i("Schedule","calendar-days","/imaging?status=Requested"),i("Reports","file-text","/imaging?status=Reported"),i("Queue","list-ordered","/care/queue"));
            case "Doctor"->List.of(
                i("Dashboard","layout-dashboard","/"),i("My Queue","list-ordered","/care/queue"),
                i("My Appointments","calendar-days","/appointments?doctor="+doctor),i("My Patients","users","/visits?doctor="+doctor),
                i("Consultations","stethoscope","/consultations"),i("Orders and Results","flask-conical","/laboratory?doctor="+doctor),
                i("Medical Certificates","file-badge","/care/certificates"),i("Tasks","list-checks","/care/tasks"),i("Doctor schedules","calendar-clock","/care/schedules"));
            case "Receptionist"->List.of(
                i("Dashboard","layout-dashboard","/"),i("Patient Registration","user-plus","/patients"),
                i("Appointments","calendar-days","/appointments"),i("Check-In","log-in","/care/checkin"),i("Queue Management","list-ordered","/care/queue"),i("Doctor schedules","calendar-clock","/care/schedules"));
            case "Nurse"->List.of(
                i("Dashboard","layout-dashboard","/"),i("Triage","activity","/care/queue"),i("Vital Signs","heart-pulse","/visits?status=Waiting"),
                i("Assigned Patients","users","/visits"),i("Queue","list-ordered","/queues"));
            case "Laboratory Staff"->List.of(
                i("Dashboard","layout-dashboard","/"),i("Laboratory Worklist","flask-conical","/laboratory"),
                i("Sample Collection","test-tube-diagonal","/laboratory?status=Ordered"),i("Results","badge-check","/laboratory?status=Processing"),
                i("Imaging Worklist","scan-line","/imaging"));
            case "Pharmacist"->List.of(
                i("Dashboard","layout-dashboard","/"),i("Prescriptions","pill","/pharmacy?status=Pending"),
                i("Dispensing","package-check","/pharmacy?status=Reviewed"),i("Medicine Stock","boxes","/stock"));
            case "Cashier"->List.of(
                i("Dashboard","layout-dashboard","/"),i("Invoices","receipt-text","/billing"),
                i("Awaiting Payment","file-text","/billing?status=Issued"),i("Payments","credit-card","/payments"));
            default->List.of( // Administrator and Hospital Administrator
                i("Dashboard","layout-dashboard","/"),i("Staff","id-card","/staff"),i("Departments","building-2","/departments"),
                i("Hospital Configuration","hospital","/facilities"),i("Reports","chart-column","/reports"),
                i("Roles and Permissions","shield-check","/access"),i("Settings","settings","/settings"));
        };
        List<String[]> operations=DemoAccess.admin(role)?List.of(
            i("Patients","users","/patients"),i("Appointments","calendar-days","/appointments"),i("Check-In Desk","log-in","/care/checkin"),
            i("Queue","list-ordered","/care/queue"),i("Doctor schedules","calendar-clock","/care/schedules"),i("Hospital Analytics","chart-line","/overview"),i("Telegram Mini App","send","/settings/telegram")):List.of();
        Set<String> taken=new HashSet<>();
        for(String[] x:primary)taken.add(path(x[2]));for(String[] x:operations)taken.add(path(x[2]));
        List<String[]> more=new ArrayList<>();
        Set<String> allowed=DemoAccess.admin(role)?null:DemoAccess.modules(role);
        for(var m:catalog.all()){
            if(!m.parent().isEmpty())continue; // child catalogs open from their parent module page
            if(allowed!=null&&!allowed.contains(m.key()))continue;
            if(taken.contains("/"+m.key()))continue;
            more.add(i(m.title(),m.icon(),"/"+m.key()));
        }
        String activeHref=activeHref(current,primary,operations,more);
        List<NavGroup> groups=new ArrayList<>();
        groups.add(new NavGroup(DemoAccess.admin(role)?"Administration":"My work",items(primary,activeHref),false));
        if(!operations.isEmpty())groups.add(new NavGroup("Hospital operations",items(operations,activeHref),false));
        if(!more.isEmpty())groups.add(new NavGroup("More modules",items(more,activeHref),true));
        return groups;
    }

    private static String[] i(String label,String icon,String href){return new String[]{label,icon,href};}
    private static String path(String href){int q=href.indexOf('?');return q<0?href:href.substring(0,q);}
    private static List<NavItem> items(List<String[]> list,String activeHref){List<NavItem> out=new ArrayList<>();for(String[] x:list)out.add(new NavItem(x[0],x[1],x[2],x[2].equals(activeHref)));return out;}

    /** Exact URL match wins (e.g. a filtered worklist); otherwise the first link with the same path; clinical sub-pages map to their list. */
    static String activeHref(String current,List<String[]>... lists){
        String cur=current.equals("/dashboard")?"/":current;
        String curPath=path(cur);
        if(curPath.startsWith("/care/appointments/"))curPath="/appointments";
        if(curPath.startsWith("/care/patients/"))curPath="/care/queue";
        if(curPath.equals("/overview"))curPath="/overview";
        for(List<String[]> l:lists)for(String[] x:l)if(x[2].equals(cur))return x[2];
        // Prefer an unfiltered link for the bare path, then any filtered link to the same path.
        for(List<String[]> l:lists)for(String[] x:l)if(x[2].equals(curPath))return x[2];
        if(!cur.contains("?"))for(List<String[]> l:lists)for(String[] x:l)if(path(x[2]).equals(curPath))return x[2];
        if(cur.contains("?"))for(List<String[]> l:lists)for(String[] x:l)if(path(x[2]).equals(curPath))return x[2];
        return "";
    }
}
