package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Integration mode end to end: signed initData → link required → one-time code → scoped session. QR/deep link alone grants nothing. */
@SpringBootTest(properties={"careplus.mode=integration","careplus.telegram.bot-token=424242:SYNTHETIC-INTEGRATION-TOKEN"}) @AutoConfigureMockMvc
class TelegramIntegrationModeTests {
    @Autowired MockMvc mvc;@Autowired TelegramAccessService telegram;
    static final String TOKEN="424242:SYNTHETIC-INTEGRATION-TOKEN";
    String initData(String userId,String start){
        var v=new TelegramInitDataVerifier(TOKEN,Duration.ofMinutes(15),Clock.systemUTC());var f=new LinkedHashMap<String,String>();
        f.put("auth_date",String.valueOf(Instant.now().getEpochSecond()));f.put("query_id","Q"+userId);f.put("start_param",start);f.put("user","{\"id\":"+userId+",\"first_name\":\"Demo\"}");f.put("hash",v.sign(f));
        return f.entrySet().stream().map(e->e.getKey()+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }
    String json(String k,String v){return "{\""+k+"\":\""+v.replace("\"","\\\"")+"\"}";}

    @Test void verifiedButUnlinkedUserMustRedeemCodeThenGetsOnlyLinkedRole()throws Exception{
        var session=new MockHttpSession();String data=initData("700001","doctor");
        mvc.perform(post("/api/telegram/session").session(session).with(csrf()).contentType("application/json").content(json("initData",data)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("link-required"));
        mvc.perform(get("/mini/patient").session(session)).andExpect(status().is3xxRedirection());
        var code=telegram.createLinkCode("Patient","PAT-0002","Reception QA").get("code");
        var result=mvc.perform(post("/api/telegram/link").session(session).with(csrf()).contentType("application/json").content(json("code",code)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Patient")).andExpect(jsonPath("$.redirect").value("/mini/patient")).andReturn();
        var signed=(MockHttpSession)result.getRequest().getSession();
        mvc.perform(get("/mini/patient").session(signed)).andExpect(status().isOk());
        mvc.perform(get("/mini/doctor").session(signed)).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/mobile").session(signed)).andExpect(status().isOk()).andExpect(jsonPath("$.patient.id").value("PAT-0002"));
        mvc.perform(post("/api/telegram/link").session(signed).with(csrf()).contentType("application/json").content(json("code",code))).andExpect(status().isUnauthorized());
    }
    @Test void replayedInitDataFromAnotherSessionAndForgedDataAreRejected()throws Exception{
        String data=initData("700002","patient");
        mvc.perform(post("/api/telegram/session").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json("initData",data))).andExpect(status().isOk());
        mvc.perform(post("/api/telegram/session").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json("initData",data))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/telegram/session").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json("initData",data.replace("700002","700009")))).andExpect(status().isUnauthorized());
    }
    @Test void doctorLinkGivesDoctorScopeAndArrivalStartParamIsForwarded()throws Exception{
        var s=new MockHttpSession();mvc.perform(post("/api/telegram/session").session(s).with(csrf()).contentType("application/json").content(json("initData",initData("700003","doctor")))).andExpect(status().isOk());
        var code=telegram.createLinkCode("Doctor","DOC-0002","Admin QA").get("code");
        var r=mvc.perform(post("/api/telegram/link").session(s).with(csrf()).contentType("application/json").content(json("code",code))).andExpect(jsonPath("$.redirect").value("/mini/doctor")).andReturn();
        var doctor=(MockHttpSession)r.getRequest().getSession();mvc.perform(get("/mini/doctor").session(doctor)).andExpect(status().isOk());
        assertThat(doctor.getAttribute("demoDoctor")).isEqualTo("DOC-0002");
        var p=new MockHttpSession();mvc.perform(post("/api/telegram/session").session(p).with(csrf()).contentType("application/json").content(json("initData",initData("700004","arrive-ABCD2345"))));
        var pc=telegram.createLinkCode("Patient","PAT-0003","QA").get("code");
        mvc.perform(post("/api/telegram/link").session(p).with(csrf()).contentType("application/json").content(json("code",pc))).andExpect(jsonPath("$.redirect").value("/mini/patient?arrival=ABCD2345"));
    }
    @Test void patientsCannotRedeemTheirOwnCheckInTicket()throws Exception{
        var s=new MockHttpSession();s.setAttribute("demoUser","telegram:1");s.setAttribute("demoRole","Patient");s.setAttribute("demoPatient","PAT-0001");s.setAttribute("authSource","telegram");
        mvc.perform(post("/api/care/checkin").session(s).with(csrf()).contentType("application/json").content("{\"appointment\":\"APT-0001\",\"verified\":\"true\",\"token\":\"00000000-0000-0000-0000-000000000000\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Show this code to reception")));
    }
    @Test void syntheticPatientLoginAndDemoMiniAccessAreRefusedInIntegrationMode()throws Exception{
        mvc.perform(post("/login").with(csrf()).param("email","p@example.test").param("role","Patient").param("patientIdentity","PAT-0001")).andExpect(redirectedUrl("/login?error"));
        var staff=new MockHttpSession();staff.setAttribute("demoUser","doc@example.test");staff.setAttribute("demoRole","Doctor");
        mvc.perform(get("/mini/doctor").session(staff)).andExpect(status().isForbidden());
        mvc.perform(post("/api/telegram/link").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json("code","AAAA-BBBB"))).andExpect(status().isUnauthorized());
    }
    @Test void settingsAreAdminOnlyValidatedAndProduceTelegramDeepLinks()throws Exception{
        var admin=new MockHttpSession();admin.setAttribute("demoUser","admin@example.test");admin.setAttribute("demoRole","Administrator");
        mvc.perform(post("/api/telegram/settings").session(admin).with(csrf()).contentType("application/json").content("{\"botUsername\":\"not valid\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/telegram/settings").session(admin).with(csrf()).contentType("application/json").content("{\"botUsername\":\"@CarePlusDemoBot\",\"shortName\":\"care\",\"publicUrl\":\"https://care.example.test/\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/telegram/settings").session(admin)).andExpect(jsonPath("$.deepLinks.patient").value("https://t.me/CarePlusDemoBot/care?startapp=patient"))
            .andExpect(jsonPath("$.deepLinks.doctor").value("https://t.me/CarePlusDemoBot/care?startapp=doctor")).andExpect(jsonPath("$.live").value(true)).andExpect(jsonPath("$.tokenConfigured").value(true));
        mvc.perform(get("/api/telegram/settings").session(admin)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SYNTHETIC-INTEGRATION-TOKEN"))));
        var reception=new MockHttpSession();reception.setAttribute("demoUser","r@example.test");reception.setAttribute("demoRole","Receptionist");
        mvc.perform(post("/api/telegram/settings").session(reception).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/telegram/link-code").session(reception).with(csrf()).contentType("application/json").content("{\"role\":\"Doctor\",\"identity\":\"DOC-0001\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/telegram/link-code").session(reception).with(csrf()).contentType("application/json").content("{\"role\":\"Patient\",\"identity\":\"PAT-0001\"}")).andExpect(status().isOk());
    }
}
