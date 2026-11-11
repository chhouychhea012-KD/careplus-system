package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Regression coverage for the review fixes: transfers, cancellations, order cancellation and encounter charges. */
class WorkflowIntegrityTests {
    HospitalService service;String patient,encounter,appointment;
    static Map<String,String> values(String... pairs){var out=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)out.put(pairs[i],pairs[i+1]);return out;}
    @BeforeEach void setup(){
        service=new InMemoryHospitalService(new ModuleCatalog());
        patient=service.save("patients",null,values("name","Synthetic integrity patient","gender","Other","dateOfBirth","1988-02-02","phone","00000000"),"QA").get("id");
        appointment=service.save("appointments",null,values("name","Integrity visit","patient",patient,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().toString(),"time","16:41","type","Consultation","reason","Synthetic review"),"QA").get("id");
        encounter=service.careAction(patient,"","checkin",values("appointment",appointment),"QA").get("id");
    }
    Map<String,String> act(String action,String... pairs){return service.careAction(patient,encounter,action,values(pairs),"QA clinician");}
    @SuppressWarnings("unchecked") List<Map<String,String>> current(String module){return ((Map<String,List<Map<String,String>>>)service.careContext(patient,encounter).get("current")).get(module);}
    List<Map<String,String>> openQueues(){return current("queues").stream().filter(q->List.of("Waiting","Called","In service","On hold").contains(q.get("status"))).toList();}
    void signNote(){var note=act("note","subjective","Synthetic history","assessment","Clinician-entered assessment");act("sign","document",note.get("id"),"reviewed","true");}

    @Test void checkedInAppointmentCannotBeCancelledWhileVisitIsOpen(){
        assertThat(service.get("appointments",appointment).get("_actions")).doesNotContain("Cancel");
        assertThatThrownBy(()->service.transition("appointments",appointment,"Cancel",Map.of(),"QA")).hasMessageContaining("not available");
        var other=service.save("appointments",null,values("name","Future booking","patient",patient,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().plusDays(1).toString(),"time","10:00","type","Consultation"),"QA");
        assertThat(service.transition("appointments",other.get("id"),"Cancel",Map.of(),"QA").get("status")).isEqualTo("Cancelled");
    }

    @Test void genericQueueTransferOpensDestinationStepForSameEncounter(){
        var q=openQueues().getFirst();
        service.transition("queues",q.get("id"),"Transfer",values("department","DEP-0002","doctor","DOC-0002"),"QA");
        assertThat(service.get("queues",q.get("id")).get("status")).isEqualTo("Transferred");
        var open=openQueues();assertThat(open).hasSize(1);
        assertThat(open.getFirst().get("doctor")).isEqualTo("DOC-0002");assertThat(open.getFirst().get("encounter")).isEqualTo(encounter);assertThat(open.getFirst().get("fromQueue")).isEqualTo(q.get("id"));
        assertThat(service.get("visits",encounter).get("doctor")).isEqualTo("DOC-0002");
        assertThatThrownBy(()->service.transition("queues",open.getFirst().get("id"),"Transfer",values("department","DEP-0003","doctor","DOC-0001"),"QA")).hasMessageContaining("assigned to this department");
    }

    @Test void doctorTransferKeepsEncounterOpenAndRequeuesPatient(){
        act("start");
        var visit=service.transition("visits",encounter,"Transfer",values("department","DEP-0002","doctor","DOC-0002","reason","Specialist opinion"),"QA");
        assertThat(visit.get("status")).isEqualTo("Waiting");assertThat(visit.get("doctor")).isEqualTo("DOC-0002");
        assertThat(openQueues()).singleElement().satisfies(q->{assertThat(q.get("doctor")).isEqualTo("DOC-0002");assertThat(q.get("status")).isEqualTo("Waiting");});
        assertThat(act("start").get("status")).isEqualTo("In consultation");
        assertThatThrownBy(()->service.transition("visits",encounter,"Transfer",values("department","DEP-0002"),"QA")).hasMessageContaining("receiving doctor");
    }

    @Test void cancellingVisitCancelsOpenQueueSteps(){
        service.transition("visits",encounter,"Cancel",Map.of(),"QA");
        assertThat(openQueues()).isEmpty();
        assertThat(service.get("appointments",appointment).get("_actions")).contains("Cancel");
    }

    @Test void queueInServiceCanBeCompletedFromQueueModule(){
        act("start");var q=openQueues().getFirst();assertThat(q.get("status")).isEqualTo("In service");
        assertThat(q.get("_actions")).contains("Complete");
        assertThat(service.transition("queues",q.get("id"),"Complete",Map.of(),"QA").get("status")).isEqualTo("Completed");
        assertThat(service.get("visits",encounter).get("status")).as("queue completion is not encounter completion").isEqualTo("In consultation");
        assertThat(service.list("queues","","In service","","","",1,5).counts()).containsKey("Waiting");
    }

    @Test void unneededOrderCanBeCancelledSoVisitCanBeDischarged(){
        act("start");signNote();
        var order=act("order","category","Laboratory","test","TST-0001","indication","Synthetic indication","priority","Normal");
        assertThatThrownBy(()->act("complete","plan","Synthetic plan")).hasMessageContaining("Results are pending");
        assertThatThrownBy(()->act("cancel-order","module","laboratory","order",order.get("id"))).hasMessageContaining("reason");
        assertThat(act("cancel-order","module","laboratory","order",order.get("id"),"reason","Ordered in error").get("status")).isEqualTo("Cancelled");
        assertThatThrownBy(()->act("cancel-order","module","laboratory","order",order.get("id"),"reason","Again")).hasMessageContaining("cannot be cancelled");
        act("complete","plan","Synthetic discharge plan");
        assertThat(service.get("visits",encounter).get("status")).isEqualTo("Completed");
        var invoice=current("billing");assertThat(invoice).singleElement().satisfies(b->{assertThat(b.get("status")).isEqualTo("Draft");assertThat(b.get("items")).contains("Consultation").doesNotContain("Complete blood count");});
    }

    @Test void dischargeDraftsInvoiceWithApprovedResultsOnce(){
        act("start");signNote();
        String lab=act("order","category","Laboratory","test","TST-0001","indication","Synthetic indication","priority","Normal").get("id");
        for(String step:List.of("Collect sample","Start test"))service.transition("laboratory",lab,step,Map.of(),"Lab QA");
        var row=new LinkedHashMap<>(service.get("laboratory",lab));row.put("result","Synthetic result within demo range");service.save("laboratory",lab,row,"Lab QA");
        for(String step:List.of("Enter result","Review","Approve"))service.transition("laboratory",lab,step,Map.of(),"Lab QA");
        act("doctor-review","module","laboratory","order",lab);
        act("complete","plan","Synthetic discharge plan");
        var bills=current("billing");assertThat(bills).hasSize(1);
        var bill=bills.getFirst();assertThat(bill.get("encounter")).isEqualTo(encounter);assertThat(bill.get("patient")).isEqualTo(patient);
        assertThat(bill.get("items").split("\\R")).hasSize(2);assertThat(Double.parseDouble(bill.get("total"))).isPositive();
        assertThatThrownBy(()->act("complete","plan","Again")).hasMessageContaining("closed");
    }
}
