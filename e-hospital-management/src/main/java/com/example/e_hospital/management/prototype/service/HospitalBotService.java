package com.example.e_hospital.management.prototype.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Private-chat commands only. No clinical data is read or sent. Webhook responses use Telegram's sendMessage method. */
@Service
public class HospitalBotService {
    private final TelegramAccessService access;
    private final String secret;
    private final boolean enabled;
    private final Clock clock;
    private final Map<Long,Instant> seen=new LinkedHashMap<>();
    private final Map<String,String> languages=new LinkedHashMap<>();
    private final Map<String,Deque<Instant>> calls=new LinkedHashMap<>();
    private final List<Map<String,String>> history=new ArrayList<>();
    private Instant lastUpdate;
    @org.springframework.beans.factory.annotation.Autowired
    public HospitalBotService(TelegramAccessService access,@Value("${careplus.telegram.webhook-secret:}") String secret,
            @Value("${careplus.telegram.webhook-enabled:false}") boolean enabled){this(access,secret,enabled,Clock.systemUTC());}
    HospitalBotService(TelegramAccessService access,String secret,boolean enabled,Clock clock){this.access=access;this.secret=secret;this.enabled=enabled;this.clock=clock;}
    public synchronized Map<String,Object> status(){
        return Map.of("enabled",enabled,"secretConfigured",!secret.isBlank(),"ready",enabled&&!secret.isBlank()&&access.tokenConfigured(),
            "path","/api/telegram/webhook","lastUpdate",lastUpdate==null?"":lastUpdate.toString(),"history",List.copyOf(history),
            "commands",List.of("/start","/help","/language en","/language km","/contact"),"note","Webhook receipt is recorded; Telegram delivery still requires live testing.");
    }
    public synchronized Map<String,Object> webhook(String supplied,Map<String,Object> update){
        if(!enabled||secret.isBlank()||!access.tokenConfigured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Telegram webhook is disabled.");
        if(supplied==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook secret.");
        Object updateId=update.get("update_id");if(!(updateId instanceof Number))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid update identifier.");
        long id=((Number)updateId).longValue();Instant now=clock.instant();seen.values().removeIf(t->t.isBefore(now.minus(Duration.ofHours(24))));
        if(seen.containsKey(id))return Map.of("ok",true);
        Map<String,Object> message=object(update.get("message")),chat=object(message.get("chat")),from=object(message.get("from"));
        if(!"private".equals(chat.get("type"))||!(chat.get("id") instanceof Number)||!Objects.equals(chat.get("id"),from.get("id")))return Map.of("ok",true);
        String chatId=String.valueOf(chat.get("id"));var recent=calls.computeIfAbsent(chatId,k->new ArrayDeque<>());
        recent.removeIf(t->t.isBefore(now.minusSeconds(60)));
        if(recent.size()>=20)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please retry later.");
        recent.add(now);seen.put(id,now);if(seen.size()>10000)seen.remove(seen.keySet().iterator().next());
        if(calls.size()>5000)calls.keySet().removeIf(k->!k.equals(chatId)&&calls.get(k).stream().allMatch(t->t.isBefore(now.minusSeconds(60))));
        String text=Objects.toString(message.get("text"),"");if(!text.startsWith("/"))return Map.of("ok",true);
        Map<String,Object> reply=command(chatId,text);lastUpdate=now;
        history.addFirst(Map.of("time",now.toString(),"command",text.split("\\s+",2)[0].split("@")[0].substring(0,Math.min(32,text.split("\\s+",2)[0].split("@")[0].length())),"status","Webhook response prepared"));
        if(history.size()>50)history.removeLast();return reply;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){return value instanceof Map<?,?>?(Map<String,Object>)value:Map.of();}
    /** Admin preview uses a synthetic chat and the same command handler; never performs network delivery. */
    public synchronized Map<String,Object> preview(String command,String language){return response("0",command,"km".equals(language)?"km":"en");}
    private Map<String,Object> command(String chatId,String text){
        String[] parts=text.strip().split("\\s+",2);String command=parts[0].split("@")[0];
        if(command.equals("/language")&&parts.length==2&&List.of("en","km").contains(parts[1])){languages.put(chatId,parts[1]);if(languages.size()>5000)languages.remove(languages.keySet().iterator().next());}
        return response(chatId,command,languages.getOrDefault(chatId,"en"));
    }
    private Map<String,Object> response(String chatId,String text,String lang){
        String command=text.strip().split("\\s+",2)[0].split("@")[0];boolean km=lang.equals("km");var settings=access.settings();
        String body=switch(command){
            case "/start" -> km?"សូមស្វាគមន៍មកកាន់ CarePlus។ បើកកម្មវិធី ហើយភ្ជាប់គណនីមន្ទីរពេទ្យរបស់អ្នក។":"Welcome to CarePlus. Open the Mini App and link your verified hospital account.";
            case "/language" -> km?"ភាសា៖ ខ្មែរ។ ប្រើ /language en ឬ /language km។":"Language: English. Use /language en or /language km.";
            case "/contact" -> km?"ព័ត៌មានទំនាក់ទំនងមន្ទីរពេទ្យ៖\n"+settings.get("contactInfo"):"Hospital contact and opening hours:\n"+settings.get("contactInfo");
            default -> km?"ប្រើ /start, /help, /language en, /language km, /contact។ បើកកម្មវិធីសម្រាប់ការណាត់ជួប និងជួររង់ចាំ។ កុំផ្ញើព័ត៌មានវេជ្ជសាស្ត្រតាមការជជែកនេះ។":"Use /start, /help, /language en, /language km, /contact. Open the Mini App for bookings, queue and tasks. Please do not send clinical information in this chat.";
        };
        var out=new LinkedHashMap<String,Object>();out.put("method","sendMessage");out.put("chat_id",chatId);out.put("text",body);
        String url=Objects.toString(settings.get("publicUrl"),"");
        if(url.startsWith("https://")){
            // Both public launch buttons require verified initData and account linking on the server.
            out.put("reply_markup",Map.of("inline_keyboard",List.of(
                List.of(Map.of("text",km?"កម្មវិធីអ្នកជំងឺ":"Open Patient Mini App","web_app",Map.of("url",url+"/tg?startapp=patient"))),
                List.of(Map.of("text",km?"កម្មវិធីវេជ្ជបណ្ឌិត":"Open Doctor Mini App","web_app",Map.of("url",url+"/tg?startapp=doctor"))))));
        }
        return out;
    }
}
