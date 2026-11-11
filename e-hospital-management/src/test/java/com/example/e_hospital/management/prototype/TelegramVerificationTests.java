package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.service.TelegramInitDataVerifier;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc
class TelegramVerificationTests {
    @Autowired MockMvc mvc;
    static final String TOKEN="123456:SYNTHETIC-TEST-TOKEN";
    static final Instant NOW=Instant.parse("2026-11-11T04:00:00Z");
    final TelegramInitDataVerifier verifier=new TelegramInitDataVerifier(TOKEN,Duration.ofHours(1),Clock.fixed(NOW,ZoneOffset.UTC));
    String encode(Map<String,String> fields){return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));}
    Map<String,String> signed(long authDate){var f=new LinkedHashMap<String,String>();f.put("query_id","SYNTHETIC");f.put("user","{\"id\":1001,\"first_name\":\"Demo\"}");f.put("auth_date",String.valueOf(authDate));f.put("hash",verifier.sign(f));return f;}

    @Test void acceptsCorrectlySignedFreshData(){
        var fields=verifier.verify(encode(signed(NOW.getEpochSecond()-120)));
        assertThat(fields).containsEntry("user","{\"id\":1001,\"first_name\":\"Demo\"}").doesNotContainKey("hash");
    }
    @Test void rejectsTamperedForeignOrExpiredData(){
        var tampered=signed(NOW.getEpochSecond());tampered.put("user","{\"id\":9999}");
        assertThatThrownBy(()->verifier.verify(encode(tampered))).hasMessageContaining("invalid");
        var otherBot=new TelegramInitDataVerifier("999:OTHER",Duration.ofHours(1),Clock.fixed(NOW,ZoneOffset.UTC));
        assertThatThrownBy(()->otherBot.verify(encode(signed(NOW.getEpochSecond())))).hasMessageContaining("invalid");
        assertThatThrownBy(()->verifier.verify(encode(signed(NOW.getEpochSecond()-7200)))).hasMessageContaining("expired");
        assertThatThrownBy(()->verifier.verify("user=%7B%7D&auth_date=1")).hasMessageContaining("missing");
        assertThatThrownBy(()->verifier.verify(null)).hasMessageContaining("missing");
    }
    @Test void liveLinkingStaysDisabledWithoutConfiguredToken()throws Exception{
        var s=new MockHttpSession();s.setAttribute("demoUser","patient@example.test");s.setAttribute("demoRole","Patient");s.setAttribute("demoPatient","PAT-0001");
        mvc.perform(post("/api/care/telegram/link").session(s).with(csrf()).contentType("application/json").content("{\"initData\":\"user=%7B%7D\"}")).andExpect(status().isNotImplemented());
    }
}
