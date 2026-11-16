package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Role-based navigation, role dashboards, certificate history access and server-side sorting. */
@SpringBootTest @AutoConfigureMockMvc
class RoleNavigationAndDashboardTests {
    @Autowired MockMvc mvc;@Autowired HospitalService service;

    MockHttpSession as(String role){var s=new MockHttpSession();s.setAttribute("demoUser",role.toLowerCase().replace(' ','.')+"@example.test");s.setAttribute("demoRole",role);if(role.equals("Doctor"))s.setAttribute("demoDoctor","DOC-0001");s.setAttribute("demoPatient","PAT-0001");return s;}

    @Test void doctorNavigationShowsClinicalWorkOnlyAndMarksTheCurrentPage() throws Exception {
        mvc.perform(get("/care/queue").session(as("Doctor"))).andExpect(status().isOk())
            .andExpect(content().string(containsString(">My Queue<"))).andExpect(content().string(containsString(">Medical Certificates<")))
            .andExpect(content().string(containsString(">Tasks<"))).andExpect(content().string(not(containsString(">Roles and Permissions<"))))
            .andExpect(content().string(containsString("href=\"/care/queue\" class=\"nav-link active\"")));
    }
    @Test void eachRoleGetsItsOwnPrimaryNavigation() throws Exception {
        mvc.perform(get("/").session(as("Receptionist"))).andExpect(status().isOk()).andExpect(content().string(containsString(">Patient Registration<"))).andExpect(content().string(containsString(">Check-In<")));
        mvc.perform(get("/").session(as("Nurse"))).andExpect(content().string(containsString(">Triage<"))).andExpect(content().string(containsString(">Vital Signs<")));
        mvc.perform(get("/").session(as("Laboratory Staff"))).andExpect(content().string(containsString(">Sample Collection<")));
        mvc.perform(get("/").session(as("Pharmacist"))).andExpect(content().string(containsString(">Dispensing<"))).andExpect(content().string(containsString(">Medicine Stock<")));
        mvc.perform(get("/").session(as("Administrator"))).andExpect(content().string(containsString(">Roles and Permissions<"))).andExpect(content().string(containsString(">Hospital Configuration<")))
            .andExpect(content().string(containsString(">Hospital Analytics<")));
    }
    @Test void moreModulesNeverRepeatsPrimaryLinksOrShowsForbiddenModules() throws Exception {
        String page=mvc.perform(get("/").session(as("Pharmacist"))).andReturn().getResponse().getContentAsString();
        String html=page.substring(page.indexOf("<aside"),page.indexOf("</aside>")); // the sidebar only
        assertThat(html).doesNotContain("href=\"/billing\"").doesNotContain("href=\"/staff\"");
        assertThat(html.split("href=\"/stock\"",-1).length-1).isEqualTo(1);
    }
    @Test void dashboardIsRoleSpecificAndBuiltFromServiceData() throws Exception {
        mvc.perform(get("/api/care/dashboard").session(as("Doctor"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.tiles[*].label",hasItems("Today's appointments","Patients waiting","Pending clinical notes","Results to review")))
            .andExpect(jsonPath("$.lists.schedule").isArray()).andExpect(jsonPath("$.lists.tasks").isArray());
        mvc.perform(get("/api/care/dashboard").session(as("Receptionist"))).andExpect(jsonPath("$.tiles[*].label",hasItems("Registrations today","Awaiting check-in"))).andExpect(jsonPath("$.queueByStation.Triage").exists());
        mvc.perform(get("/api/care/dashboard").session(as("Administrator"))).andExpect(jsonPath("$.departments").exists()).andExpect(jsonPath("$.billing.invoiced").exists()).andExpect(jsonPath("$.staff.doctors.Available").exists());
        mvc.perform(get("/api/care/dashboard").session(as("Patient"))).andExpect(status().isForbidden());
    }
    @SuppressWarnings("unchecked") @Test void doctorTilesMatchTheQueue(){
        var d=service.roleDashboard("Doctor","DOC-0001");
        long waiting=service.list("queues","","Waiting","","","",1,100).items().stream().filter(q->"DOC-0001".equals(q.get("doctor"))&&List.of("Registration","Triage","Consultation").contains(q.getOrDefault("station","Consultation"))).count();
        var tile=((List<Map<String,Object>>)d.get("tiles")).stream().filter(t->t.get("label").equals("Patients waiting")).findFirst().orElseThrow();
        assertThat(((Number)tile.get("value")).longValue()).isEqualTo(waiting);
    }
    @Test void certificateHistoryIsForCliniciansOnly() throws Exception {
        mvc.perform(get("/api/care/documents?kind=certificate").session(as("Doctor"))).andExpect(status().isOk());
        mvc.perform(get("/api/care/documents?kind=certificate").session(as("Receptionist"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/documents?kind=triage").session(as("Doctor"))).andExpect(status().isBadRequest());
        mvc.perform(get("/care/certificates").session(as("Pharmacist"))).andExpect(status().isForbidden());
    }
    @Test void analyticsOverviewMovedButStillAvailable() throws Exception {
        mvc.perform(get("/overview").session(as("Administrator"))).andExpect(status().isOk()).andExpect(content().string(containsString("nav2")));
    }
    @Test void tablesSortOnTheServerBeforePaging(){
        var asc=service.list("patients","","","","","",1,100,"name","asc").items().stream().map(r->r.get("name")).toList();
        var sorted=new ArrayList<>(asc);sorted.sort(String.CASE_INSENSITIVE_ORDER);assertThat(asc).isEqualTo(sorted);
        var desc=service.list("patients","","","","","",1,1,"name","desc").items();assertThat(desc.getFirst().get("name")).isEqualTo(sorted.getLast());
        var dates=service.list("appointments","","","","","",1,100,"date","asc").items().stream().map(r->r.get("date")+" "+r.get("time")).toList();
        assertThat(dates).isSorted();
        assertThat(LocalDate.now()).isNotNull();
    }
}
