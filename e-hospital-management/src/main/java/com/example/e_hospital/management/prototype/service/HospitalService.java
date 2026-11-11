package com.example.e_hospital.management.prototype.service;
import com.example.e_hospital.management.prototype.model.RecordPage;
import java.util.*;

/** Replace this implementation with Oracle-backed repositories in the persistence phase. */
public interface HospitalService {
    List<String> availableSlots(String doctor,String date);
    Map<String,Object> careContext(String patient,String encounter);
    Map<String,String> careAction(String patient,String encounter,String action,Map<String,String> values,String actor);
    Map<String,Object> mobileContext(String role,String identity);
    Map<String,String> checkInToken(String appointment,String patient,String token,String actor);
    /** Derived patient journey for an appointment (APT-) or encounter (VIS-): steps, owners and the next action. */
    Map<String,Object> journey(String appointmentOrEncounter);
    /** Calls the highest-priority waiting patient for a doctor (and optional station). */
    Map<String,String> callNext(String doctor,String station,String actor);
    /** Staff scan of a patient's appointment ticket code. Single use. */
    Map<String,String> redeemCheckInTicket(String token,String actor);
    /** Today's hospital arrival code shown on the reception QR poster; rotate=true replaces it. */
    Map<String,String> arrivalCode(boolean rotate);
    /** Patient self check-in after scanning the hospital arrival QR. */
    Map<String,String> arrivalCheckIn(String patient,String appointment,String code,String actor);
    List<Map<String,String>> checkInHistory(String appointmentOrPatient);
    RecordPage list(String module,String search,String status,String filter,String from,String to,int page,int size);
    Map<String,String> get(String module,String id);
    Map<String,String> save(String module,String id,Map<String,String> values,String actor);
    void delete(String module,String id,String actor);
    Map<String,String> transition(String module,String id,String action,Map<String,String> values,String actor);
    Map<String,List<Map<String,String>>> references();
    Map<String,Object> dashboard(String from,String to);
    Map<String,Object> details(String module,String id);
    byte[] export(String module,String search,String status,String filter,String from,String to);
    String attach(String module,String id,String name,byte[] bytes,String contentType,String actor);
    Map<String,Object> attachment(String id);
    Map<String,Object> importMedicines(List<Map<String,String>> rows,String actor);
}
