package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc
class ConnectedCareTests {
    @Autowired MockMvc mvc;
    HospitalService service;String patient,encounter,appointment;
    static Map<String,String> values(String... pairs){var out=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)out.put(pairs[i],pairs[i+1]);return out;}
    @BeforeEach void setup(){service=new InMemoryHospitalService(new ModuleCatalog());patient=service.save("patients",null,values("name","Synthetic connected patient","gender","Other","dateOfBirth","1990-01-01","phone","00000000"),"QA").get("id");appointment=service.save("appointments",null,values("name","QA connected appointment","patient",patient,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().toString(),"time","16:43","type","Consultation","reason","Synthetic assessment"),"QA").get("id");encounter=service.careAction(patient,"","checkin",values("appointment",appointment),"QA").get("id");}
    Map<String,String> act(String action,String... pairs){return service.careAction(patient,encounter,action,values(pairs),"QA clinician");}
    @SuppressWarnings("unchecked") List<Map<String,String>> current(String module){return ((Map<String,List<Map<String,String>>>)service.careContext(patient,encounter).get("current")).get(module);}
    MockHttpSession session(String role){var s=new MockHttpSession();s.setAttribute("demoUser","synthetic@example.test");s.setAttribute("demoRole",role);return s;}
    @Test void checkinMaintainsStableLinksAndPreventsDuplicateVisits(){
        assertThat(service.get("appointments",appointment).get("encounter")).isEqualTo(encounter);assertThat(current("queues")).hasSize(1);assertThat(current("queues").getFirst().get("patient")).isEqualTo(patient);
        assertThat(service.careAction(patient,"","checkin",values("appointment",appointment),"QA").get("id")).isEqualTo(encounter);assertThat(current("queues")).hasSize(1);
        assertThatThrownBy(()->service.save("visits",null,service.get("visits",encounter),"QA")).hasMessageContaining("active visit already exists");
        var other=service.save("appointments",null,values("name","Second booking","patient",patient,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().toString(),"time","16:44","type","Consultation"),"QA");
        assertThatThrownBy(()->service.careAction(patient,"","checkin",values("appointment",other.get("id")),"QA")).hasMessageContaining("active visit already exists");
        assertThat(service.get("appointments",appointment).get("status")).isEqualTo("Scheduled");
    }
    @Test void triageValidatesVitalsAndPreservesPatientIsolation(){
        assertThatThrownBy(()->act("triage","complaint","Synthetic","priority","Urgent","oxygen","101")).hasMessageContaining("units");
        assertThatThrownBy(()->act("triage","complaint","Synthetic","priority","Normal","bloodPressure","120")).hasMessageContaining("mmHg");
        var triage=act("triage","complaint","Synthetic","priority","Urgent","bloodPressure","120/80","oxygen","97","temperature","36.8");assertThat(triage.get("encounter")).isEqualTo(encounter);assertThat(current("queues").getFirst().get("priority")).isEqualTo("Urgent");
        assertThatThrownBy(()->service.careContext("PAT-0002",encounter)).hasMessageContaining("does not belong");
    }
    @Test void noteDraftVersionsAndSignaturesAreImmutable(){
        act("start");var note=act("note","subjective","Synthetic history","assessment","Clinician entered");
        assertThatThrownBy(()->act("complete","plan","Follow up")).hasMessageContaining("Sign draft notes");
        var revised=act("note","document",note.get("id"),"version",note.get("version"),"subjective","Updated history","assessment","Reviewed");assertThat(revised.get("version")).isEqualTo("2");
        assertThatThrownBy(()->act("note","document",note.get("id"),"version","1","subjective","Old","assessment","Old")).hasMessageContaining("changed");
        assertThatThrownBy(()->act("sign","document",note.get("id"))).hasMessageContaining("review");
        act("sign","document",note.get("id"),"reviewed","true");assertThatThrownBy(()->act("note","document",note.get("id"),"version","2","subjective","Overwrite","assessment","Overwrite")).hasMessageContaining("signed");
        var amendment=act("note","amends",note.get("id"),"subjective","Amended history","assessment","Reviewed correction");assertThat(amendment.get("amends")).isEqualTo(note.get("id"));
    }
    @Test void labImagingAndPrescribingShareEncounterAndPatientProjection(){
        act("start");var lab=act("order","category","Laboratory","test","TST-0001","indication","Synthetic indication");var image=act("order","category","Imaging","name","Synthetic chest examination","modality","X-ray","indication","Synthetic indication");
        assertThat(current("laboratory")).extracting(r->r.get("id")).containsExactly(lab.get("id"));assertThat(current("imaging")).extracting(r->r.get("id")).containsExactly(image.get("id"));
        assertThatThrownBy(()->act("complete","plan","Review")).hasMessageContaining("pending");
        for(String a:List.of("Collect sample","Start test"))service.transition("laboratory",lab.get("id"),a,Map.of(),"QA lab");
        var result=service.get("laboratory",lab.get("id"));result.put("result","Synthetic test result");service.save("laboratory",lab.get("id"),result,"QA lab");for(String a:List.of("Enter result","Review","Approve"))service.transition("laboratory",lab.get("id"),a,Map.of(),"QA lab");
        for(String a:List.of("Schedule","Start examination"))service.transition("imaging",image.get("id"),a,Map.of(),"QA imaging");var report=service.get("imaging",image.get("id"));report.put("report","Synthetic report only");service.save("imaging",image.get("id"),report,"QA imaging");for(String a:List.of("Submit report","Review","Approve"))service.transition("imaging",image.get("id"),a,Map.of(),"QA imaging");
        assertThat((List<?>)service.mobileContext("Patient",patient).get("laboratory")).isEmpty();
        act("release","module","laboratory","order",lab.get("id"));assertThat((List<?>)service.mobileContext("Patient",patient).get("laboratory")).hasSize(1);
        act("doctor-review","module","laboratory","order",lab.get("id"));act("doctor-review","module","imaging","order",image.get("id"));
        var rx=act("prescription","medicine","MED-0001","quantity","2","duration","2","frequency","Once daily","dosage","Clinician entered mock dosing");assertThat(rx.get("encounter")).isEqualTo(encounter);assertThat(rx.get("status")).isEqualTo("Pending");
        act("followup","followupDate",LocalDate.now().plusDays(14).toString(),"time","16:43","reason","Synthetic follow-up");
        act("complete","plan","Reviewed results and follow-up plan");assertThat(service.get("visits",encounter).get("status")).isEqualTo("Completed");assertThat(service.get("appointments",appointment).get("status")).isEqualTo("Completed");
        assertThat(service.get("pharmacy",rx.get("id")).get("status")).isEqualTo("Pending");
    }
    @Test void certificateRequiresReviewAndKeepsIssuedVersion(){var draft=act("certificate","template","Consultation Attendance Certificate","text","Attended this synthetic demonstration encounter.");assertThat((List<?>)service.mobileContext("Patient",patient).get("documents")).isEmpty();act("issue","document",draft.get("id"),"reviewed","true");assertThat((List<?>)service.mobileContext("Patient",patient).get("documents")).hasSize(1);assertThatThrownBy(()->act("certificate","document",draft.get("id"),"version","1","template","Consultation Attendance Certificate","text","Overwrite")).hasMessageContaining("signed");}
    @Test void queueTransferCreatesDestinationWithoutClosingEncounter(){var q=current("queues").getFirst();act("queue","queue",q.get("id"),"status","Called");act("queue","queue",q.get("id"),"status","On hold");act("queue","queue",q.get("id"),"status","Waiting");act("queue","queue",q.get("id"),"status","Transferred","station","Laboratory","doctor","DOC-0001");assertThat(current("queues")).hasSize(2);assertThat(current("queues")).anyMatch(r->r.get("status").equals("Waiting")&&r.get("station").equals("Laboratory"));assertThat(service.get("visits",encounter).get("status")).isEqualTo("Waiting");}
    @Test void checkinTokensAreScopedAndSingleUse(){var a=service.save("appointments",null,values("name","Token appointment","patient","PAT-0018","doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().toString(),"time","16:45","type","Consultation"),"QA");var token=service.checkInToken(a.get("id"),"PAT-0018","","QA");assertThat(token.get("token")).doesNotContain("PAT-");assertThatThrownBy(()->service.checkInToken(appointment,patient,token.get("token"),"QA")).hasMessageContaining("invalid or expired");service.checkInToken(a.get("id"),"PAT-0018",token.get("token"),"QA");assertThatThrownBy(()->service.checkInToken(a.get("id"),"PAT-0018",token.get("token"),"QA")).hasMessageContaining("invalid or expired");}
    @Test void serverRejectsRoleAndPatientEscalation()throws Exception {
        var patientSession=session("Patient");mvc.perform(get("/api/care/mobile").session(patientSession)).andExpect(status().isOk()).andExpect(jsonPath("$.patient.id").value("PAT-0001"));mvc.perform(get("/api/records/patients/PAT-0002").session(patientSession)).andExpect(status().isForbidden());mvc.perform(get("/api/care/context?patient=PAT-0001").session(patientSession)).andExpect(status().isForbidden());mvc.perform(post("/api/role").session(patientSession).with(csrf()).contentType("application/json").content("{\"role\":\"Doctor\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/records/pharmacy").session(session("Pharmacist")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/care/action").session(session("Receptionist")).with(csrf()).contentType("application/json").content("{\"action\":\"note\",\"patient\":\"PAT-0001\",\"encounter\":\"VIS-0001\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/context?patient=PAT-0002&encounter=VIS-0002").session(session("Doctor"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/care/telegram/link").session(patientSession).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isNotImplemented());
    }
    @Test void newRoutesRender()throws Exception {for(String route:List.of("/care/queue","/mini/doctor","/care/patients/PAT-0001","/care/patients/PAT-0001/encounters/VIS-0001"))mvc.perform(get(route).session(session("Administrator"))).andExpect(status().isOk());mvc.perform(get("/mini/patient").session(session("Patient"))).andExpect(status().isOk());mvc.perform(get("/ui/locales/km.json")).andExpect(status().isOk());}
    @Test void prescriptionDraftSubmissionAndMultiTestOrder(){
        act("start");var draft=act("prescription-draft","medicine","MED-0001","quantity","2","duration","2","frequency","Once daily","dosage","Synthetic clinician directions","location","PHL-0001");assertThat(current("pharmacy")).isEmpty();assertThat(draft.get("status")).isEqualTo("Draft");
        var rx=act("submit-prescription","document",draft.get("id"),"reviewed","true");assertThat(rx.get("status")).isEqualTo("Pending");assertThat(current("pharmacy")).hasSize(1);assertThatThrownBy(()->act("submit-prescription","document",draft.get("id"),"reviewed","true")).hasMessageContaining("draft");
        act("order","category","Laboratory","tests","TST-0001,TST-0002","indication","Synthetic multi-test order");assertThat(current("laboratory")).hasSize(2);
        assertThatThrownBy(()->act("order","category","Laboratory","tests","TST-0001,INVALID","indication","Synthetic")).hasMessageContaining("not found");assertThat(current("laboratory")).hasSize(2);
    }
    @Test void duplicateRegistrationAndEncounterReassignmentAreRejected(){
        assertThatThrownBy(()->service.save("patients",null,values("name","Synthetic connected patient","dateOfBirth","1990-01-01","gender","Other","phone","111111"),"QA")).hasMessageContaining("already exists");
        var lab=act("order","category","Laboratory","test","TST-0001","indication","Synthetic");var edit=new LinkedHashMap<>(lab);edit.put("patient","PAT-0002");edit.remove("encounter");assertThatThrownBy(()->service.save("laboratory",lab.get("id"),edit,"QA")).hasMessageContaining("cannot be changed");assertThat(service.get("laboratory",lab.get("id")).get("patient")).isEqualTo(patient);
    }

}
