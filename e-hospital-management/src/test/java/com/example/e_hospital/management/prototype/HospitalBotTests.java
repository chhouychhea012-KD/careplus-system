package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HospitalBotTests {
    Map<String,Object> update(int id,String command){return Map.of("update_id",id,"message",Map.of("chat",Map.of("id",100,"type","private"),"from",Map.of("id",100),"text",command));}
    @Test void commandsPersistLanguageAndRateLimitReplaysAndGroupMessages(){
        var access=new TelegramAccessService("SYNTHETIC","demo",false,Duration.ofMinutes(15),Clock.systemUTC());
        access.updateSettings(Map.of("publicUrl","https://hospital.example.test","contactInfo","Synthetic reception · 09:00–17:00"));
        var bot=new HospitalBotService(access,"SYNTHETIC-SECRET",true);
        assertThat(bot.webhook("SYNTHETIC-SECRET",update(1,"/language km")).get("text").toString()).contains("ភាសា");
        assertThat(bot.webhook("SYNTHETIC-SECRET",update(2,"/start")).get("text").toString()).contains("សូមស្វាគមន៍");
        assertThat(bot.webhook("SYNTHETIC-SECRET",update(3,"/contact")).get("text").toString()).contains("Synthetic reception");
        assertThat(bot.webhook("SYNTHETIC-SECRET",update(4,"/help")).get("reply_markup").toString()).contains("/tg?startapp=doctor");
        assertThat(bot.webhook("SYNTHETIC-SECRET",update(4,"/help"))).containsEntry("ok",true).doesNotContainKey("method");
        for(int i=5;i<=20;i++)bot.webhook("SYNTHETIC-SECRET",update(i,"/help"));
        assertThatThrownBy(()->bot.webhook("SYNTHETIC-SECRET",update(21,"/help"))).hasMessageContaining("429");
        assertThat(bot.webhook("SYNTHETIC-SECRET",Map.of("update_id",22,"message",Map.of("chat",Map.of("id",-1,"type","group"),"text","/contact")))).containsEntry("ok",true).doesNotContainKey("method");
        assertThat(bot.status().toString()).doesNotContain("SYNTHETIC-SECRET");
    }
    @Test void disabledWebhookFailsClosedAndPreviewHasNoLaunchUntilConfigured(){
        var access=new TelegramAccessService("","demo",false,Duration.ofMinutes(15),Clock.systemUTC());
        var bot=new HospitalBotService(access,"",false);
        assertThatThrownBy(()->bot.webhook(null,update(1,"/start"))).hasMessageContaining("503");
        assertThat(bot.preview("/start","en")).doesNotContainKey("reply_markup");
    }
}
