package com.example.e_hospital.management.prototype.service;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.model.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.charset.StandardCharsets;

@Service
public class InMemoryHospitalService implements HospitalService {
    private final ModuleCatalog catalog;
    private final Map<String,LinkedHashMap<String,Map<String,String>>> store=new LinkedHashMap<>();
    private final Map<String,List<Map<String,String>>> histories=new HashMap<>();
    private final Map<String,Map<String,Object>> files=new HashMap<>();
    private final Map<String,Map<String,String>> careDocuments=new LinkedHashMap<>();
    private final Map<String,Map<String,String>> checkInTokens=new HashMap<>();
    private final List<Map<String,String>> checkIns=new ArrayList<>();
    private final java.security.SecureRandom random=new java.security.SecureRandom();
    private String arrivalCode;private LocalDate arrivalDay;
    private java.util.function.BiConsumer<String,Map<String,String>> events=(type,facts)->{};
    /** Receives generic care events (appointment, queue-called, visit-completed, follow-up) for opt-in notifications. */
    public void onEvent(java.util.function.BiConsumer<String,Map<String,String>> sink){this.events=sink;}
    private void emit(String type,String patient,String... kv){var m=new LinkedHashMap<String,String>();m.put("patient",patient);for(int i=0;i<kv.length;i+=2)m.put(kv[i],kv[i+1]);try{events.accept(type,m);}catch(RuntimeException ignored){}}
    private final AtomicLong sequence=new AtomicLong(1000);
    /** Statuses added for live workflows only; seeds keep their original rotation so demo IDs stay stable. */
    private static final Map<String,List<String>> SEED_EXCLUDED=Map.of("queues",List.of("In service","On hold"),"laboratory",List.of("Cancelled"),"imaging",List.of("Cancelled"));
    public InMemoryHospitalService(ModuleCatalog catalog) {
        this.catalog=catalog;
        for(var m:catalog.all()) store.put(m.key(),new LinkedHashMap<>());
        for(var m:catalog.all()) {
            for(int i=0;i<m.names().size();i++) {
                Map<String,String> row=new LinkedHashMap<>();
                row.put("id",m.prefix()+"-"+String.format("%04d",i+1));
                var seedStatuses=m.statuses().stream().filter(st->!(SEED_EXCLUDED.getOrDefault(m.key(),List.of()).contains(st))).toList();row.put("status",seedStatuses.get(i % seedStatuses.size()));
                row.put("createdAt",LocalDate.now().minusDays(i).toString());
                row.put("version","1");
                for(var f:m.fields()) row.put(f.key(),seed(m,f,i));
                row.put("name",m.names().get(i));
                store.get(m.key()).put(row.get("id"),row);
                history(row.get("id"),"Synthetic record initialized","Demo data provider");
            }
        }
        for(var b:store.get("billing").values())if(List.of("Paid","Partially paid").contains(b.get("status")))b.put("status","Issued");
        // References are stable entity IDs, never patient/doctor display strings.
        for(var m:catalog.all()) { int i=0; for(var row:store.get(m.key()).values()) {
            for(var f:m.fields()) if(f.type().equals("ref")) {
                var refs=new ArrayList<>(store.get(f.source()).keySet()); row.put(f.key(),refs.get(i%refs.size()));
            }
            if(row.containsKey("doctor") && row.containsKey("department")) row.put("department",raw("doctors",row.get("doctor")).get("department"));
            if(m.key().equals("payments")) {String[] ids={"INV-0002","INV-0003","INV-0004","INV-0007"};row.put("invoice",ids[i/2]);var invoice=raw("billing",row.get("invoice"));row.put("patient",invoice.get("patient"));row.put("amount",i/2==2?money(total(invoice)):money(70+(i/2)*15));row.put("notes","Synthetic receipt. No real payment was processed.");}
            if(m.key().equals("stock")) row.put("quantity",i==1?"12":String.valueOf(150+i*35));
            if(m.key().equals("facilities") && !row.get("status").equals("Occupied")) row.put("patient","");
            if(m.key().equals("settings")) row.put("value",List.of("CarePlus General Hospital","Central campus","English","dd MMM yyyy","Asia/Bangkok","In-app only","Not connected — future integration","Not connected — demonstration").get(i));
            i++;
        }}
        for(var m:catalog.all()) for(var row:store.get(m.key()).values()) if(row.containsKey("doctor")&&row.containsKey("department")) row.put("department",raw("doctors",row.get("doctor")).get("department"));
        for(String module:List.of("queues","emr","consultations","laboratory","imaging","pharmacy","billing"))for(var row:store.get(module).values()) {
            store.get("visits").values().stream().filter(v->Objects.equals(v.get("patient"),row.get("patient"))&&(!row.containsKey("doctor")||Objects.equals(v.get("doctor"),row.get("doctor")))).findFirst().ifPresent(v->{row.put("encounter",v.get("id"));if(module.equals("queues")){row.put("station","Consultation");row.put("queuedAt",LocalDateTime.now().minusMinutes(20).toString());v.put("queueNumber",row.get("name"));}});
        }
    }
    private String seed(ModuleDefinition m,FieldDefinition f,int i) {
        String k=f.key();
        if(f.type().equals("file")||f.type().equals("ref")) return "";
        if(!f.options().isEmpty()) return f.options().get(i%f.options().size());
        if(f.type().equals("date")) {
            if(k.equals("dateOfBirth")) return LocalDate.now().minusYears(24+i*2).minusDays(i*13L).toString();
            if(k.equals("expiry")) return LocalDate.now().plusMonths(i==2?-1:6+i).toString();
            if(k.equals("dueDate")||k.equals("expectedDate")||k.equals("endDate")) return LocalDate.now().plusDays(7).toString();
            if(k.startsWith("leave")) return "";
            return LocalDate.now().minusDays(i<6?0:i-5).toString();
        }
        if(f.type().equals("time")) return k.equals("endTime")?"17:00":String.format("%02d:%02d",9+i/4,(i%4)*15);
        if(f.type().equals("email")) return "demo"+(i+1)+"@example.test";
        if(f.type().equals("tel")) return "+1 202 555 "+String.format("%04d",100+i);
        if(f.type().equals("number")) return switch(k) {
            case "pulse" -> "72"; case "temperature" -> "36.6"; case "weight" -> "68";
            case "tax" -> "7"; case "discount" -> "0"; case "quantity" -> "20"; case "minimum" -> "30";
            case "duration" -> "7"; case "coverage" -> "5000"; case "capacity" -> "60";
            case "price","unitCost" -> "2.50"; case "experience" -> "12"; default -> String.valueOf(120+i*25);
        };
        return switch(k) {
            case "bloodPressure" -> "120/80"; case "allergies" -> i==1?"Penicillin":"No known allergies";
            case "conditions" -> i%3==0?"Hypertension — routine monitoring":"No chronic conditions reported";
            case "diagnosis" -> "Routine assessment; synthetic clinical scenario";
            case "notes" -> "Demonstration record. All information is synthetic and for workflow preview only.";
            case "treatment" -> "Monitor symptoms. Schedule a follow-up review in 2 weeks.";
            case "address" -> (100+i)+" Example Avenue, Demo City";
            case "emergencyContact" -> "Demo emergency contact "+(i+1);
            case "specialty" -> "General Medicine"; case "qualification" -> "MD, Board certified";
            case "schedule" -> "Monday–Friday, 09:00–12:00 and 14:00–17:00";
            case "branch" -> "Central campus"; case "building" -> "Main building"; case "floor" -> String.valueOf(1+i/4);
            case "ward" -> i<8?"General ward":"Recovery ward"; case "room","location" -> "Room "+(101+i/2);
            case "dosage" -> "1 tablet after meals"; case "generic" -> m.names().get(i).split(" ")[0];
            case "strength" -> "500 mg"; case "brand" -> "CarePlus Essentials"; case "manufacturer","supplier" -> "MedSource Demo Supplies";
            case "items" -> "Consultation | 1 | 80\nClinical services | 1 | "+(40+i*25);
            case "policy" -> "DEMO-POL-"+(1000+i); case "provider" -> "CareShield Health";
            case "indication" -> "Clinical assessment requested; synthetic example";
            case "report" -> "Technique: standard examination. Findings: no acute abnormality in this synthetic example.";
            case "impression" -> "No acute findings. Clinical correlation advised.";
            case "result" -> "Within reference range (synthetic)"; case "referenceRange" -> "4.0 – 10.0"; case "unit" -> "10^9/L";
            case "role" -> "Care coordinator"; case "recipient","actor" -> "Demo care team";
            case "message" -> "Please review the relevant record in the workspace. This is a demo notification; no message has been sent.";
            case "resource" -> "Hospital workspace"; case "description" -> "Workspace demonstration preference";
            default -> f.label()+" "+(i+1);
        };
    }
    private Map<String,String> raw(String module,String id) {
        catalog.get(module); var row=store.get(module).get(id);
        if(row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"); return row;
    }
    private Map<String,String> display(String module,Map<String,String> row) {
        var result=new LinkedHashMap<>(row);
        for(var f:catalog.get(module).fields()) if(f.type().equals("ref")) {
            var linked=store.get(f.source()).get(row.get(f.key())); result.put(f.key()+"Label",linked==null?"—":linked.get("name"));
        }
        if(module.equals("billing")) {
            double total=total(row),paid=paid(row.get("id"));
            result.put("total",money(total));result.put("paid",money(paid));result.put("balance",money(Math.max(0,total-paid)));
            if(paid>0&&!row.get("status").equals("Void")) result.put("status",paid+0.001>=total?"Paid":"Partially paid");
        }
        if(module.equals("stock")&&!row.get("status").equals("Quarantined")) {
            result.put("status",LocalDate.parse(row.get("expiry")).isBefore(LocalDate.now())?"Expired":number(row,"quantity")<=number(row,"minimum")?"Low stock":"In stock");
        }
        if(module.equals("appointments")){
            var visit=store.get("visits").get(row.get("encounter"));result.put("arrivalStatus",visit==null?"Waiting for Check-In":"Checked-In");result.put("consultationStatus",visit==null?"Not started":visit.get("status"));
            var queue=store.get("queues").values().stream().filter(q->visit!=null&&visit.get("id").equals(q.get("encounter"))).reduce((a,b)->b);result.put("queueStatus",queue.map(q->q.get("status")).orElse("—"));result.put("queueNumber",queue.map(q->q.get("name")).orElse("—"));
        }
        result.put("_actions",String.join("|",allowed(module,result)));
        return result;
    }
    @Override public synchronized RecordPage list(String module,String search,String status,String filter,String from,String to,int page,int size) {return list(module,search,status,filter,from,to,page,size,"","asc");}
    @Override public synchronized RecordPage list(String module,String search,String status,String filter,String from,String to,int page,int size,String sort,String dir) {
        catalog.get(module); size=Math.max(1,Math.min(size,100)); page=Math.max(1,page);
        List<Map<String,String>> rows=new ArrayList<>(); Map<String,Long> counts=new LinkedHashMap<>();
        for(var r:store.get(module).values()) {
            var row=display(module,r);
            String d=row.getOrDefault("date",row.get("createdAt"));
            if(!search.isBlank() && row.values().stream().noneMatch(v->v.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))) continue;
            if(!matchesFilter(row,filter)) continue;
            if(!from.isBlank()&&d.compareTo(from)<0 || !to.isBlank()&&d.compareTo(to)>0) continue;
            counts.merge(row.get("status"),1L,Long::sum);
            if(!status.isBlank()&&!status.equals(row.get("status"))) continue;
            rows.add(row);
        }
        if(sort!=null&&!sort.isBlank()&&sort.matches("[A-Za-z]{1,40}")){
            // Sort by the label shown in the table for reference fields; numbers compare numerically.
            String key=catalog.get(module).fields().stream().anyMatch(f->f.key().equals(sort)&&f.type().equals("ref"))?sort+"Label":sort;
            Comparator<Map<String,String>> cmp=(a,b)->{String x=Objects.toString(a.get(key),""),y=Objects.toString(b.get(key),"");
                try{return Double.compare(Double.parseDouble(x),Double.parseDouble(y));}catch(NumberFormatException e){return x.compareToIgnoreCase(y);}};
            if(key.equals("date"))cmp=Comparator.comparing((Map<String,String> r)->Objects.toString(r.get("date"),"")+" "+Objects.toString(r.get("time"),""));
            rows.sort("desc".equalsIgnoreCase(dir)?cmp.reversed():cmp);
        }
        int pages=Math.max(1,(int)Math.ceil(rows.size()/(double)size));page=Math.min(page,pages);
        int start=Math.min(rows.size(),(page-1)*size);
        return new RecordPage(rows.subList(start,Math.min(rows.size(),start+size)),rows.size(),page,pages,counts);
    }
    @Override public synchronized Map<String,String> get(String module,String id) { return display(module,raw(module,id)); }
    @Override public synchronized Map<String,String> save(String module,String id,Map<String,String> values,String actor) {
        if(module.equals("audit")) fail("Audit events are read-only.");
        var m=catalog.get(module); boolean create=id==null; Map<String,String> old=create?null:raw(module,id);
        if(!create && !Objects.equals(old.get("version"),values.get("version"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"This record changed. Reopen it before saving.");
        if(!create&&old.containsKey("encounter")&&values.containsKey("encounter")&&!Objects.equals(old.get("encounter"),values.get("encounter")))fail("The encounter of a linked record cannot be changed.");
        Map<String,String> row=new LinkedHashMap<>();
        for(var f:m.fields()) {
            String value=values.getOrDefault(f.key(),"").strip();
            if(value.length()>6000) fail(f.label()+" is too long.");
            if(f.required()&&value.isBlank()) fail(f.label()+" is required.");
            if(!value.isBlank()) {
                try {
                    switch(f.type()) {
                        case "date" -> LocalDate.parse(value);
                        case "time" -> LocalTime.parse(value);
                        case "number" -> { double n=Double.parseDouble(value);if(!Double.isFinite(n)||n<0||n>100000000) fail(f.label()+" must be a valid positive number."); }
                        case "email" -> { if(!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) fail("Enter a valid email address."); }
                        case "select" -> { if(!f.options().contains(value)) fail("Invalid "+f.label()+"."); }
                        case "ref" -> raw(f.source(),value);
                        case "file" -> { if(!value.startsWith("/api/files/")) fail("Upload the image before saving."); }
                    }
                } catch(DateTimeParseException|NumberFormatException ex) { fail("Enter a valid "+f.label()+"."); }
            }
            row.put(f.key(),value);
        }
        if(module.equals("patients")&&create&&store.get("patients").values().stream().anyMatch(p->p.get("name").equalsIgnoreCase(row.get("name"))&&p.get("dateOfBirth").equals(row.get("dateOfBirth"))))fail("A patient with this name and date of birth already exists. Search the patient list before registering again.");
        if(!create&&old.containsKey("encounter")&&row.containsKey("patient")&&!raw("visits",old.get("encounter")).get("patient").equals(row.get("patient")))fail("The patient of an encounter-linked record cannot be changed.");
        if(module.equals("patients")&&LocalDate.parse(row.get("dateOfBirth")).isAfter(LocalDate.now())) fail("Date of birth cannot be in the future.");
        if(row.containsKey("doctor")&&row.containsKey("department")&&!row.get("doctor").isBlank()&&!raw("doctors",row.get("doctor")).get("department").equals(row.get("department"))) fail("Select a doctor assigned to this department.");
        if(row.containsKey("endDate")&&row.getOrDefault("fromDate",row.getOrDefault("date","")).compareTo(row.get("endDate"))>0) fail("End date must be after start date.");
        if(row.containsKey("leaveEnd")&&!row.get("leaveEnd").isBlank()&&row.get("leaveStart").compareTo(row.get("leaveEnd"))>0) fail("Leave end must be after leave start.");
        if(module.equals("visits")&&create&&store.get("visits").values().stream().anyMatch(v->v.get("patient").equals(row.get("patient"))&&v.get("date").equals(row.get("date"))&&!List.of("Completed","Cancelled","Transferred").contains(v.get("status"))))fail("An active visit already exists. Open the existing visit before checking in again.");
        if(module.equals("appointments")) checkSlot(row,create?"":id);
        if(module.equals("billing")) {
            double subtotal=0;
            for(String line:row.get("items").split("\\R")) {
                String[] p=line.split("\\|"); if(p.length!=3||p[0].isBlank()) fail("Use item | quantity | unit price for each billable item.");
                try { double qty=Double.parseDouble(p[1].strip()),price=Double.parseDouble(p[2].strip());if(!Double.isFinite(qty)||!Double.isFinite(price)||qty<=0||price<0) fail("Billable quantities must be positive and prices nonnegative."); subtotal+=qty*price; } catch(NumberFormatException ex) { fail("Invalid billable item amount."); }
            }
            row.put("amount",money(subtotal));
            if(number(row,"discount")>subtotal||number(row,"tax")>100) fail("Check the discount and tax values.");
            if(!create&&paid(id)>total(row)+0.001) fail("Invoice total cannot be lower than payments already recorded.");
        }
        if(module.equals("payments")) {
            var invoice=raw("billing",row.get("invoice")); if(!invoice.get("patient").equals(row.get("patient"))) fail("The payment patient must match the invoice patient.");
            if(!create) fail("Recorded receipts are immutable. Refund and record a new payment.");
            if(!List.of("Issued","Partially paid").contains(display("billing",invoice).get("status"))) fail("Issue the invoice before recording a payment.");
            if(number(row,"amount")<=0 || number(row,"amount")>total(invoice)-paid(invoice.get("id"))+0.001) fail("Payment must be positive and cannot exceed the outstanding balance.");
        }
        if(List.of("laboratory","imaging","pharmacy","billing").contains(module))for(String key:List.of("priority","department"))if(values.containsKey(key))row.put(key,values.get(key));
        if(!create) for(String key:List.of("encounter","appointment","queueNumber","calledAt","station","checkedInAt","released","reviewedByDoctor","queuedAt","visitReason","appointmentTime","fromQueue")) if(old.containsKey(key))row.put(key,old.get(key));
        if(values.containsKey("encounter")&&!values.get("encounter").isBlank()) {
            var visit=raw("visits",values.get("encounter"));
            if(!visit.get("patient").equals(row.get("patient")))fail("Encounter does not belong to this patient.");
            row.put("encounter",visit.get("id"));
        }
        if(!create&&List.of("laboratory","imaging").contains(module)&&List.of("Reviewed","Approved").contains(old.get("status")))fail("Reviewed results are immutable. Create an amended order.");
        if(!create&&List.of("emr","consultations").contains(module)&&List.of("Final","Completed").contains(old.get("status")))fail("Signed documentation is immutable. Create an amendment.");
        row.put("id",create?m.prefix()+"-"+sequence.incrementAndGet():id);
        row.put("status",create?m.statuses().getFirst():old.get("status"));row.put("createdAt",create?LocalDate.now().toString():old.get("createdAt"));
        row.put("version",create?"1":String.valueOf(Integer.parseInt(old.get("version"))+1));
        if(!create&&old.containsKey("queueNumber")) row.put("queueNumber",old.get("queueNumber"));
        if(!create&&old.containsKey("calledAt")) row.put("calledAt",old.get("calledAt"));
        if(create)store.get(module).putFirst(row.get("id"),row);else store.get(module).put(row.get("id"),row);
        history(row.get("id"),create?"Record created":"Record updated",actor);audit(actor,create?"Created":"Updated",module,row.get("id"));
        if(create&&module.equals("appointments")&&!"Follow-up".equals(row.get("type")))emit("appointment",row.get("patient"),"date",row.get("date"),"time",row.get("time"));
        return display(module,row);
    }
    private final Map<String,Map<String,String>> scheduleOverrides=new HashMap<>();
    @Override public synchronized Map<String,String> doctorSchedule(String doctor,String date){
        var clinician=raw("doctors",doctor);LocalDate day;
        try{day=LocalDate.parse(date);}catch(Exception ex){fail("Choose a valid date.");return Map.of();}
        var out=new LinkedHashMap<>(scheduleOverrides.getOrDefault(doctor+"/"+date,Map.of("doctor",doctor,"date",date,"startTime","09:00","endTime","17:00","breakStart","12:00","breakEnd","14:00","interval","15","location","Outpatient department","closed",String.valueOf(day.getDayOfWeek().getValue()>5))));
        out.put("doctorLabel",clinician.get("name"));out.put("version",out.getOrDefault("version","0"));return out;
    }
    private List<String> scheduledSlots(String doctor,String date){
        var s=doctorSchedule(doctor,date);if("true".equals(s.get("closed"))||raw("doctors",doctor).get("status").equals("On leave"))return List.of();
        var slots=new ArrayList<String>();LocalTime end=LocalTime.parse(s.get("endTime"));int interval=Integer.parseInt(s.get("interval"));
        for(int minute=LocalTime.parse(s.get("startTime")).toSecondOfDay()/60;minute+interval<=end.toSecondOfDay()/60;minute+=interval){
            LocalTime time=LocalTime.of(minute/60,minute%60),finish=time.plusMinutes(interval);boolean overlaps=!s.get("breakStart").isBlank()&&time.isBefore(LocalTime.parse(s.get("breakEnd")))&&finish.isAfter(LocalTime.parse(s.get("breakStart")));
            if(!overlaps)slots.add(time.toString());
        }return slots;
    }
    @Override public synchronized Map<String,String> saveDoctorSchedule(Map<String,String> values,String actor){
        String doctor=required(values,"doctor"),date=required(values,"date");raw("doctors",doctor);var previous=doctorSchedule(doctor,date);
        if(!previous.get("version").equals(values.get("version")))throw new ResponseStatusException(HttpStatus.CONFLICT,"This schedule changed. Reload before saving.");
        var s=new LinkedHashMap<String,String>();s.put("doctor",doctor);s.put("date",date);
        try{
            if(LocalDate.parse(date).isBefore(LocalDate.now()))fail("Choose today or a future date.");
            LocalTime start=LocalTime.parse(required(values,"startTime")),end=LocalTime.parse(required(values,"endTime"));if(!start.isBefore(end))fail("Schedule end must be after start.");
            if(start.getSecond()!=0||end.getSecond()!=0)fail("Use schedule times in hours and minutes.");
            int interval=Integer.parseInt(required(values,"interval"));if(interval<5||interval>60||interval%5!=0)fail("Slot length must be 5 to 60 minutes in steps of 5.");
            String bs=values.getOrDefault("breakStart",""),be=values.getOrDefault("breakEnd","");
            if(!bs.isBlank()||!be.isBlank()){LocalTime b1=LocalTime.parse(bs),b2=LocalTime.parse(be);if(!b1.isBefore(b2)||b1.isBefore(start)||b2.isAfter(end))fail("Break must be inside the schedule.");}
            for(String k:List.of("startTime","endTime","interval","location"))s.put(k,required(values,k));s.put("breakStart",bs);s.put("breakEnd",be);s.put("closed",String.valueOf("true".equals(values.get("closed"))));
        }catch(DateTimeException|NumberFormatException ex){fail("Enter valid schedule dates and times.");}
        s.put("version",String.valueOf(Integer.parseInt(previous.get("version"))+1));String key=doctor+"/"+date;var old=scheduleOverrides.put(key,s);
        try{var slots=scheduledSlots(doctor,date);if(store.get("appointments").values().stream().anyMatch(a->doctor.equals(a.get("doctor"))&&date.equals(a.get("date"))&&!List.of("Completed","Cancelled","No-Show").contains(a.get("status"))&&!slots.contains(a.get("time"))))fail("Existing appointments conflict with this schedule. Reschedule them first.");}
        catch(RuntimeException ex){if(old==null)scheduleOverrides.remove(key);else scheduleOverrides.put(key,old);throw ex;}
        audit(actor,"Schedule updated","doctors",doctor);return doctorSchedule(doctor,date);
    }
    @Override public synchronized List<String> availableSlots(String doctor,String date) {
        var d=raw("doctors",doctor);LocalDate day;try{day=LocalDate.parse(date);}catch(DateTimeParseException e){fail("Choose a valid date.");return List.of();}
        if(day.isBefore(LocalDate.now()))return List.of();
        var slots=new ArrayList<String>();for(String slot:scheduledSlots(doctor,date))if(store.get("appointments").values().stream().noneMatch(a->doctor.equals(a.get("doctor"))&&date.equals(a.get("date"))&&slot.equals(a.get("time"))&&!List.of("Completed","Cancelled","No-Show").contains(a.get("status"))))slots.add(slot);
        int capacity=(int)number(raw("departments",d.get("department")),"capacity");long booked=store.get("appointments").values().stream().filter(a->d.get("department").equals(a.get("department"))&&date.equals(a.get("date"))&&!List.of("Cancelled","Completed").contains(a.get("status"))).count();return booked>=capacity?List.of():List.copyOf(slots);
    }
    private void checkSlot(Map<String,String> row,String except) {
        if(scheduleOverrides.containsKey(row.get("doctor")+"/"+row.get("date"))&&!scheduledSlots(row.get("doctor"),row.get("date")).contains(row.get("time")))fail("Choose an available time in the doctor's configured schedule.");
        for(var existing:store.get("appointments").values()) if(!existing.get("id").equals(except)&&!List.of("Cancelled","Completed").contains(existing.get("status"))&&existing.get("doctor").equals(row.get("doctor"))&&existing.get("date").equals(row.get("date"))&&existing.get("time").equals(row.get("time"))) fail("This doctor already has an appointment at that time.");
    }
    @Override public synchronized void delete(String module,String id,String actor) {
        if(List.of("audit","payments").contains(module)) fail("This record is immutable. Use its workflow actions.");
        raw(module,id);
        if(careDocuments.values().stream().anyMatch(d->id.equals(d.get("patient"))||id.equals(d.get("encounter"))))fail("This record has clinical documentation and cannot be deleted.");
        for(var rows:store.values())for(var r:rows.values())if(id.equals(r.get("encounter"))||id.equals(r.get("appointment")))fail("This record is linked to a visit.");
        for(var m:catalog.all()) for(var f:m.fields()) if(f.type().equals("ref")&&f.source().equals(module)) for(var row:store.get(m.key()).values()) if(id.equals(row.get(f.key()))) throw new ResponseStatusException(HttpStatus.CONFLICT,"This record is linked to "+m.title().toLowerCase()+". Remove or reassign its linked records first.");
        store.get(module).remove(id);histories.remove(id);files.entrySet().removeIf(e->id.equals(e.getValue().get("record")));
        audit(actor,"Deleted",module,id);
    }
    private static final Map<String,String> TARGET=Map.ofEntries(
        Map.entry("Mark no-show","No-Show"),Map.entry("Activate","Active"),Map.entry("Deactivate","Inactive"),Map.entry("Confirm","Confirmed"),Map.entry("Complete","Completed"),Map.entry("Cancel","Cancelled"),
        Map.entry("Set available","Available"),Map.entry("Set on duty","On duty"),Map.entry("Set on leave","On leave"),Map.entry("Assign bed","Occupied"),Map.entry("Release bed","Available"),Map.entry("Set maintenance","Maintenance"),
        Map.entry("Check in","Checked in"),Map.entry("Start consultation","In consultation"),Map.entry("Transfer","Transferred"),Map.entry("Call patient","Called"),Map.entry("Mark missed","Missed"),
        Map.entry("Review","Reviewed"),Map.entry("Finalize","Final"),Map.entry("Collect sample","Collected"),Map.entry("Start test","Processing"),Map.entry("Enter result","Result entered"),Map.entry("Approve","Approved"),
        Map.entry("Schedule","Scheduled"),Map.entry("Start examination","In progress"),Map.entry("Submit report","Reported"),Map.entry("Dispense","Dispensed"),Map.entry("Quarantine","Quarantined"),
        Map.entry("Submit","Submitted"),Map.entry("Place order","Ordered"),Map.entry("Receive goods","Received"),Map.entry("Issue invoice","Issued"),Map.entry("Void","Void"),Map.entry("Refund","Refunded"),
        Map.entry("Verify","Verified"),Map.entry("Submit claim","Submitted"),Map.entry("Reject","Rejected"),Map.entry("Approve leave","On leave"),Map.entry("Return to work","Active"),Map.entry("Suspend","Suspended"),
        Map.entry("Mark read","Read"),Map.entry("Mark unread","Unread"),Map.entry("Archive","Archived"),Map.entry("Restore","Ready"),Map.entry("Enable","Enabled"),Map.entry("Disable","Disabled"));
    private boolean openEncounter(String id){var v=id==null?null:store.get("visits").get(id);return v!=null&&!List.of("Completed","Cancelled","Transferred").contains(v.get("status"));}
    private List<String> allowed(String module,Map<String,String> row) {
        String s=row.get("status"); var result=new ArrayList<String>();
        for(String a:catalog.get(module).actions()) {
            boolean terminal=List.of("Completed","Cancelled","Final","Dispensed","Received","Void","Refunded","Transferred").contains(s)||(s.equals("Approved")&&!module.equals("procurement"));
            if(terminal&&!List.of("Refund","Release bed","Activate","Restore","Mark unread").contains(a)) continue;
            if(Objects.equals(TARGET.get(a),s)) continue;
            boolean ok=switch(a) {
                case "Reschedule","Confirm","Mark no-show" -> List.of("Scheduled","Confirmed").contains(s)&&!row.containsKey("encounter");
                case "Cancel" -> !(module.equals("appointments")&&openEncounter(row.get("encounter")));
                case "Collect sample" -> s.equals("Ordered"); case "Start test" -> s.equals("Collected"); case "Enter result" -> s.equals("Processing");
                case "Review" -> switch(module) {case "laboratory" -> s.equals("Result entered");case "imaging" -> s.equals("Reported");case "pharmacy" -> s.equals("Pending");default -> s.equals("Draft");};
                case "Approve" -> List.of("Reviewed","Submitted").contains(s)||(module.equals("leave-requests")&&s.equals("Pending"));
                case "Finalize" -> s.equals("Reviewed"); case "Dispense" -> s.equals("Reviewed");case "Call patient" -> s.equals("Waiting");
                case "Complete" -> switch(module) {case "queues" -> List.of("Called","In service").contains(s);case "visits" -> s.equals("In consultation");case "opd" -> s.equals("Checked in");default -> true;};
                case "Mark missed" -> s.equals("Called");case "Start consultation" -> s.equals("Waiting");case "Check in" -> s.equals("Registered");
                case "Start examination" -> s.equals("Scheduled");case "Schedule" -> s.equals("Requested");case "Submit report" -> s.equals("In progress");
                case "Submit" -> s.equals("Draft");case "Place order" -> s.equals("Approved");case "Receive goods" -> s.equals("Ordered");
                case "Issue invoice" -> s.equals("Draft");case "Void" -> !s.equals("Paid")&&!s.equals("Partially paid");case "Refund" -> s.equals("Recorded");
                case "Assign bed" -> List.of("Available","Reserved").contains(s);case "Release bed" -> s.equals("Occupied")||s.equals("Maintenance");
                case "Submit claim" -> s.equals("Verified");default -> true;
            };if(ok) result.add(a);
        }return result;
    }
    @Override public synchronized Map<String,String> transition(String module,String id,String action,Map<String,String> values,String actor) {
        var row=raw(module,id);
        if(!allowed(module,display(module,row)).contains(action)) fail("This action is not available for the current status.");
        if(module.equals("visits")&&List.of("Complete","Start consultation").contains(action))return careAction(row.get("patient"),id,action.equals("Complete")?"complete":"start",Map.of("plan",values.getOrDefault("plan",row.getOrDefault("notes",""))),actor);
        if(module.equals("consultations")&&action.equals("Complete")&&row.containsKey("encounter")){careAction(row.get("patient"),row.get("encounter"),"complete",Map.of("plan",row.getOrDefault("treatment","")),actor);}
        if(module.equals("appointments")&&action.equals("Complete"))fail("Complete the linked consultation from the patient workspace.");
        if(module.equals("queues")&&action.equals("Transfer")&&row.containsKey("encounter")){
            // A transfer ends this step and opens the next one; the patient never drops out of the queue.
            String dep=values.getOrDefault("department","");raw("departments",dep);String doctor=values.getOrDefault("doctor","");
            if(doctor.isBlank()&&raw("doctors",row.get("doctor")).get("department").equals(dep))doctor=row.get("doctor");
            if(doctor.isBlank())fail("Select the receiving doctor.");if(!raw("doctors",doctor).get("department").equals(dep))fail("Select a doctor assigned to this department.");
            var input=new LinkedHashMap<String,String>();input.put("queue",id);input.put("status","Transferred");String station=Objects.toString(values.get("station"),"");input.put("station",station.isBlank()?"Consultation":station);input.put("doctor",doctor);
            return display(module,raw(module,careAction(row.get("patient"),row.get("encounter"),"queue",input,actor).get("id")));
        }
        if(module.equals("visits")&&action.equals("Transfer"))return display(module,raw(module,transferVisit(row,values,actor).get("id")));
        if(module.equals("visits")&&action.equals("Cancel"))for(var q:store.get("queues").values())if(id.equals(q.get("encounter"))&&!List.of("Completed","Cancelled","Transferred","Missed").contains(q.get("status"))){q.put("status","Cancelled");q.put("version",String.valueOf(Integer.parseInt(q.get("version"))+1));history(q.get("id"),"Cancelled with visit "+id,actor);}
        if(module.equals("appointments")&&action.equals("Reschedule")&&row.containsKey("encounter"))fail("A checked-in appointment cannot be rescheduled. Book a follow-up.");
        if(module.equals("opd")&&action.equals("Check in")) {
            var visit=beginVisit(row,null,actor);row.put("encounter",visit.get("id"));row.put("queueNumber",visit.get("queueNumber"));
            row.put("status","Checked in");history(id,"Checked in",actor);return display(module,row);
        }
        if(action.equals("Reschedule")) {
            var copy=new LinkedHashMap<>(row);copy.put("date",values.getOrDefault("date",""));copy.put("time",values.getOrDefault("time",""));
            try { if(LocalDate.parse(copy.get("date")).isBefore(LocalDate.now())) fail("Choose today or a future date.");LocalTime.parse(copy.get("time")); }catch(DateTimeParseException ex){fail("Choose a valid date and time.");}
            checkSlot(copy,id);row.put("date",copy.get("date"));row.put("time",copy.get("time"));row.put("status","Scheduled");
        } else if(List.of("Add stock","Remove stock","Count stock").contains(action)) {
            double qty;try {qty=Double.parseDouble(values.getOrDefault("quantity",""));}catch(NumberFormatException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a quantity.");}
            if(!Double.isFinite(qty)||qty<0||qty>1000000||values.getOrDefault("reason","").isBlank()) fail("Enter a valid quantity and adjustment reason.");
            double next=action.equals("Count stock")?qty:number(row,"quantity")+(action.equals("Remove stock")?-qty:qty);
            if(next<0) fail("Insufficient stock.");row.put("quantity",money(next));row.put("notes",values.get("reason"));
        } else {
            if(action.equals("Approve")&&module.equals("laboratory")&&row.get("result").isBlank()) fail("Enter a result before approval.");
            if(action.equals("Enter result")&&row.get("result").isBlank()) fail("Edit the order and enter a result first.");
            if(action.equals("Submit report")&&row.get("report").isBlank()) fail("Write a report before submitting.");
            if(action.equals("Approve leave")&&(row.get("leaveStart").isBlank()||row.get("leaveEnd").isBlank())) fail("Enter the leave dates first.");
            if(action.equals("Assign bed")) {String p=values.getOrDefault("patient",row.getOrDefault("patient",""));raw("patients",p);for(var bed:store.get("facilities").values()) if(!id.equals(bed.get("id"))&&p.equals(bed.get("patient"))&&bed.get("status").equals("Occupied")) fail("This patient already occupies a bed.");row.put("patient",p);}
            if(action.equals("Release bed")) row.put("patient","");
            if(action.equals("Call patient"))row.put("calledAt",LocalDateTime.now().toString());
            if(action.equals("Transfer")) { String dep=values.getOrDefault("department","");raw("departments",dep);row.put("department",dep); }
            if(action.equals("Dispense")) dispense(row,actor);
            if(action.equals("Receive goods")) receive(row,actor);
            row.put("status",TARGET.get(action));
        }
        row.put("version",String.valueOf(Integer.parseInt(row.get("version"))+1));
        history(id,action+(values.getOrDefault("reason","").isBlank()?"":" — "+values.get("reason")),actor);audit(actor,"Workflow",module,id+": "+action);
        return display(module,row);
    }
    /** Doctor transfer keeps the same encounter open and moves the patient to the receiving doctor's queue. */
    private Map<String,String> transferVisit(Map<String,String> visit,Map<String,String> values,String actor) {
        String dep=values.getOrDefault("department","");raw("departments",dep);String doctor=values.getOrDefault("doctor","");
        if(doctor.isBlank())fail("Select the receiving doctor.");var clinician=raw("doctors",doctor);if(!clinician.get("department").equals(dep))fail("Select a doctor assigned to this department.");
        if(doctor.equals(visit.get("doctor")))fail("Choose a different doctor for the transfer.");
        var active=store.get("queues").values().stream().filter(q->visit.get("id").equals(q.get("encounter"))&&List.of("Waiting","Called","In service","On hold").contains(q.get("status"))).reduce((a,b)->b);
        if(active.isPresent()){var input=new LinkedHashMap<String,String>();input.put("queue",active.get().get("id"));input.put("status","Transferred");input.put("station","Consultation");input.put("doctor",doctor);
            careAction(visit.get("patient"),visit.get("id"),"queue",input,actor);}
        visit.put("doctor",doctor);visit.put("department",dep);if(visit.get("status").equals("In consultation"))visit.put("status","Waiting");
        visit.put("version",String.valueOf(Integer.parseInt(visit.get("version"))+1));history(visit.get("id"),"Transferred to "+clinician.get("name")+(values.getOrDefault("reason","").isBlank()?"":" — "+values.get("reason")),actor);audit(actor,"Workflow","visits",visit.get("id")+": Transfer");
        return visit;
    }
    private void dispense(Map<String,String> rx,String actor) {
        double needed=number(rx,"quantity");List<Map<String,String>> batches=store.get("stock").values().stream().filter(b->rx.get("medicine").equals(b.get("medicine"))&&!b.get("status").equals("Quarantined")&&!LocalDate.parse(b.get("expiry")).isBefore(LocalDate.now())).sorted(Comparator.comparing(b->b.get("expiry"))).toList();
        if(batches.stream().mapToDouble(b->number(b,"quantity")).sum()<needed) fail("Insufficient unexpired stock for this prescription.");
        for(var b:batches) {double taken=Math.min(needed,number(b,"quantity"));if(taken>0){b.put("quantity",money(number(b,"quantity")-taken));b.put("version",String.valueOf(Integer.parseInt(b.get("version"))+1));history(b.get("id"),"Dispensed "+money(taken)+" for "+rx.get("id"),actor);}needed-=taken;if(needed<=0) break;}
    }
    private void receive(Map<String,String> po,String actor) {
        if(po.get("medicine").isBlank()||number(po,"quantity")<=0) fail("Select a medicine and a positive quantity before receiving.");
        var batch=new LinkedHashMap<String,String>();batch.put("name","GRN-"+sequence.incrementAndGet());batch.put("medicine",po.get("medicine"));batch.put("location",store.get("stock-locations").keySet().iterator().next());batch.put("quantity",po.get("quantity"));batch.put("minimum","30");batch.put("expiry",LocalDate.now().plusYears(1).toString());batch.put("unitCost",money(number(po,"amount")/number(po,"quantity")));batch.put("notes","Received against "+po.get("id"));save("stock",null,batch,actor);
    }
    @Override public synchronized Map<String,List<Map<String,String>>> references() {
        Map<String,List<Map<String,String>>> refs=new LinkedHashMap<>();
        for(var m:catalog.all()) refs.put(m.key(),store.get(m.key()).values().stream().map(r->{var out=new LinkedHashMap<String,String>();out.put("id",r.get("id"));out.put("name",r.get("name"));out.put("status",r.get("status"));if(r.containsKey("department"))out.put("department",r.get("department"));if(r.containsKey("patient"))out.put("patient",r.get("patient"));return (Map<String,String>)out;}).toList());
        return refs;
    }
    @Override public synchronized Map<String,Object> details(String module,String id) {
        var row=get(module,id);Map<String,Object> result=new LinkedHashMap<>();result.put("record",row);result.put("timeline",histories.getOrDefault(id,List.of()));
        String patient=module.equals("patients")?id:row.getOrDefault("patient","");
        Map<String,List<Map<String,String>>> related=new LinkedHashMap<>();
        for(var def:catalog.all()) {String m=def.key();
            related.put(m,store.get(m).values().stream().filter(r->
                !patient.isBlank()&&(patient.equals(r.get("patient"))||m.equals("patients")&&patient.equals(r.get("id"))) ||
                module.equals("departments")&&id.equals(r.get("department")) ||
                module.equals("doctors")&&id.equals(r.get("doctor")) ||
                module.equals("medicines")&&id.equals(r.get("medicine")) ||
                module.equals("billing")&&id.equals(r.get("invoice"))||module.equals("pharmacy")&&m.equals("stock")&&row.get("medicine").equals(r.get("medicine"))||def.fields().stream().anyMatch(f->f.type().equals("ref")&&f.source().equals(module)&&id.equals(r.get(f.key())))).map(r->display(m,r)).toList());
        }
        result.put("related",related);
        result.put("files",files.values().stream().filter(f->id.equals(f.get("record"))).map(f->Map.of("id",f.get("id"),"name",f.get("name"),"size",((byte[])f.get("bytes")).length)).toList());
        return result;
    }
    @Override public synchronized Map<String,Object> dashboard(String from,String to) {
        Map<String,Object> d=new LinkedHashMap<>();
        for(var m:catalog.all()) {boolean current=List.of("patients","doctors","facilities","queues","stock").contains(m.key());d.put(m.key(),list(m.key(),"","","",current?"":from,current?"":to,1,100));}
        List<String> labels=new ArrayList<>();List<Long> a=new ArrayList<>(),p=new ArrayList<>();List<Double> revenue=new ArrayList<>();
        LocalDate end=to.isBlank()?LocalDate.now():LocalDate.parse(to),start=from.isBlank()?end.minusDays(6):LocalDate.parse(from);
        if(start.isAfter(end)) fail("Start date must be before end date.");long span=Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(start,end)/7);
        for(LocalDate day=start;!day.isAfter(end)&&labels.size()<14;day=day.plusDays(span)) {
            String ds=day.toString(),de=day.plusDays(span-1).isAfter(end)?end.toString():day.plusDays(span-1).toString();labels.add(day.getDayOfMonth()+" "+day.getMonth().toString().substring(0,3));
            a.add(list("appointments","","","",ds,de,1,1).total());p.add(list("patients","","","",ds,de,1,1).total());
            revenue.add(store.get("payments").values().stream().filter(r->r.get("date").compareTo(ds)>=0&&r.get("date").compareTo(de)<=0&&r.get("status").equals("Recorded")).mapToDouble(r->number(r,"amount")).sum());
        }
        d.put("chart",Map.of("labels",labels,"appointments",a,"patients",p,"revenue",revenue));return d;
    }
    @Override public synchronized byte[] export(String module,String search,String status,String filter,String from,String to) {
        var m=catalog.get(module);List<String> keys=new ArrayList<>(List.of("id","status"));keys.addAll(m.fields().stream().filter(f->!f.type().equals("file")).map(FieldDefinition::key).toList());
        StringBuilder csv=new StringBuilder("\uFEFF").append(String.join(",",keys)).append("\r\n");
        for(var raw:store.get(module).values()) {var r=display(module,raw);String date=r.getOrDefault("date",r.get("createdAt"));
            if(!search.isBlank()&&r.values().stream().noneMatch(v->v.toLowerCase().contains(search.toLowerCase())))continue;
            if(!status.isBlank()&&!status.equals(r.get("status"))||!matchesFilter(r,filter)||!from.isBlank()&&date.compareTo(from)<0||!to.isBlank()&&date.compareTo(to)>0)continue;
            csv.append(String.join(",",keys.stream().map(k->csv(r.getOrDefault(k+"Label",r.getOrDefault(k,"")))).toList())).append("\r\n");
        }return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private String csv(String value){if(value.matches("^[=+@\\-\\t\\r].*")) value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
    @Override public synchronized String attach(String module,String id,String name,byte[] bytes,String contentType,String actor) {
        if(!id.equals("avatar"))raw(module,id);
        if(bytes.length==0||bytes.length>5*1024*1024)fail("Choose a file up to 5 MB.");
        if(!List.of("application/pdf","image/png","image/jpeg","text/plain").contains(contentType))fail("Choose a PDF, PNG, JPEG, or text document.");
        if(files.size()>=100)fail("Demo attachment limit reached. Restart to clear in-memory files.");
        String fileId=UUID.randomUUID().toString();Map<String,Object> f=new LinkedHashMap<>();f.put("id",fileId);f.put("record",id);f.put("module",module);f.put("name",name.replaceAll("[\\r\\n\\\\/]","_"));f.put("bytes",bytes.clone());f.put("contentType",contentType);files.put(fileId,f);history(id,"Document attached: "+name,actor);return fileId;
    }
    @Override public synchronized Map<String,Object> attachment(String id){var f=files.get(id);if(f==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"File not found");return Map.copyOf(f);}
    @Override public synchronized Map<String,Object> importMedicines(List<Map<String,String>> rows,String actor){
        if(rows.isEmpty()||rows.size()>200)fail("Import between 1 and 200 medicines per file.");
        var medicinesBefore=new LinkedHashMap<>(store.get("medicines"));var auditBefore=new LinkedHashMap<>(store.get("audit"));var historyKeys=new HashSet<>(histories.keySet());
        try {int index=0;for(var input:rows){index++;var resolved=new LinkedHashMap<>(input);for(var f:catalog.get("medicines").fields())if(f.type().equals("ref")&&!resolved.getOrDefault(f.key(),"").isBlank()) {
            String value=resolved.get(f.key());var found=store.get(f.source()).values().stream().filter(r->value.equals(r.get("id"))||value.equalsIgnoreCase(r.get("name"))).toList();if(found.size()!=1)fail("Row "+index+": choose a unique existing "+f.label()+" by name or ID.");resolved.put(f.key(),found.getFirst().get("id"));
        }save("medicines",null,resolved,actor);}return Map.of("created",rows.size(),"message",rows.size()+" medicines imported into memory.");
        }catch(RuntimeException ex){store.put("medicines",medicinesBefore);store.put("audit",auditBefore);histories.keySet().retainAll(historyKeys);throw ex;}
    }
    private void history(String id,String action,String actor){histories.computeIfAbsent(id,k->new ArrayList<>()).addFirst(Map.of("action",action,"actor",actor,"time",LocalDateTime.now().toString()));}
    private void audit(String actor,String action,String module,String id) {String aid="LOG-"+sequence.incrementAndGet();var row=new LinkedHashMap<String,String>();row.put("id",aid);row.put("name",action+" "+module+" record");row.put("actor",actor);row.put("action",action);row.put("resource",module+" / "+id);row.put("date",LocalDate.now().toString());row.put("createdAt",LocalDate.now().toString());row.put("status","Recorded");row.put("notes","Synthetic demo workspace activity");row.put("version","1");store.get("audit").putFirst(aid,row);}
    private boolean matchesFilter(Map<String,String> row,String filter){
        if(filter.isBlank())return true;
        for(String part:filter.split(";")){
            if(!part.contains("=")){if(!row.containsValue(part))return false;continue;}
            String[] kv=part.split("=",2);String key=kv[0],value=kv[1];
            if(key.equals("ageMin")||key.equals("ageMax")){try{int bound=Integer.parseInt(value),age=Period.between(LocalDate.parse(row.get("dateOfBirth")),LocalDate.now()).getYears();if(bound<0||bound>130)fail("Use ages between 0 and 130.");if(key.equals("ageMin")&&age<bound||key.equals("ageMax")&&age>bound)return false;}catch(RuntimeException ex){fail("Enter a valid age filter.");}}
            else if(!value.equals(row.get(key)))return false;
        }return true;
    }
    private double paid(String id){return store.get("payments").values().stream().filter(p->id.equals(p.get("invoice"))&&p.get("status").equals("Recorded")).mapToDouble(p->number(p,"amount")).sum();}
    private double total(Map<String,String> r){return Math.round((number(r,"amount")-number(r,"discount"))*(1+number(r,"tax")/100)*100.0)/100.0;}
    private double number(Map<String,String> r,String key){try{return Double.parseDouble(r.getOrDefault(key,"0").isBlank()?"0":r.get(key));}catch(NumberFormatException ex){return 0;}}
    private String money(double v){return String.format(Locale.US,"%.2f",v);}
    private void fail(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private List<Map<String,String>> patientRows(String module,String patient,String encounter) {
        return store.get(module).values().stream().filter(r->patient.equals(r.get("patient"))&&(encounter.isBlank()||encounter.equals(r.get("encounter"))||module.equals("visits")&&encounter.equals(r.get("id")))).map(r->display(module,r)).toList();
    }
    private Map<String,String> beginVisit(Map<String,String> source,String appointment,String actor) {
        String patient=source.get("patient");
        if(source.containsKey("encounter"))return raw("visits",source.get("encounter"));
        if(store.get("visits").values().stream().anyMatch(v->patient.equals(v.get("patient"))&&LocalDate.now().toString().equals(v.get("date"))&&!List.of("Completed","Cancelled","Transferred").contains(v.get("status"))))fail("An active visit already exists. Open the existing visit before checking in again.");
        var v=new LinkedHashMap<String,String>();
        for(String k:List.of("patient","doctor","department"))v.put(k,source.get(k));
        v.put("name",source.getOrDefault("reason","").isBlank()?source.get("name"):source.get("reason"));v.put("date",LocalDate.now().toString());v.put("type","Outpatient");
        var visit=save("visits",null,v,actor);var actual=raw("visits",visit.get("id"));
        if(appointment!=null)actual.put("appointment",appointment);
        var q=new LinkedHashMap<String,String>(v);q.put("name","Q-"+sequence.incrementAndGet());q.put("priority",source.getOrDefault("priority","Normal"));q.put("room","Reception");q.put("encounter",actual.get("id"));
        var queue=save("queues",null,q,actor);raw("queues",queue.get("id")).put("station","Triage");raw("queues",queue.get("id")).put("visitReason",v.get("name"));raw("queues",queue.get("id")).put("appointmentTime",source.getOrDefault("time",""));raw("queues",queue.get("id")).put("queuedAt",LocalDateTime.now().toString());
        actual.put("queueNumber",queue.get("name"));history(actual.get("id"),"Registration / check-in",actor);return actual;
    }
    @Override public synchronized Map<String,Object> careContext(String patient,String encounter) {
        var p=get("patients",patient);var result=new LinkedHashMap<String,Object>();result.put("patient",p);
        Map<String,String> visit=encounter.isBlank()?Map.of():get("visits",encounter);
        if(!encounter.isBlank()&&!patient.equals(visit.get("patient")))fail("Encounter does not belong to this patient.");
        result.put("encounter",visit);
        var history=new LinkedHashMap<String,List<Map<String,String>>>();var current=new LinkedHashMap<String,List<Map<String,String>>>();
        for(String m:List.of("appointments","visits","queues","emr","consultations","laboratory","imaging","pharmacy","billing","payments")) {history.put(m,patientRows(m,patient,""));current.put(m,encounter.isBlank()?List.of():patientRows(m,patient,encounter));}
        result.put("history",history);result.put("current",current);
        result.put("documents",careDocuments.values().stream().filter(d->patient.equals(d.get("patient"))).map(Map::copyOf).toList());
        result.put("timeline",encounter.isBlank()?List.of():List.copyOf(histories.getOrDefault(encounter,List.of())));
        return result;
    }
    private String required(Map<String,String> values,String key) {String v=Objects.toString(values.get(key),"").strip();if(v.isBlank()||v.length()>6000)fail("Enter a valid "+key+".");return v;}
    private void range(Map<String,String> values,String key,double min,double max) {
        String v=values.getOrDefault(key,"");if(v.isBlank())return;
        try{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<min||n>max)fail("Check "+key+" and its units.");}catch(NumberFormatException ex){fail("Enter a number for "+key+".");}
    }
    @Override public synchronized Map<String,String> careAction(String patient,String encounter,String action,Map<String,String> values,String actor) {
        raw("patients",patient);
        if(action.equals("checkin")) {
            var appointment=raw("appointments",required(values,"appointment"));if(!patient.equals(appointment.get("patient")))fail("Appointment does not belong to this patient.");
            if(!List.of("Scheduled","Confirmed").contains(appointment.get("status")))fail("This appointment is not available for check-in.");
            if(!LocalDate.now().toString().equals(appointment.get("date")))fail("Check-in is available on the appointment date.");
            var visit=beginVisit(appointment,appointment.get("id"),actor);appointment.put("encounter",visit.get("id"));appointment.put("checkedInAt",LocalDateTime.now().toString());appointment.put("version",String.valueOf(Integer.parseInt(appointment.get("version"))+1));
            history(visit.get("id"),"Appointment attached: "+appointment.get("id"),actor);
            if(checkIns.stream().noneMatch(c->appointment.get("id").equals(c.get("appointment")))){var event=new LinkedHashMap<String,String>();event.put("time",LocalDateTime.now().withNano(0).toString());event.put("appointment",appointment.get("id"));event.put("patient",patient);event.put("encounter",visit.get("id"));event.put("method",Objects.toString(values.get("method"),"Reception desk"));event.put("actor",actor);checkIns.addFirst(event);}
            return display("visits",visit);
        }
        var visit=raw("visits",encounter);if(!patient.equals(visit.get("patient")))fail("Encounter does not belong to this patient.");
        if(List.of("Completed","Cancelled","Transferred").contains(visit.get("status"))&&!List.of("release","doctor-review").contains(action))fail("This encounter is closed. Open a new visit for additional care.");
        var input=new LinkedHashMap<String,String>(values);input.put("patient",patient);input.put("encounter",encounter);input.put("doctor",visit.get("doctor"));input.put("department",visit.get("department"));input.put("date",LocalDate.now().toString());
        if(action.equals("order")&&values.getOrDefault("category","").equals("Laboratory")&&!values.getOrDefault("tests","").isBlank()) {
            var tests=Arrays.stream(values.get("tests").split(",")).distinct().toList();if(tests.size()>20)fail("Select up to 20 tests.");required(values,"indication");for(String test:tests)raw("lab-tests",test);
            Map<String,String> last=Map.of();for(String test:tests){var item=new LinkedHashMap<>(values);item.remove("tests");item.put("test",test);last=careAction(patient,encounter,"order",item,actor);}return last;
        }
        Map<String,String> result=visit;
        switch(action) {
            case "start" -> {if(!visit.get("status").equals("Waiting"))fail("Consultation is already started.");visit.put("status","In consultation");for(var q:store.get("queues").values())if(encounter.equals(q.get("encounter"))&&List.of("Registration","Triage","Consultation","Doctor review").contains(q.getOrDefault("station","Consultation"))&&List.of("Waiting","Called").contains(q.get("status"))){q.put("status","In service");q.put("station","Consultation");}}
            case "queue" -> {
                var q=raw("queues",required(values,"queue"));if(!encounter.equals(q.get("encounter")))fail("Queue does not belong to this encounter.");
                String next=required(values,"status"),old=q.get("status");
                var allowed=Map.of("Waiting",List.of("Called","On hold","Transferred","Cancelled"),"Called",List.of("In service","Missed","On hold","Transferred","Waiting"),"In service",List.of("Completed","On hold","Transferred"),"On hold",List.of("Waiting","Transferred","Cancelled"),"Missed",List.of("Waiting","Cancelled"));
                if(!allowed.getOrDefault(old,List.of()).contains(next))fail("Queue action is unavailable for this status.");
                if(next.equals("Transferred")) {
                    String station=required(values,"station");if(!List.of("Registration","Triage","Consultation","Laboratory","Imaging","Pharmacy","Payment","Doctor review").contains(station))fail("Choose a valid station.");
                    String doctor=values.getOrDefault("doctor",visit.get("doctor"));var clinician=raw("doctors",doctor);
                    var destination=new LinkedHashMap<>(q);destination.remove("version");destination.put("name","Q-"+sequence.incrementAndGet());destination.put("room",station);destination.put("doctor",doctor);destination.put("department",clinician.get("department"));
                    var saved=save("queues",null,destination,actor);var target=raw("queues",saved.get("id"));target.put("station",station);target.put("queuedAt",LocalDateTime.now().toString());target.put("fromQueue",q.get("id"));target.put("visitReason",q.getOrDefault("visitReason",visit.get("name")));target.put("appointmentTime",q.getOrDefault("appointmentTime",""));
                    if(station.equals("Consultation")||station.equals("Doctor review")){visit.put("doctor",doctor);visit.put("department",clinician.get("department"));}
                }
                q.put("status",next);if(next.equals("Called"))q.put("calledAt",LocalDateTime.now().toString());if(next.equals("Waiting")&&List.of("Called","Missed").contains(old))q.put("queuedAt",LocalDateTime.now().toString());if(next.equals("Called"))emit("queue-called",patient,"queue",q.get("name"));q.put("version",String.valueOf(Integer.parseInt(q.get("version"))+1));result=q;
            }
            case "triage" -> {
                required(values,"complaint");required(values,"priority");if(!List.of("Normal","Urgent","Emergency").contains(values.get("priority")))fail("Choose a valid priority.");
                range(values,"temperature",25,45);range(values,"pulse",1,300);range(values,"respiratoryRate",1,100);range(values,"oxygen",1,100);range(values,"weight",0.1,600);range(values,"height",20,260);range(values,"pain",0,10);
                if(!values.getOrDefault("bloodPressure","").isBlank()&&!values.get("bloodPressure").matches("[0-9]{2,3}/[0-9]{2,3}"))fail("Use systolic/diastolic in mmHg.");
                if(!values.getOrDefault("doctor","").isBlank()){var assigned=raw("doctors",values.get("doctor"));if(!values.getOrDefault("department",assigned.get("department")).equals(assigned.get("department")))fail("Select a doctor assigned to this department.");visit.put("doctor",assigned.get("id"));visit.put("department",assigned.get("department"));input.put("doctor",assigned.get("id"));input.put("department",assigned.get("department"));}
                result=saveCareDocument("triage",input,actor);visit.put("triageStatus","Recorded");
                for(var q:store.get("queues").values())if(encounter.equals(q.get("encounter"))){q.put("priority",values.get("priority"));q.put("doctor",visit.get("doctor"));q.put("department",visit.get("department"));if(List.of("Registration","Triage").contains(q.getOrDefault("station",""))&&List.of("Waiting","Called","On hold").contains(q.get("status"))){q.put("station","Consultation");if(q.get("status").equals("Called"))q.put("status","Waiting");}}
            }
            case "note" -> {required(values,"subjective");required(values,"assessment");result=saveCareDocument("note",input,actor);}
            case "diagnosis" -> {required(values,"text");result=saveCareDocument("diagnosis",input,actor);}
            case "certificate" -> {required(values,"text");String template=required(values,"template");if(!List.of("Consultation Attendance Certificate","Medical Leave Certificate","General Medical Certificate","Referral Letter","Fit-to-Work Certificate").contains(template))fail("Choose a certificate template.");result=saveCareDocument("certificate",input,actor);}
            case "sign","issue" -> {
                var doc=careDocuments.get(required(values,"document"));if(doc==null||!encounter.equals(doc.get("encounter")))fail("Document not found in this encounter.");
                if(!doc.get("status").equals("Draft"))fail("This document is already finalized. Create an amendment.");
                if(!"true".equals(values.get("reviewed")))fail("Clinician review is required.");
                if(action.equals("issue")&&!doc.get("kind").equals("certificate")||action.equals("sign")&&!doc.get("kind").equals("note"))fail("Invalid document action.");
                doc.put("status",action.equals("issue")?"Issued":"Signed");doc.put("signedBy",actor);doc.put("signedAt",LocalDateTime.now().toString());result=doc;
            }
            case "order" -> {
                String category=required(values,"category");required(values,"indication");
                switch(category) {
                    case "Laboratory" -> {var test=raw("lab-tests",required(values,"test"));input.put("name",test.get("name"));for(String k:List.of("category","sample","referenceRange","unit"))input.put(k,test.get(k));input.put("notes",values.get("indication"));input.put("result","");result=save("laboratory",null,input,actor);}
                    case "Imaging" -> {input.put("name",required(values,"name"));input.put("category",required(values,"modality"));input.put("time",LocalTime.now().withSecond(0).withNano(0).toString());input.put("report","");result=save("imaging",null,input,actor);}
                    case "Procedures" -> {required(values,"name");result=saveCareDocument("procedure",input,actor);}
                    default -> fail("Choose an order category.");
                }
            }
            case "prescription-draft" -> {raw("medicines",required(values,"medicine"));required(values,"dosage");required(values,"quantity");required(values,"duration");range(values,"quantity",1,10000);range(values,"duration",1,365);result=saveCareDocument("prescription",input,actor);}
            case "submit-prescription" -> {var doc=careDocuments.get(required(values,"document"));if(doc==null||!encounter.equals(doc.get("encounter"))||!doc.get("kind").equals("prescription")||!doc.get("status").equals("Draft"))fail("Open a prescription draft in this encounter.");if(!"true".equals(values.get("reviewed")))fail("Clinician review is required.");var rx=new LinkedHashMap<>(doc);rx.remove("version");rx.put("name","Encounter prescription");rx.putIfAbsent("location","PHL-0001");result=save("pharmacy",null,rx,actor);doc.put("status","Submitted");doc.put("prescriptionId",result.get("id"));}
            case "prescription" -> {input.putIfAbsent("location","PHL-0001");required(values,"dosage");range(values,"quantity",1,10000);range(values,"duration",1,365);input.put("name","Encounter prescription");result=save("pharmacy",null,input,actor);}
            case "followup" -> {input.put("date",required(values,"followupDate"));if(LocalDate.parse(input.get("date")).isBefore(LocalDate.now()))fail("Choose a future appointment date.");input.remove("encounter");input.put("time",required(values,"time"));input.put("type","Follow-up");input.put("name","Follow-up");input.put("reason",required(values,"reason"));result=save("appointments",null,input,actor);raw("appointments",result.get("id")).put("previousEncounter",encounter);emit("follow-up",patient,"date",input.get("date"));}
            case "cancel-order" -> {
                String module=required(values,"module");if(!List.of("laboratory","imaging").contains(module))fail("Select an order.");var order=raw(module,required(values,"order"));
                if(!encounter.equals(order.get("encounter")))fail("Order does not belong to this encounter.");if(List.of("Approved","Cancelled").contains(order.get("status")))fail("Approved or cancelled orders cannot be cancelled.");
                String reason=required(values,"reason");order.put("status","Cancelled");order.put("version",String.valueOf(Integer.parseInt(order.get("version"))+1));history(order.get("id"),"Cancel — "+reason,actor);result=order;
            }
            case "release","doctor-review" -> {
                String module=required(values,"module");if(!List.of("laboratory","imaging").contains(module))fail("Select a result.");var order=raw(module,required(values,"order"));if(!encounter.equals(order.get("encounter")))fail("Result does not belong to this encounter.");if(!order.get("status").equals("Approved"))fail("Release an approved result only.");order.put(action.equals("release")?"released":"reviewedByDoctor","true");result=order;
            }
            case "complete" -> {
                if(!visit.get("status").equals("In consultation"))fail("Start the consultation before completing it.");
                required(values,"plan");
                if(careDocuments.values().stream().anyMatch(d->encounter.equals(d.get("encounter"))&&d.get("kind").equals("note")&&d.get("status").equals("Draft")))fail("Sign draft notes before completing the encounter.");
                for(String m:List.of("laboratory","imaging"))if(store.get(m).values().stream().anyMatch(o->encounter.equals(o.get("encounter"))&&!o.get("status").equals("Cancelled")&&(!o.get("status").equals("Approved")||!"true".equals(o.get("reviewedByDoctor")))))fail("Results are pending or require doctor review. Keep this encounter open.");
                visit.put("status","Completed");visit.put("dischargePlan",values.get("plan"));
                for(var q:store.get("queues").values())if(encounter.equals(q.get("encounter"))&&!List.of("Transferred","Cancelled","Missed").contains(q.get("status")))q.put("status","Completed");
                if(visit.containsKey("appointment"))raw("appointments",visit.get("appointment")).put("status","Completed");
                draftEncounterInvoice(visit,actor);emit("visit-completed",patient);
            }
            default -> fail("Unknown care action.");
        }
        visit.put("version",String.valueOf(Integer.parseInt(visit.get("version"))+1));history(encounter,action+" / "+result.get("id"),actor);audit(actor,action,"visits",encounter);
        if(List.of("order","followup","release","submit-prescription").contains(action))save("notifications",null,Map.of("name","Care task updated","type","Clinical","recipient","Demo care team","message","A task was updated. Open the hospital workspace to review it.","channel","In-app (mock)","date",LocalDate.now().toString()),actor);
        return Map.copyOf(result);
    }
    /** Charge capture: a draft invoice the cashier reviews and issues. No payment is taken here. */
    private void draftEncounterInvoice(Map<String,String> visit,String actor) {
        String id=visit.get("id");if(store.get("billing").values().stream().anyMatch(b->id.equals(b.get("encounter"))&&!b.get("status").equals("Void")))return;
        var lines=new ArrayList<String>();lines.add("Consultation — "+raw("doctors",visit.get("doctor")).get("name")+" | 1 | "+money(number(raw("doctors",visit.get("doctor")),"fee")));
        for(var o:store.get("laboratory").values())if(id.equals(o.get("encounter"))&&o.get("status").equals("Approved")){var test=store.get("lab-tests").get(o.get("test"));lines.add(o.get("name").replace("|","/")+" | 1 | "+money(test==null?0:number(test,"price")));}
        for(var o:store.get("imaging").values())if(id.equals(o.get("encounter"))&&o.get("status").equals("Approved")){var svc=store.get("billable-services").values().stream().filter(x->x.get("name").equalsIgnoreCase(o.get("name"))).findFirst();lines.add(o.get("name").replace("|","/")+" | 1 | "+money(svc.map(x->number(x,"price")).orElse(0.0)));}
        var invoice=new LinkedHashMap<String,String>();invoice.put("name","Encounter charges "+id);invoice.put("patient",visit.get("patient"));invoice.put("date",LocalDate.now().toString());invoice.put("dueDate",LocalDate.now().plusDays(14).toString());invoice.put("amount","0");invoice.put("discount","0");invoice.put("tax","0");invoice.put("items",String.join("\n",lines));invoice.put("notes","Draft generated at discharge. Review charges before issuing. Synthetic demo invoice.");invoice.put("encounter",id);
        var saved=save("billing",null,invoice,actor);history(id,"Draft encounter invoice "+saved.get("id"),actor);
    }
    private Map<String,String> saveCareDocument(String kind,Map<String,String> input,String actor) {
        String id=input.getOrDefault("document","");Map<String,String> old=id.isBlank()?null:careDocuments.get(id);
        if(!id.isBlank()&&(old==null||!old.get("encounter").equals(input.get("encounter"))||!old.get("kind").equals(kind)))fail("Document not found in this encounter.");
        if(old!=null&&(!old.get("status").equals("Draft")||!Objects.equals(old.get("version"),input.get("version"))))fail("Document is signed or changed. Reopen it or create an amendment.");
        var doc=new LinkedHashMap<String,String>();
        for(String key:List.of("patient","encounter","doctor","department","date","complaint","priority","bloodPressure","temperature","pulse","respiratoryRate","oxygen","weight","height","pain","allergies","symptoms","notes","subjective","objective","assessment","plan","text","template","purpose","leaveStart","leaveEnd","name","indication","amends","medicine","dosage","frequency","quantity","duration","location")) {String v=input.getOrDefault(key,"");if(v.length()>6000)fail("Document field is too long.");doc.put(key,v);}
        if(!doc.get("leaveStart").isBlank()||!doc.get("leaveEnd").isBlank())try{if(LocalDate.parse(doc.get("leaveStart")).isAfter(LocalDate.parse(doc.get("leaveEnd"))))fail("Check the leave dates.");}catch(DateTimeParseException ex){fail("Enter valid leave dates.");}
        if(!doc.get("amends").isBlank()){var original=careDocuments.get(doc.get("amends"));if(original==null||!original.get("patient").equals(doc.get("patient"))||!original.get("kind").equals(kind)||original.get("status").equals("Draft"))fail("Choose a finalized document belonging to this patient.");}
        if(old!=null)history(old.get("encounter"),"Saved previous document version: "+old,actor);
        doc.put("id",old==null?"DOCM-"+sequence.incrementAndGet():id);doc.put("kind",kind);doc.put("status",List.of("triage","diagnosis","procedure").contains(kind)?"Recorded":"Draft");doc.put("version",old==null?"1":String.valueOf(Integer.parseInt(old.get("version"))+1));doc.put("createdBy",actor);careDocuments.put(doc.get("id"),doc);return Map.copyOf(doc);
    }
    @Override public synchronized Map<String,Object> mobileContext(String role,String identity) {
        if(role.equals("Patient")) {
            var output=new LinkedHashMap<String,Object>();output.put("patient",get("patients",identity));
            for(String m:List.of("appointments","queues","visits","pharmacy"))output.put(m,patientRows(m,identity,""));
            for(String m:List.of("laboratory","imaging"))output.put(m,patientRows(m,identity,"").stream().filter(r->"true".equals(r.get("released"))).toList());
            output.put("documents",careDocuments.values().stream().filter(d->identity.equals(d.get("patient"))&&d.get("kind").equals("certificate")&&d.get("status").equals("Issued")).map(Map::copyOf).toList());
            output.put("doctors",references().get("doctors"));output.put("departments",references().get("departments"));return output;
        }
        var output=new LinkedHashMap<String,Object>();for(String m:List.of("appointments","queues","visits","laboratory","imaging"))output.put(m,store.get(m).values().stream().filter(r->!role.equals("Doctor")||identity.equals(r.get("doctor"))).map(r->display(m,r)).toList());
        output.put("departments",references().get("departments"));output.put("doctors",references().get("doctors"));output.put("documents",careDocuments.values().stream().filter(d->!role.equals("Doctor")||identity.equals(d.get("doctor"))).map(Map::copyOf).toList());return output;
    }
    @Override public synchronized Map<String,String> checkInToken(String appointment,String patient,String token,String actor) {
        var a=raw("appointments",appointment);if(!patient.equals(a.get("patient")))fail("Appointment does not belong to this patient.");
        if(token.isBlank()) {
            // Eligibility is checked before a ticket is issued so the patient learns about problems on the phone, not at the desk.
            if(a.containsKey("encounter")||!List.of("Scheduled","Confirmed").contains(a.get("status")))fail("This appointment is not available for check-in.");
            if(!LocalDate.now().toString().equals(a.get("date")))fail("Check-in is available on the appointment date.");
            if(store.get("visits").values().stream().anyMatch(v->patient.equals(v.get("patient"))&&LocalDate.now().toString().equals(v.get("date"))&&!List.of("Completed","Cancelled","Transferred").contains(v.get("status"))))fail("You already have an active visit today. Please speak to reception.");
            checkInTokens.entrySet().removeIf(e->Long.parseLong(e.getValue().get("expires"))<System.currentTimeMillis()||appointment.equals(e.getValue().get("appointment")));String value=UUID.randomUUID().toString();String expires=String.valueOf(System.currentTimeMillis()+300000);checkInTokens.put(value,Map.of("appointment",appointment,"patient",patient,"expires",expires));return Map.of("token",value,"expires",expires);}
        var t=checkInTokens.get(token);if(t==null||!appointment.equals(t.get("appointment"))||!patient.equals(t.get("patient"))||Long.parseLong(t.get("expires"))<System.currentTimeMillis())fail("Check-in code is invalid or expired.");
        checkInTokens.remove(token);return careAction(patient,"","checkin",Map.of("appointment",appointment,"method","Patient confirmed with check-in code"),actor);
    }


    // ---- Patient journey -------------------------------------------------------------------------------------------
    private static Map<String,String> step(String key,String label,String status,String owner,String detail,String action){var m=new LinkedHashMap<String,String>();m.put("key",key);m.put("label",label);m.put("status",status);m.put("owner",owner);m.put("detail",detail);m.put("action",action);return m;}
    @Override public synchronized Map<String,Object> journey(String id) {
        Map<String,String> appointment=null,visit=null;
        if(id.startsWith("VIS-")){visit=raw("visits",id);var a=visit.get("appointment");if(a!=null)appointment=raw("appointments",a);}
        else{appointment=raw("appointments",id);var e=appointment.get("encounter");if(e!=null)visit=raw("visits",e);}
        String patient=appointment!=null?appointment.get("patient"):visit.get("patient");String enc=visit==null?"":visit.get("id");
        var steps=new ArrayList<Map<String,String>>();
        steps.add(step("registration","Registration","done","Reception","Patient "+patient+" registered",""));
        String aStatus=appointment==null?"":appointment.get("status");String vStatus=visit==null?"":visit.get("status");
        boolean closed=List.of("Completed","Cancelled","Transferred").contains(vStatus);
        if(visit!=null)steps.add(step("checkin","Check-In","done","Reception",appointment==null?"Walk-in registration":"Checked in "+Objects.toString(appointment.get("checkedInAt"),"").replace('T',' ').replaceAll("\\.\\d+$",""),""));
        else if(List.of("Cancelled","No-Show").contains(aStatus))steps.add(step("checkin","Check-In","cancelled","Reception","Appointment "+aStatus.toLowerCase(Locale.ROOT),""));
        else{var date=LocalDate.parse(appointment.get("date"));
            if(date.isEqual(LocalDate.now()))steps.add(step("checkin","Check-In","current","Reception","Patient expected at "+appointment.get("time"),"checkin"));
            else if(date.isAfter(LocalDate.now()))steps.add(step("checkin","Check-In","pending","Reception","Scheduled for "+appointment.get("date")+" "+appointment.get("time"),""));
            else steps.add(step("checkin","Check-In","blocked","Reception","Appointment date has passed — reschedule or mark no-show","reschedule"));}
        Map<String,String> queue=visit==null?null:store.get("queues").values().stream().filter(q->enc.equals(q.get("encounter"))).reduce((a,b)->b).orElse(null);
        boolean triaged=visit!=null&&careDocuments.values().stream().anyMatch(d->enc.equals(d.get("encounter"))&&d.get("kind").equals("triage"));
        if(visit==null)steps.add(step("triage","Triage & vitals","pending","Nurse","After check-in",""));
        else if(triaged)steps.add(step("triage","Triage & vitals","done","Nurse","Vital signs recorded",""));
        else if(!vStatus.equals("Waiting"))steps.add(step("triage","Triage & vitals","skipped","Nurse","Consultation started without triage",""));
        else steps.add(step("triage","Triage & vitals","current","Nurse","Record vital signs before the consultation","vitals"));
        if(visit==null)steps.add(step("consultation","Consultation","pending","Doctor","Waiting for check-in",""));
        else switch(vStatus){
            case "Waiting" -> {String where=queue==null?"Not queued":queue.get("name")+" · "+queue.get("status")+(queue.get("status").equals("Waiting")?" · position "+position(queue):"");
                steps.add(step("consultation","Consultation",triaged?"current":"pending","Doctor",where,queue!=null&&queue.get("status").equals("Waiting")?"call":"start"));}
            case "In consultation" -> steps.add(step("consultation","Consultation","current","Doctor","Consultation in progress","document"));
            case "Completed" -> steps.add(step("consultation","Consultation","done","Doctor","Consultation completed",""));
            default -> steps.add(step("consultation","Consultation","cancelled","Doctor","Visit "+vStatus.toLowerCase(Locale.ROOT),""));
        }
        var orders=new ArrayList<Map<String,String>>();if(visit!=null)for(String m:List.of("laboratory","imaging"))store.get(m).values().stream().filter(o->enc.equals(o.get("encounter"))&&!o.get("status").equals("Cancelled")).forEach(orders::add);
        long pending=orders.stream().filter(o->!o.get("status").equals("Approved")).count(),unreviewed=orders.stream().filter(o->o.get("status").equals("Approved")&&!"true".equals(o.get("reviewedByDoctor"))).count();
        if(visit==null||orders.isEmpty())steps.add(step("orders","Orders & results",closed?"skipped":"pending","Doctor",closed?"No orders were needed":"No laboratory or imaging orders",""));
        else if(pending>0)steps.add(step("orders","Orders & results","blocked","Laboratory / Imaging",pending+" result(s) pending",""));
        else if(unreviewed>0)steps.add(step("orders","Orders & results","current","Doctor",unreviewed+" result(s) awaiting doctor review","review"));
        else steps.add(step("orders","Orders & results","done","Doctor",orders.size()+" result(s) reviewed",""));
        var rx=visit==null?List.<Map<String,String>>of():store.get("pharmacy").values().stream().filter(r->enc.equals(r.get("encounter"))).toList();
        var bill=visit==null?null:store.get("billing").values().stream().filter(b->enc.equals(b.get("encounter"))&&!b.get("status").equals("Void")).findFirst().map(b->display("billing",b)).orElse(null);
        long rxOpen=rx.stream().filter(r->List.of("Pending","Reviewed").contains(r.get("status"))).count();
        if(bill==null&&rx.isEmpty())steps.add(step("pharmacy-billing","Pharmacy & billing",vStatus.equals("Completed")?"skipped":"pending","Pharmacy / Cashier",vStatus.equals("Completed")?"No charges recorded":"Charges are drafted at discharge",""));
        else if(rxOpen>0)steps.add(step("pharmacy-billing","Pharmacy & billing","current","Pharmacist",rxOpen+" prescription(s) to dispense",""));
        else if(bill!=null&&!bill.get("status").equals("Paid"))steps.add(step("pharmacy-billing","Pharmacy & billing","current","Cashier","Invoice "+bill.get("id")+" · "+bill.get("status")+" · balance "+bill.get("balance"),""));
        else steps.add(step("pharmacy-billing","Pharmacy & billing",bill==null?"pending":"done","Cashier",bill==null?"Charges are drafted at discharge":"Invoice "+bill.get("id")+" paid",""));
        if(vStatus.equals("Completed"))steps.add(step("completion","Discharge & follow-up","done","Doctor",Objects.toString(visit.get("dischargePlan"),"Visit completed"),""));
        else if(vStatus.equals("In consultation"))steps.add(step("completion","Discharge & follow-up",pending+unreviewed>0?"blocked":"pending","Doctor",pending+unreviewed>0?"Results must be reviewed before discharge":"Complete the consultation when ready","complete"));
        else steps.add(step("completion","Discharge & follow-up",List.of("Cancelled","No-Show").contains(aStatus)||List.of("Cancelled","Transferred").contains(vStatus)?"cancelled":"pending","Doctor","",""));
        var next=steps.stream().filter(st->List.of("current","blocked").contains(st.get("status"))&&!st.get("action").isBlank()).findFirst().orElse(null);
        var out=new LinkedHashMap<String,Object>();out.put("patient",get("patients",patient));out.put("appointment",appointment==null?Map.of():display("appointments",appointment));out.put("encounter",visit==null?Map.of():display("visits",visit));
        out.put("queue",queue==null?Map.of():display("queues",queue));out.put("position",queue!=null&&queue.get("status").equals("Waiting")?position(queue):0);out.put("steps",steps);out.put("next",next==null?Map.of():next);
        out.put("checkIns",checkInHistory(appointment!=null?appointment.get("id"):patient));out.put("invoice",bill==null?Map.of():bill);return out;
    }
    private int position(Map<String,String> q){
        var order=Comparator.<Map<String,String>>comparingInt(r->List.of("Emergency","Urgent","Normal").indexOf(r.getOrDefault("priority","Normal"))).thenComparing(r->Objects.toString(r.get("queuedAt"),r.get("createdAt")));
        var waiting=store.get("queues").values().stream().filter(r->r.get("status").equals("Waiting")&&Objects.equals(r.get("doctor"),q.get("doctor"))&&stationGroup(r).equals(stationGroup(q))).sorted(order).toList();
        return waiting.indexOf(q)+1;
    }
    /** Registration/Triage/Consultation tickets all wait for the doctor; other stations are separate worklists. */
    private static String stationGroup(Map<String,String> q){String st=q.getOrDefault("station","Consultation");return List.of("Registration","Triage","Consultation").contains(st)?"Consultation":st;}
    @Override public synchronized Map<String,String> callNext(String doctor,String station,String actor){
        String st=station==null||station.isBlank()?"Consultation":station;boolean consult=stationGroup(Map.of("station",st)).equals("Consultation");
        if(consult||doctor!=null&&!doctor.isBlank())raw("doctors",doctor==null||doctor.isBlank()?"missing":doctor);
        var order=Comparator.<Map<String,String>>comparingInt(r->List.of("Emergency","Urgent","Normal").indexOf(r.getOrDefault("priority","Normal"))).thenComparing(r->Objects.toString(r.get("queuedAt"),r.get("createdAt")));
        var next=store.get("queues").values().stream().filter(q->q.get("status").equals("Waiting")&&(!consult&&(doctor==null||doctor.isBlank())||Objects.equals(doctor,q.get("doctor")))&&q.containsKey("encounter")&&stationGroup(q).equals(st.equals("Triage")?"Triage":stationGroup(Map.of("station",st)))&&(!st.equals("Triage")||List.of("Registration","Triage").contains(q.getOrDefault("station","")))).sorted(order).findFirst();
        if(next.isEmpty())fail("No patients are waiting in this queue.");
        var q=next.get();return careAction(q.get("patient"),q.get("encounter"),"queue",Map.of("queue",q.get("id"),"status","Called"),actor);
    }
    // ---- Check-in channels -----------------------------------------------------------------------------------------
    @Override public synchronized Map<String,String> redeemCheckInTicket(String token,String actor){
        String t=Objects.toString(token,"").strip();if(t.startsWith("CPCI:"))t=t.substring(5);
        var ticket=checkInTokens.get(t);if(ticket==null||Long.parseLong(ticket.get("expires"))<System.currentTimeMillis()){checkInTokens.remove(t);fail("Check-in code is invalid or expired.");}
        checkInTokens.remove(t);var a=raw("appointments",ticket.get("appointment"));
        return careAction(ticket.get("patient"),"","checkin",Map.of("appointment",a.get("id"),"method","Appointment QR scanned at reception"),actor);
    }
    @Override public synchronized Map<String,String> arrivalCode(boolean rotate){
        if(rotate||arrivalCode==null||!LocalDate.now().equals(arrivalDay)){String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";var b=new StringBuilder();for(int i=0;i<8;i++)b.append(alphabet.charAt(random.nextInt(alphabet.length())));arrivalCode=b.toString();arrivalDay=LocalDate.now();}
        return Map.of("code",arrivalCode,"validOn",arrivalDay.toString());
    }
    @Override public synchronized Map<String,String> arrivalCheckIn(String patient,String appointment,String code,String actor){
        arrivalCode(false);String c=Objects.toString(code,"").strip().toUpperCase(Locale.ROOT);if(c.startsWith("ARRIVE-"))c=c.substring(7);
        if(!java.security.MessageDigest.isEqual(c.getBytes(StandardCharsets.UTF_8),arrivalCode.getBytes(StandardCharsets.UTF_8)))fail("This hospital arrival code is not valid today. Scan the poster at reception again.");
        var a=raw("appointments",appointment);if(!patient.equals(a.get("patient")))fail("Appointment does not belong to this patient.");
        return careAction(patient,"","checkin",Map.of("appointment",appointment,"method","Patient scanned hospital arrival QR"),actor);
    }
    @Override public synchronized List<Map<String,String>> checkInHistory(String id){
        return checkIns.stream().filter(c->id==null||id.isBlank()||id.equals(c.get("appointment"))||id.equals(c.get("patient"))).limit(50).map(Map::copyOf).toList();
    }

    // ---- Role dashboards ------------------------------------------------------------------------------------------
    private List<Map<String,String>> rows(String module){return store.get(module).values().stream().map(r->display(module,r)).toList();}
    private static Map<String,Object> tile(String label,Object value,String hint,String href,String tone){var m=new LinkedHashMap<String,Object>();m.put("label",label);m.put("value",value);m.put("hint",hint);m.put("href",href);m.put("tone",tone);return m;}
    private static Map<String,String> item(String title,String detail,String status,String href){var m=new LinkedHashMap<String,String>();m.put("title",title);m.put("detail",detail);m.put("status",status);m.put("href",href);return m;}
    private static long count(List<Map<String,String>> list,String status){return list.stream().filter(r->status.equals(r.get("status"))).count();}
    private Map<String,Long> byStatus(List<Map<String,String>> list,List<String> order){var m=new LinkedHashMap<String,Long>();for(String s:order)m.put(s,count(list,s));return m;}
    private long waitMinutes(Map<String,String> q){try{return java.time.Duration.between(LocalDateTime.parse(q.get("queuedAt")),LocalDateTime.now()).toMinutes();}catch(Exception e){return 0;}}

    @Override public synchronized Map<String,Object> roleDashboard(String role,String doctor) {
        String today=LocalDate.now().toString();var out=new LinkedHashMap<String,Object>();out.put("role",role);out.put("date",today);
        var appts=rows("appointments");var visits=rows("visits");var queues=rows("queues");var labs=rows("laboratory");var imaging=rows("imaging");
        var todayAppts=appts.stream().filter(a->today.equals(a.get("date"))).sorted(Comparator.comparing(a->Objects.toString(a.get("time"),""))).toList();
        var activeQueue=queues.stream().filter(q->List.of("Waiting","Called","In service","On hold").contains(q.get("status"))).toList();
        var tiles=new ArrayList<Map<String,Object>>();var lists=new LinkedHashMap<String,Object>();var actions=new ArrayList<Map<String,String>>();
        switch(role){
            case "Imaging Staff" -> {
                tiles.add(tile("Imaging requests",count(imaging,"Requested"),"Requested","/imaging?status=Requested","blue"));
                tiles.add(tile("Scheduled",count(imaging,"Scheduled"),"Scheduled","/imaging?status=Scheduled","teal"));
                tiles.add(tile("In progress",count(imaging,"In progress"),"In progress","/imaging?status=In%20progress","amber"));
                tiles.add(tile("Reported",count(imaging,"Reported"),"Awaiting approval","/imaging?status=Reported","rose"));
                lists.put("worklist",imaging.stream().filter(r->!List.of("Approved","Cancelled").contains(r.get("status"))).limit(10).map(r->item(r.get("id")+" · "+r.get("patientLabel"),r.get("name"),r.get("status"),"/imaging/"+r.get("id"))).toList());
                out.put("byStatus",byStatus(imaging,List.of("Requested","Scheduled","In progress","Reported","Reviewed","Approved")));
                actions.add(item("Imaging worklist","","","/imaging"));actions.add(item("Open queue","","","/care/queue"));
            }
            case "Doctor" -> {
                var mine=todayAppts.stream().filter(a->doctor.equals(a.get("doctor"))).toList();
                var waiting=activeQueue.stream().filter(q->doctor.equals(q.get("doctor"))&&q.get("status").equals("Waiting")&&stationGroup(q).equals("Consultation")).toList();
                var current=visits.stream().filter(v->doctor.equals(v.get("doctor"))&&v.get("status").equals("In consultation")).findFirst().orElse(null);
                var openVisits=visits.stream().filter(v->doctor.equals(v.get("doctor"))&&List.of("Waiting","In consultation").contains(v.get("status"))).map(v->v.get("id")).collect(java.util.stream.Collectors.toSet());
                var notes=careDocuments.values().stream().filter(d->d.get("kind").equals("note")&&d.get("status").equals("Draft")&&doctor.equals(d.get("doctor"))).toList();
                var noNote=visits.stream().filter(v->doctor.equals(v.get("doctor"))&&v.get("status").equals("In consultation")&&careDocuments.values().stream().noneMatch(d->d.get("kind").equals("note")&&v.get("id").equals(d.get("encounter")))).toList();
                var results=new ArrayList<Map<String,String>>();
                for(var r:labs)if(doctor.equals(r.get("doctor"))&&List.of("Result entered","Approved").contains(r.get("status"))&&!"true".equals(r.get("reviewedByDoctor")))results.add(r);
                for(var r:imaging)if(doctor.equals(r.get("doctor"))&&List.of("Reported","Approved").contains(r.get("status"))&&!"true".equals(r.get("reviewedByDoctor")))results.add(r);
                var certDrafts=careDocuments.values().stream().filter(d->d.get("kind").equals("certificate")&&d.get("status").equals("Draft")&&doctor.equals(d.get("doctor"))).toList();
                var upcoming=appts.stream().filter(a->doctor.equals(a.get("doctor"))&&a.get("date")!=null&&a.get("date").compareTo(today)>0&&List.of("Scheduled","Confirmed").contains(a.get("status"))).sorted(Comparator.comparing((Map<String,String> a)->a.get("date")).thenComparing(a->Objects.toString(a.get("time"),""))).limit(6).toList();
                tiles.add(tile("Today's appointments",mine.size(),mine.stream().filter(a->a.get("encounter")!=null).count()+" checked in","/appointments?doctor="+doctor,"blue"));
                tiles.add(tile("Patients waiting",waiting.size(),waiting.isEmpty()?"Queue is clear":"Longest wait "+waiting.stream().mapToLong(this::waitMinutes).max().orElse(0)+" min","/care/queue","amber"));
                tiles.add(tile("Pending clinical notes",notes.size()+noNote.size(),"Drafts and visits without a note","/care/tasks","teal"));
                tiles.add(tile("Results to review",results.size(),"Laboratory and imaging","/care/tasks","rose"));
                if(current!=null)out.put("current",item(current.get("patientLabel"),current.get("id")+" · "+Objects.toString(current.get("name"),""),current.get("status"),"/care/patients/"+current.get("patient")+"/encounters/"+current.get("id")));
                out.put("next",waiting.stream().min(Comparator.<Map<String,String>>comparingInt(q->List.of("Emergency","Urgent","Normal").indexOf(q.getOrDefault("priority","Normal"))).thenComparing(q->Objects.toString(q.get("queuedAt"),""))).map(q->item(q.get("patientLabel"),q.get("name")+" · "+waitMinutes(q)+" min",q.getOrDefault("priority","Normal"),"/care/queue")).orElse(null));
                lists.put("schedule",mine.stream().map(a->item(a.get("time")+" · "+a.get("patientLabel"),a.get("name")+" · "+a.get("arrivalStatus"),a.get("encounter")!=null?visits.stream().filter(v->v.get("id").equals(a.get("encounter"))).map(v->v.get("status")).findFirst().orElse(a.get("status")):a.get("status"),"/care/appointments/"+a.get("id"))).toList());
                var tasks=new ArrayList<Map<String,String>>();
                for(var v:noNote)tasks.add(item("Write clinical note · "+v.get("patientLabel"),v.get("id")+" is in consultation without a note","Note","/care/patients/"+v.get("patient")+"/encounters/"+v.get("id")+"?tab=note"));
                for(var d:notes)tasks.add(item("Sign clinical note · "+Objects.toString(store.get("patients").getOrDefault(d.get("patient"),Map.of()).get("name"),d.get("patient")),d.get("id")+" · draft v"+d.get("version"),"Draft","/care/patients/"+d.get("patient")+"/encounters/"+d.get("encounter")+"?tab=note"));
                for(var r:results)tasks.add(item("Review result · "+r.get("patientLabel"),r.get("id")+" · "+r.get("name"),r.get("status"),r.get("encounter")!=null?"/care/patients/"+r.get("patient")+"/encounters/"+r.get("encounter")+"?tab="+(r.get("id").startsWith("LAB")?"lab":"imaging"):"/"+(r.get("id").startsWith("LAB")?"laboratory":"imaging")+"/"+r.get("id")));
                for(var d:certDrafts)tasks.add(item("Issue certificate · "+Objects.toString(store.get("patients").getOrDefault(d.get("patient"),Map.of()).get("name"),d.get("patient")),d.get("id")+" · "+d.get("template"),"Draft","/care/patients/"+d.get("patient")+"/encounters/"+d.get("encounter")+"?tab=cert"));
                lists.put("tasks",tasks);
                lists.put("upcoming",upcoming.stream().map(a->item(a.get("date")+" "+a.get("time")+" · "+a.get("patientLabel"),a.get("name"),a.get("status"),"/care/appointments/"+a.get("id"))).toList());
                actions.add(item("Open my queue","","","/care/queue"));actions.add(item("My appointments","","","/appointments?doctor="+doctor));actions.add(item("Medical certificates","","","/care/certificates"));actions.add(item("Doctor Mini App","","","/mini/doctor"));
            }
            case "Receptionist" -> {
                var awaiting=todayAppts.stream().filter(a->a.get("encounter")==null&&List.of("Scheduled","Confirmed").contains(a.get("status"))).toList();
                var arrived=todayAppts.stream().filter(a->a.get("encounter")!=null).toList();
                long registrations=rows("patients").stream().filter(p->today.equals(p.get("createdAt"))).count();
                tiles.add(tile("Registrations today",registrations,"New patient records","/patients","teal"));
                tiles.add(tile("Appointments today",todayAppts.size(),count(todayAppts,"Cancelled")+" cancelled","/appointments","blue"));
                tiles.add(tile("Awaiting check-in",awaiting.size(),"Expected today","/care/checkin","amber"));
                tiles.add(tile("Checked in",arrived.size(),"Arrived today","/care/checkin","green"));
                lists.put("arrivals",awaiting.stream().map(a->item(a.get("time")+" · "+a.get("patientLabel"),a.get("doctorLabel")+" · "+a.get("departmentLabel"),"Waiting for Check-In","/care/appointments/"+a.get("id"))).toList());
                lists.put("checkins",checkIns.stream().filter(c->Objects.toString(c.get("time"),"").startsWith(today)).sorted(Comparator.comparing((Map<String,String> c)->c.get("time")).reversed()).limit(8).map(c->item(Objects.toString(store.get("patients").getOrDefault(c.get("patient"),Map.of()).get("name"),c.get("patient")),c.get("method")+" · "+Objects.toString(c.get("time"),"").substring(11,16),"Checked in","/care/appointments/"+c.get("appointment"))).toList());
                out.put("queueByStation",queueByStation(activeQueue));
                actions.add(item("Register patient","","","/patients?create=1"));actions.add(item("Book appointment","","","/appointments?create=1"));actions.add(item("Check-in desk","","","/care/checkin"));actions.add(item("Queue management","","","/care/queue"));
            }
            case "Nurse" -> {
                var triage=activeQueue.stream().filter(q->"Triage".equals(q.get("station"))).toList();
                var noVitals=visits.stream().filter(v->today.equals(v.get("date"))&&v.get("status").equals("Waiting")).toList();
                tiles.add(tile("Waiting for triage",triage.size(),"Triage station","/care/queue","amber"));
                tiles.add(tile("Visits waiting",noVitals.size(),"Before consultation","/visits?status=Waiting","blue"));
                tiles.add(tile("In consultation",count(visits,"In consultation"),"With a doctor","/visits?status=In%20consultation","teal"));
                lists.put("triage",triage.stream().map(q->item(q.get("name")+" · "+q.get("patientLabel"),q.getOrDefault("priority","Normal")+" · "+waitMinutes(q)+" min",q.get("status"),q.get("encounter")!=null?"/care/patients/"+q.get("patient")+"/encounters/"+q.get("encounter")+"?tab=triage":"/care/queue")).toList());
                out.put("queueByStation",queueByStation(activeQueue));
                actions.add(item("Triage queue","","","/care/queue"));actions.add(item("Check-in desk","","","/care/checkin"));actions.add(item("Assigned patients","","","/visits"));
            }
            case "Laboratory Staff" -> {
                tiles.add(tile("Samples to collect",count(labs,"Ordered"),"Ordered tests","/laboratory?status=Ordered","amber"));
                tiles.add(tile("Processing",count(labs,"Collected")+count(labs,"Processing"),"Collected or running","/laboratory?status=Processing","blue"));
                tiles.add(tile("Results to approve",count(labs,"Result entered"),"Awaiting approval","/laboratory?status=Result%20entered","teal"));
                tiles.add(tile("Imaging requests",count(imaging,"Requested")+count(imaging,"Scheduled"),"Requested or scheduled","/imaging","rose"));
                lists.put("worklist",labs.stream().filter(r->List.of("Ordered","Collected","Processing","Result entered").contains(r.get("status"))).limit(10).map(r->item(r.get("id")+" · "+r.get("patientLabel"),r.get("name"),r.get("status"),"/laboratory/"+r.get("id"))).toList());
                out.put("byStatus",byStatus(labs,List.of("Ordered","Collected","Processing","Result entered","Approved")));
                actions.add(item("Laboratory worklist","","","/laboratory"));actions.add(item("Imaging worklist","","","/imaging"));
            }
            case "Pharmacist" -> {
                var rx=rows("pharmacy");var stock=rows("stock");
                tiles.add(tile("Prescriptions to review",count(rx,"Pending"),"Pending","/pharmacy?status=Pending","amber"));
                tiles.add(tile("Ready to dispense",count(rx,"Reviewed"),"Reviewed","/pharmacy?status=Reviewed","teal"));
                tiles.add(tile("Low stock",count(stock,"Low stock"),"At or below reorder level","/stock?status=Low%20stock","rose"));
                tiles.add(tile("Expired batches",count(stock,"Expired"),"Remove from use","/stock?status=Expired","grey"));
                lists.put("worklist",rx.stream().filter(r->List.of("Pending","Reviewed").contains(r.get("status"))).limit(10).map(r->item(r.get("id")+" · "+r.get("patientLabel"),r.get("medicineLabel")+" · "+r.get("dosage"),r.get("status"),"/pharmacy/"+r.get("id"))).toList());
                actions.add(item("Prescriptions","","","/pharmacy?status=Pending"));actions.add(item("Medicine stock","","","/stock"));
            }
            case "Cashier" -> {
                var bills=rows("billing");
                tiles.add(tile("Draft invoices",count(bills,"Draft"),"Prepare and issue","/billing?status=Draft","amber"));
                tiles.add(tile("Awaiting payment",count(bills,"Issued")+count(bills,"Partially paid"),"Issued or part-paid","/billing?status=Issued","blue"));
                tiles.add(tile("Outstanding balance",money(bills.stream().filter(b->!b.get("status").equals("Void")).mapToDouble(b->Double.parseDouble(b.get("balance"))).sum()),"Across open invoices","/billing","rose"));
                lists.put("worklist",bills.stream().filter(b->List.of("Draft","Issued","Partially paid").contains(b.get("status"))).limit(10).map(b->item(b.get("id")+" · "+b.get("patientLabel"),"Balance "+b.get("balance"),b.get("status"),"/billing/"+b.get("id"))).toList());
                actions.add(item("Invoices","","","/billing"));actions.add(item("Payments","","","/payments"));
            }
            default -> { // Administrators
                var bills=rows("billing");var doctors=rows("doctors");var staff=rows("staff");
                var visitsToday=visits.stream().filter(v->today.equals(v.get("date"))).toList();
                tiles.add(tile("Appointments today",todayAppts.size(),count(todayAppts,"Completed")+" completed","/appointments","blue"));
                tiles.add(tile("Patient visits today",visitsToday.size(),count(visitsToday,"In consultation")+" in consultation","/visits","teal"));
                tiles.add(tile("Patients in queue",activeQueue.size(),"Waiting, called, in service or on hold","/care/queue","amber"));
                tiles.add(tile("Outstanding balance",money(bills.stream().filter(b->!b.get("status").equals("Void")).mapToDouble(b->Double.parseDouble(b.get("balance"))).sum()),count(bills,"Paid")+" invoices paid","/billing","rose"));
                var dept=new LinkedHashMap<String,Map<String,Long>>();
                for(var d:rows("departments")){var m=new LinkedHashMap<String,Long>();m.put("appointments",todayAppts.stream().filter(a->d.get("id").equals(a.get("department"))).count());m.put("visits",visitsToday.stream().filter(v->d.get("id").equals(v.get("department"))).count());dept.put(d.get("name"),m);}
                out.put("departments",dept);
                out.put("staff",Map.of("doctors",byStatus(doctors,List.of("Available","On duty","On leave")),"staff",byStatus(staff,List.of("Active","On leave","Inactive"))));
                var done=queues.stream().filter(q->q.get("status").equals("Completed")&&q.get("calledAt")!=null&&q.get("queuedAt")!=null).mapToLong(q->{try{return java.time.Duration.between(LocalDateTime.parse(q.get("queuedAt")),LocalDateTime.parse(q.get("calledAt"))).toMinutes();}catch(Exception e){return -1;}}).filter(x->x>=0).summaryStatistics();
                out.put("queue",Map.of("byStatus",byStatus(queues,List.of("Waiting","Called","In service","On hold","Transferred","Completed","Missed")),"averageWait",done.getCount()==0?"—":Math.round(done.getAverage())+" min","longestWait",activeQueue.stream().filter(q->q.get("status").equals("Waiting")).mapToLong(this::waitMinutes).max().orElse(0)+" min"));
                out.put("billing",Map.of("byStatus",byStatus(bills,List.of("Draft","Issued","Partially paid","Paid","Void")),"invoiced",money(bills.stream().filter(b->!b.get("status").equals("Void")&&!b.get("status").equals("Draft")).mapToDouble(b->Double.parseDouble(b.get("total"))).sum()),"collected",money(bills.stream().mapToDouble(b->Double.parseDouble(b.get("paid"))).sum())));
                out.put("queueByStation",queueByStation(activeQueue));
                actions.add(item("Hospital analytics","","","/overview"));actions.add(item("Staff","","","/staff"));actions.add(item("Roles and permissions","","","/access"));actions.add(item("Telegram Mini App","","","/settings/telegram"));
            }
        }
        if(role.equals("Hospital Director")){
            actions.clear();actions.add(item("Hospital analytics","","","/overview"));actions.add(item("Reports","","","/reports"));actions.add(item("Audit Logs","","","/audit"));
            for(var t:tiles)if(List.of("/appointments","/visits","/care/queue").contains(t.get("href")))t.put("href","/overview");
        }
        out.put("tiles",tiles);out.put("lists",lists);out.put("actions",actions);return out;
    }
    private Map<String,Long> queueByStation(List<Map<String,String>> active){var m=new LinkedHashMap<String,Long>();for(String st:List.of("Triage","Consultation","Laboratory","Imaging","Pharmacy","Payment","Doctor review"))m.put(st,active.stream().filter(q->st.equals(q.getOrDefault("station","Consultation"))).count());return m;}

    @Override public synchronized List<Map<String,String>> clinicalDocuments(String kind,String doctor) {
        return careDocuments.values().stream().filter(d->kind.equals(d.get("kind"))&&(doctor==null||doctor.isBlank()||doctor.equals(d.get("doctor")))).map(d->{var m=new LinkedHashMap<>(d);var p=store.get("patients").get(d.get("patient"));m.put("patientLabel",p==null?"—":p.get("name"));var doc=store.get("doctors").get(d.get("doctor"));m.put("doctorLabel",doc==null?"—":doc.get("name"));return (Map<String,String>)m;}).sorted(Comparator.comparing((Map<String,String> d)->Objects.toString(d.get("id"),"")).reversed()).toList();
    }
}
