package com.example.e_hospital.management.prototype.controller;
import java.util.*;
/** Server-side demo capabilities. Demo login remains a synthetic preview, not hospital authentication. */
public final class DemoAccess {
    private DemoAccess(){}
    public static boolean admin(String role){return List.of("Administrator","Hospital Administrator").contains(role);}
    public static Set<String> stations(String role){return switch(role){
        case "Laboratory Staff"->Set.of("Laboratory","Imaging");
        case "Imaging Staff"->Set.of("Imaging");
        case "Pharmacist"->Set.of("Pharmacy");
        case "Cashier"->Set.of("Payment");
        default->Set.of();};}
    public static Set<String> modules(String role){return switch(role){
        case "Doctor"->Set.of("patients","appointments","doctors","departments","visits","queues","emr","consultations","laboratory","imaging","pharmacy","notifications","lab-tests","medicines","report-templates");
        case "Nurse"->Set.of("patients","appointments","doctors","departments","facilities","opd","visits","queues","emr","consultations","notifications");
        case "Receptionist"->Set.of("patients","appointments","doctors","departments","opd","visits","queues","insurance","notifications");
        case "Laboratory Staff"->Set.of("patients","laboratory","imaging","lab-tests","report-templates","notifications","reports");
        case "Imaging Staff"->Set.of("patients","imaging","report-templates","notifications","reports");
        case "Hospital Director"->Set.of("departments","doctors","staff","facilities","reports","billing","payments","audit","notifications");
        case "Pharmacist"->Set.of("pharmacy","medicines","stock","procurement","medicine-categories","brands","units","stock-locations","pharmacy-locations","suppliers","manufacturers","notifications","reports");
        case "Cashier"->Set.of("patients","billing","payments","insurance","billable-services","insurance-providers","notifications","reports");
        default->Set.of();};}
}
