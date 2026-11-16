package com.example.e_hospital.management.prototype.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Telegram Mini App access: configuration, deep links, one-time account-link codes, linked accounts and a
 * notification outbox. Opening a deep link or scanning a QR code never grants access by itself: a session is created
 * only after server-side verification of initData AND an existing link to a hospital account.
 * State is in memory (prototype); see docs/telegram-mini-app-setup.md for production requirements.
 */
@Service
public class TelegramAccessService {
    private static final Pattern BOT=Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,30}[Bb][Oo][Tt]$"),SHORT=Pattern.compile("^[A-Za-z0-9_]{3,30}$"),USER_ID=Pattern.compile("\"id\"\\s*:\\s*(\\d{1,20})"),FIRST=Pattern.compile("\"first_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){0,64})\"");
    private final String botToken,mode;private final boolean sendEnabled;private final Duration maxAge;private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    private String botUsername="",shortName="",publicUrl="";
    private String contactInfo="Demo hospital — contact details and opening hours are not configured.";
    private final Map<String,Map<String,String>> linkCodes=new HashMap<>(),links=new LinkedHashMap<>();
    private final Map<String,String> usedInitData=new HashMap<>();
    private final List<Map<String,String>> outbox=new ArrayList<>();

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramAccessService(@Value("${careplus.telegram.bot-token:}") String botToken,@Value("${careplus.mode:demo}") String mode,
                                 @Value("${careplus.telegram.send-enabled:false}") boolean sendEnabled,@Value("${careplus.telegram.init-data-max-age-minutes:15}") long maxAgeMinutes){
        this(botToken,mode,sendEnabled,Duration.ofMinutes(maxAgeMinutes),Clock.systemUTC());
    }
    public TelegramAccessService(String botToken,String mode,boolean sendEnabled,Duration maxAge,Clock clock){
        this.botToken=Objects.toString(botToken,"").strip();this.mode="integration".equalsIgnoreCase(mode)?"integration":"demo";this.sendEnabled=sendEnabled;this.maxAge=maxAge;this.clock=clock;
    }
    public boolean integration(){return mode.equals("integration");}
    public boolean tokenConfigured(){return !botToken.isBlank();}
    private static void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}

    public synchronized Map<String,Object> settings(){
        var s=new LinkedHashMap<String,Object>();s.put("mode",mode);s.put("botUsername",botUsername);s.put("shortName",shortName);s.put("publicUrl",publicUrl);
        s.put("tokenConfigured",tokenConfigured());s.put("sendEnabled",sendEnabled&&tokenConfigured());s.put("contactInfo",contactInfo);
        boolean live=tokenConfigured()&&!botUsername.isBlank()&&publicUrl.startsWith("https://");
        s.put("status",live?(integration()?"Ready for live Telegram testing":"Configured — switch careplus.mode to integration to require Telegram sign-in"):"Demo — live Telegram opening unavailable");
        var missing=new ArrayList<String>();if(!tokenConfigured())missing.add("Bot token (environment variable CAREPLUS_TELEGRAM_BOT_TOKEN)");if(botUsername.isBlank())missing.add("Bot username");if(!publicUrl.startsWith("https://"))missing.add("Public HTTPS application URL");
        s.put("missing",missing);s.put("live",live);s.put("links",links.size());return s;
    }
    public synchronized Map<String,Object> updateSettings(Map<String,String> v){
        String bot=Objects.toString(v.get("botUsername"),"").strip().replaceFirst("^@","").replaceFirst("^https://t\\.me/","");
        String sn=Objects.toString(v.get("shortName"),"").strip();String url=Objects.toString(v.get("publicUrl"),"").strip().replaceAll("/+$","");
        if(!bot.isBlank()&&!BOT.matcher(bot).matches())bad("Enter the bot username from BotFather, for example CarePlusHospitalBot (it must end in 'bot').");
        if(!sn.isBlank()&&!SHORT.matcher(sn).matches())bad("The Mini App short name uses 3–30 letters, digits or underscores.");
        if(!url.isBlank()){try{var u=URI.create(url);if(!"https".equals(u.getScheme())||u.getHost()==null)bad("The public application URL must start with https://");}catch(IllegalArgumentException ex){bad("Enter a valid https:// URL.");}}
        String contact=v.getOrDefault("contactInfo",contactInfo).strip();if(contact.length()>1000)bad("Contact information is too long.");
        botUsername=bot;shortName=sn;publicUrl=url;contactInfo=contact;return settings();
    }
    /** Deep links for posters. start parameters: patient, doctor, arrive-CODE (Telegram allows A-Z a-z 0-9 _ -, max 64). */
    public synchronized Map<String,String> deepLinks(String arrivalCode,String localBase){
        var m=new LinkedHashMap<String,String>();
        for(var e:Map.of("patient","patient","doctor","doctor","arrival","arrive-"+arrivalCode).entrySet()){
            String key=e.getKey(),param=e.getValue();
            m.put(key,botUsername.isBlank()?localBase+"/tg?startapp="+param:"https://t.me/"+botUsername+(shortName.isBlank()?"":"/"+shortName)+"?startapp="+param);
        }
        m.put("kind",botUsername.isBlank()?"demo":"telegram");return m;
    }

    // ---- Account linking -------------------------------------------------------------------------------------------
    public synchronized Map<String,String> createLinkCode(String role,String identity,String actor){
        if(!List.of("Patient","Doctor").contains(role))bad("Choose Patient or Doctor.");
        linkCodes.values().removeIf(c->Instant.parse(c.get("expires")).isBefore(clock.instant()));
        String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";var b=new StringBuilder();for(int i=0;i<8;i++){if(i==4)b.append('-');b.append(alphabet.charAt(random.nextInt(alphabet.length())));}
        var c=new LinkedHashMap<String,String>();c.put("code",b.toString());c.put("role",role);c.put("identity",identity);c.put("expires",clock.instant().plus(Duration.ofMinutes(10)).toString());c.put("createdBy",actor);
        linkCodes.put(c.get("code"),c);return Map.copyOf(c);
    }
    /** Verifies raw initData (never initDataUnsafe). Rejects stale data and reuse of the same initData by another session. */
    public synchronized Map<String,String> verify(String initData,String sessionId){
        if(!tokenConfigured())throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,"Live Telegram access is not configured on this server. Use the local demo preview.");
        Map<String,String> fields;try{fields=new TelegramInitDataVerifier(botToken,maxAge,clock).verify(initData);}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,ex.getMessage());}
        String digest=sha256(initData);usedInitData.entrySet().removeIf(e->usedInitData.size()>5000);
        String owner=usedInitData.putIfAbsent(digest,sessionId);if(owner!=null&&!owner.equals(sessionId))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"This Telegram launch was already used. Close and reopen the Mini App.");
        var m=USER_ID.matcher(fields.getOrDefault("user",""));if(!m.find())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Telegram user is missing.");
        var out=new LinkedHashMap<String,String>();out.put("telegramUser",m.group(1));var f=FIRST.matcher(fields.getOrDefault("user",""));out.put("firstName",f.find()?f.group(1):"");out.put("startParam",fields.getOrDefault("start_param",""));return out;
    }
    public synchronized Map<String,String> linkedAccount(String telegramUser){var l=links.get(telegramUser);return l==null?null:Map.copyOf(l);}
    public synchronized Map<String,String> redeemLinkCode(String code,String telegramUser,String firstName){
        String c=Objects.toString(code,"").strip().toUpperCase(Locale.ROOT);var entry=linkCodes.remove(c);
        if(entry==null||Instant.parse(entry.get("expires")).isBefore(clock.instant()))bad("This link code is invalid or expired. Ask the hospital desk for a new code.");
        var l=new LinkedHashMap<String,String>();l.put("telegramUser",telegramUser);l.put("role",entry.get("role"));l.put("identity",entry.get("identity"));l.put("firstName",firstName);l.put("linkedAt",clock.instant().toString());l.put("notifications","off");
        links.put(telegramUser,l);return Map.copyOf(l);
    }
    public synchronized void setNotifications(String telegramUser,boolean on){var l=links.get(telegramUser);if(l==null)bad("Link your Telegram account first.");l.put("notifications",on?"on":"off");}
    public synchronized List<Map<String,String>> linkedAccounts(){return links.values().stream().map(l->{var m=new LinkedHashMap<>(l);m.put("telegramUser","•••"+l.get("telegramUser").substring(Math.max(0,l.get("telegramUser").length()-3)));return (Map<String,String>)m;}).toList();}
    public synchronized void unlink(String identity){links.values().removeIf(l->l.get("identity").equals(identity));}

    // ---- Notifications ---------------------------------------------------------------------------------------------
    /** Generic, opt-in messages only. Never include diagnoses, results, documents or clinical notes. */
    public synchronized void notifyPatient(String patient,String type,Map<String,String> facts){
        String text=switch(type){
            case "appointment" -> "CarePlus: your appointment on "+facts.getOrDefault("date","")+" at "+facts.getOrDefault("time","")+" is booked. Open the CarePlus Mini App for details.";
            case "queue-called" -> "CarePlus: queue number "+facts.getOrDefault("queue","")+" has been called. Open the CarePlus Mini App to see where to go.";
            case "visit-completed" -> "CarePlus: your visit is complete. Any released documents are available in the CarePlus Mini App.";
            case "follow-up" -> "CarePlus: a follow-up appointment was booked for "+facts.getOrDefault("date","")+". Open the CarePlus Mini App for details.";
            default -> "CarePlus: you have an update. Open the CarePlus Mini App.";
        };
        for(var l:links.values()){
            if(!l.get("identity").equals(patient)||!"on".equals(l.get("notifications")))continue;
            var msg=new LinkedHashMap<String,String>();msg.put("time",clock.instant().toString());msg.put("type",type);msg.put("to","•••"+l.get("telegramUser").substring(Math.max(0,l.get("telegramUser").length()-3)));msg.put("text",text);
            msg.put("status",sendEnabled&&tokenConfigured()?send(l.get("telegramUser"),text):"Queued — sending disabled");outbox.addFirst(msg);if(outbox.size()>200)outbox.removeLast();
        }
    }
    public synchronized List<Map<String,String>> outbox(){return outbox.stream().map(Map::copyOf).toList();}
    private String send(String chatId,String text){
        try{var body="chat_id="+URLEncoder.encode(chatId,StandardCharsets.UTF_8)+"&text="+URLEncoder.encode(text,StandardCharsets.UTF_8);
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+botToken+"/sendMessage")).timeout(Duration.ofSeconds(8)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding());
            return response.statusCode()==200?"Sent":"Failed (HTTP "+response.statusCode()+")";
        }catch(Exception ex){return "Failed ("+ex.getClass().getSimpleName()+")";}
    }
    private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
