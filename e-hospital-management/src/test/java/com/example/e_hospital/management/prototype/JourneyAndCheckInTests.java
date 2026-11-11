package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Derived patient journey, queue ordering actions and the three check-in channels. */
class JourneyAndCheckInTests {
    InMemoryHospitalService service;String patient,appointment;
    static Map<String,String> values(String... p){var m=new LinkedHashMap<String,String>();for(int i=0;i<p.length;i+=2)m.put(p[i],p[i+1]);return m;}
    String newPatient(String name){return service.save("patients",null,values("name",name,"gender","Other","dateOfBirth","1990-01-01","phone","00000000"),"QA").get("id");}
    String book(String p,String time){return service.save("appointments",null,values("name","Journey","patient",p,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().toString(),"time",time,"type","Consultation"),"QA").get("id");}
    @BeforeEach void setup(){service=new InMemoryHospitalService(new ModuleCatalog());
        // Seeded demo tickets for DOC-0001 would legitimately be called first; clear them so ordering is deterministic.
        for(var q:service.list("queues","","Waiting","doctor=DOC-0001","","",1,100).items())service.transition("queues",q.get("id"),"Cancel",Map.of(),"QA");
        patient=newPatient("Synthetic journey patient");appointment=book(patient,"16:30");}
    @SuppressWarnings("unchecked") Map<String,String> step(String key){return ((List<Map<String,String>>)service.journey(appointment).get("steps")).stream().filter(s->s.get("key").equals(key)).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") Map<String,String> next(){return (Map<String,String>)service.journey(appointment).get("next");}
    String encounter(){return service.get("appointments",appointment).get("encounter");}

    @Test void journeyFollowsRealStateFromBookingToDischarge(){
        assertThat(step("checkin").get("status")).isEqualTo("current");assertThat(next().get("action")).isEqualTo("checkin");
        assertThat(step("consultation").get("status")).isEqualTo("pending");
        service.careAction(patient,"","checkin",values("appointment",appointment),"QA");
        assertThat(step("checkin").get("status")).isEqualTo("done");assertThat(step("triage").get("status")).isEqualTo("current");
        service.careAction(patient,encounter(),"triage",values("complaint","Synthetic","priority","Normal"),"Nurse QA");
        assertThat(step("triage").get("status")).isEqualTo("done");assertThat(step("consultation").get("status")).isEqualTo("current");assertThat(step("consultation").get("detail")).contains("position 1");
        service.careAction(patient,encounter(),"start",Map.of(),"Dr QA");
        var lab=service.careAction(patient,encounter(),"order",values("category","Laboratory","test","TST-0001","indication","Synthetic"),"Dr QA").get("id");
        assertThat(step("orders").get("status")).isEqualTo("blocked");assertThat(step("completion").get("status")).isEqualTo("blocked");
        service.careAction(patient,encounter(),"cancel-order",values("module","laboratory","order",lab,"reason","Not needed"),"Dr QA");
        assertThat(step("orders").get("detail")).contains("No laboratory");
        var note=service.careAction(patient,encounter(),"note",values("subjective","s","assessment","a"),"Dr QA");service.careAction(patient,encounter(),"sign",values("document",note.get("id"),"reviewed","true"),"Dr QA");
        service.careAction(patient,encounter(),"complete",values("plan","Synthetic plan"),"Dr QA");
        assertThat(step("consultation").get("status")).isEqualTo("done");assertThat(step("completion").get("status")).isEqualTo("done");
        assertThat(step("pharmacy-billing").get("status")).isEqualTo("current");assertThat(step("pharmacy-billing").get("owner")).isEqualTo("Cashier");
        assertThat(service.journey(encounter()).get("appointment")).isNotEqualTo(Map.of());
    }
    @Test void cancelledAndFutureAppointmentsShowCorrectState(){
        var future=service.save("appointments",null,values("name","Later","patient",patient,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().plusDays(3).toString(),"time","10:00","type","Consultation"),"QA").get("id");
        appointment=future;assertThat(step("checkin").get("status")).isEqualTo("pending");
        service.transition("appointments",future,"Cancel",Map.of(),"QA");assertThat(step("checkin").get("status")).isEqualTo("cancelled");assertThat(next()).isEmpty();
    }
    @Test void callNextUsesPriorityThenArrivalAndSkipRequeuesAtEnd(){
        var other=newPatient("Synthetic urgent patient");var otherAppt=book(other,"16:31");
        service.careAction(patient,"","checkin",values("appointment",appointment),"QA");service.careAction(other,"","checkin",values("appointment",otherAppt),"QA");
        String otherEnc=service.get("appointments",otherAppt).get("encounter");service.careAction(other,otherEnc,"triage",values("complaint","x","priority","Urgent"),"QA");
        var called=service.callNext("DOC-0001","Consultation","Dr QA");assertThat(called.get("patient")).isEqualTo(other);assertThat(called.get("status")).isEqualTo("Called");
        service.careAction(other,otherEnc,"queue",values("queue",called.get("id"),"status","Waiting"),"Dr QA");
        assertThat(service.get("queues",called.get("id")).get("status")).isEqualTo("Waiting");
        assertThat(service.callNext("DOC-0001","Consultation","Dr QA").get("patient")).as("urgent stays first after skip").isEqualTo(other);
        assertThatThrownBy(()->service.callNext("DOC-0005","Consultation","QA")).hasMessageContaining("No patients");
    }
    @Test void holdAndResumeKeepTheTicket(){
        service.careAction(patient,"","checkin",values("appointment",appointment),"QA");var q=service.callNext("DOC-0001","Consultation","QA");
        service.careAction(patient,encounter(),"queue",values("queue",q.get("id"),"status","On hold"),"QA");
        assertThat(service.careAction(patient,encounter(),"queue",values("queue",q.get("id"),"status","Waiting"),"QA").get("status")).isEqualTo("Waiting");
    }
    @Test void appointmentTicketIsSingleUseAndRecordedInHistory(){
        var token=service.checkInToken(appointment,patient,"","Patient app").get("token");
        var visit=service.redeemCheckInTicket("CPCI:"+token,"Reception QA");assertThat(visit.get("id")).startsWith("VIS-");
        assertThatThrownBy(()->service.redeemCheckInTicket(token,"Reception QA")).hasMessageContaining("invalid or expired");
        assertThat(service.checkInHistory(appointment)).singleElement().satisfies(c->{assertThat(c.get("method")).contains("QR");assertThat(c.get("encounter")).isEqualTo(visit.get("id"));});
        assertThat(service.careAction(patient,"","checkin",values("appointment",appointment),"QA").get("id")).as("no duplicate encounter").isEqualTo(visit.get("id"));
        assertThat(service.checkInHistory(appointment)).hasSize(1);
    }
    @Test void arrivalCodeChecksInOwnAppointmentOnlyAndRotates(){
        String code=service.arrivalCode(false).get("code");assertThat(code).hasSize(8);
        assertThatThrownBy(()->service.arrivalCheckIn(patient,appointment,"WRONG123","Patient")).hasMessageContaining("not valid");
        var stranger=newPatient("Synthetic stranger");assertThatThrownBy(()->service.arrivalCheckIn(stranger,appointment,code,"Stranger")).hasMessageContaining("does not belong");
        String rotated=service.arrivalCode(true).get("code");assertThat(rotated).isNotEqualTo(code);
        assertThatThrownBy(()->service.arrivalCheckIn(patient,appointment,code,"Patient")).hasMessageContaining("not valid");
        assertThat(service.arrivalCheckIn(patient,appointment,"arrive-"+rotated.toLowerCase(),"Patient").get("id")).startsWith("VIS-");
        assertThat(service.checkInHistory(patient).getFirst().get("method")).contains("arrival QR");
    }
    @Test void notificationsAreOptInAndContainNoClinicalDetail(){
        var telegram=new TelegramAccessService("1:T","integration",false,java.time.Duration.ofMinutes(15),java.time.Clock.systemUTC());
        service.onEvent((type,facts)->telegram.notifyPatient(facts.get("patient"),type,facts));
        var code=telegram.createLinkCode("Patient",patient,"QA").get("code");telegram.redeemLinkCode(code,"555001","Demo");
        service.careAction(patient,"","checkin",values("appointment",appointment),"QA");service.callNext("DOC-0001","Consultation","QA");
        assertThat(telegram.outbox()).as("opted out by default").isEmpty();
        telegram.setNotifications("555001",true);service.careAction(patient,encounter(),"start",Map.of(),"QA");
        var q=service.careAction(patient,encounter(),"queue",values("queue",service.journey(appointment).get("queue") instanceof Map<?,?> m?(String)m.get("id"):"","status","Transferred","station","Laboratory"),"QA");
        service.callNext("DOC-0001","Laboratory","QA");
        assertThat(telegram.outbox()).isNotEmpty().allSatisfy(m->{assertThat(m.get("text")).startsWith("CarePlus:").doesNotContain("Laboratory").doesNotContain(patient);assertThat(m.get("status")).contains("sending disabled");});
    }
}
