package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"careplus.telegram.bot-token=SYNTHETIC-TEST-ONLY","careplus.telegram.webhook-secret=SYNTHETIC-WEBHOOK-SECRET","careplus.telegram.webhook-enabled=true"})
@AutoConfigureMockMvc
class RequirementsRegressionTests {
    @Autowired MockMvc mvc;
    static Map<String,String> values(String... v){var out=new LinkedHashMap<String,String>();for(int i=0;i<v.length;i+=2)out.put(v[i],v[i+1]);return out;}
    MockHttpSession session(String role){var s=new MockHttpSession();s.setAttribute("demoUser","synthetic@example.test");s.setAttribute("demoRole",role);return s;}
    @Test void webhookRequiresSecretWithoutDependingOnBrowserCsrf()throws Exception{
        String body="{\"update_id\":91001,\"message\":{\"chat\":{\"id\":111,\"type\":\"private\"},\"from\":{\"id\":111},\"text\":\"/start\"}}";
        mvc.perform(post("/api/telegram/webhook").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token","forged").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token","SYNTHETIC-WEBHOOK-SECRET").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.method").value("sendMessage")).andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("verified hospital account")));
        mvc.perform(post("/api/telegram/webhook").header("X-Telegram-Bot-Api-Secret-Token","SYNTHETIC-WEBHOOK-SECRET").contentType("application/json").content(body)).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.method").doesNotExist());
        mvc.perform(post("/api/telegram/bot-preview").session(session("Administrator")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void previewIsBilingualAdminOnlyAndNeverExposesSecrets()throws Exception{
        mvc.perform(post("/api/telegram/bot-preview").session(session("Administrator")).with(csrf()).contentType("application/json").content("{\"command\":\"/help\",\"language\":\"km\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("ប្រើ")));
        mvc.perform(get("/api/telegram/bot-status").session(session("Administrator"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SYNTHETIC-WEBHOOK-SECRET"))));
        mvc.perform(get("/api/telegram/bot-status").session(session("Doctor"))).andExpect(status().isForbidden());
    }
    @Test void directorIsReadOnlyAndImagingCannotModifyLab()throws Exception{
        mvc.perform(get("/").session(session("Hospital Director"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Hospital Director")));
        mvc.perform(get("/api/care/dashboard").session(session("Hospital Director"))).andExpect(status().isOk()).andExpect(jsonPath("$.departments").exists());
        mvc.perform(post("/api/records/departments").session(session("Hospital Director")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/dashboard").session(session("Imaging Staff"))).andExpect(status().isOk()).andExpect(jsonPath("$.lists.worklist").isArray());
        mvc.perform(post("/api/records/laboratory/LAB-0001/actions").session(session("Imaging Staff")).with(csrf()).contentType("application/json").content("{\"action\":\"Collect sample\"}")).andExpect(status().isForbidden());
    }
    @Test void departmentsCannotCallOtherStationsAndLinkedRoleCannotEscalate()throws Exception{
        for(String role:List.of("Receptionist","Nurse","Imaging Staff","Pharmacist","Cashier"))mvc.perform(get("/mini/doctor").session(session(role))).andExpect(status().isForbidden());
        for(String role:List.of("Pharmacist","Cashier","Laboratory Staff","Imaging Staff"))mvc.perform(post("/api/care/queue/call-next").session(session(role)).with(csrf()).contentType("application/json").content("{\"station\":\"Consultation\"}")).andExpect(status().isForbidden());
        var linked=session("Doctor");linked.setAttribute("authSource","telegram");
        mvc.perform(post("/api/role").session(linked).with(csrf()).contentType("application/json").content("{\"role\":\"Administrator\"}")).andExpect(status().isForbidden());
    }
    @Test void cannotMoveAnExistingOrderToAnotherEncounterOrConsumeLabQueueAtConsultationStart(){
        var service=new InMemoryHospitalService(new ModuleCatalog());
        String p=service.save("patients",null,values("name","Synthetic encounter immutability","gender","Other","dateOfBirth","1980-01-01","phone","000000"),"QA").get("id");
        String apt=service.save("appointments",null,values("name","Synthetic visit","patient",p,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().toString(),"time","16:47","type","Consultation"),"QA").get("id");
        String e=service.careAction(p,"","checkin",values("appointment",apt),"QA").get("id");
        String other=service.save("visits",null,values("name","Synthetic next attendance","patient",p,"doctor","DOC-0001","department","DEP-0001","date",LocalDate.now().plusDays(1).toString(),"type","Outpatient"),"QA").get("id");
        var lab=service.careAction(p,e,"order",values("category","Laboratory","test","TST-0001","indication","Synthetic only"),"QA");
        var edit=new LinkedHashMap<>(lab);edit.put("encounter",other);
        assertThatThrownBy(()->service.save("laboratory",lab.get("id"),edit,"QA")).hasMessageContaining("encounter of a linked record");
        @SuppressWarnings("unchecked") var current=(Map<String,List<Map<String,String>>>)service.careContext(p,e).get("current");
        String q=current.get("queues").getFirst().get("id");
        service.careAction(p,e,"queue",values("queue",q,"status","Transferred","station","Laboratory","doctor","DOC-0001"),"QA");
        service.careAction(p,e,"start",Map.of(),"QA");
        @SuppressWarnings("unchecked") var after=(Map<String,List<Map<String,String>>>)service.careContext(p,e).get("current");
        assertThat(after.get("queues")).anySatisfy(row->{assertThat(row.get("station")).isEqualTo("Laboratory");assertThat(row.get("status")).isEqualTo("Waiting");});
        assertThat(service.get("laboratory",lab.get("id")).get("encounter")).isEqualTo(e);
    }
}
