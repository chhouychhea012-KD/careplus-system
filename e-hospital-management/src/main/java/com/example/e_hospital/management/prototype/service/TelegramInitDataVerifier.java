package com.example.e_hospital.management.prototype.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/**
 * Server-side check of Telegram Mini App {@code initData} (first-party HMAC scheme).
 * data_check_string = every received field except {@code hash}, sorted by key, "key=value" joined by '\n';
 * secret_key = HMAC_SHA256(key="WebAppData", data=bot_token); expected hash = hex(HMAC_SHA256(secret_key, data_check_string)).
 * Never trust {@code initDataUnsafe} from the browser: only fields returned by this verifier may identify a Telegram user,
 * and even then a verified Telegram user must still be linked to an authenticated hospital account before records are shown.
 */
public final class TelegramInitDataVerifier {
    private final String botToken;private final Duration maxAge;private final Clock clock;
    public TelegramInitDataVerifier(String botToken,Duration maxAge,Clock clock){
        if(botToken==null||botToken.isBlank())throw new IllegalArgumentException("A bot token is required.");
        this.botToken=botToken;this.maxAge=maxAge;this.clock=clock;
    }
    /** Returns the decoded fields when the signature and auth_date are valid; otherwise throws IllegalArgumentException. */
    public Map<String,String> verify(String initData){
        if(initData==null||initData.isBlank()||initData.length()>8192)throw new IllegalArgumentException("Telegram data is missing.");
        var fields=new TreeMap<String,String>();
        for(String pair:initData.split("&")){
            int eq=pair.indexOf('=');if(eq<=0)throw new IllegalArgumentException("Telegram data is malformed.");
            String key=URLDecoder.decode(pair.substring(0,eq),StandardCharsets.UTF_8),value=URLDecoder.decode(pair.substring(eq+1),StandardCharsets.UTF_8);
            if(fields.put(key,value)!=null)throw new IllegalArgumentException("Telegram data is malformed.");
        }
        String hash=fields.remove("hash");if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Telegram signature is missing.");
        var check=new StringJoiner("\n");fields.forEach((k,v)->check.add(k+"="+v));
        byte[] expected=hmac(hmac("WebAppData".getBytes(StandardCharsets.UTF_8),botToken),check.toString());
        if(!MessageDigest.isEqual(expected,HexFormat.of().parseHex(hash)))throw new IllegalArgumentException("Telegram signature is invalid.");
        long authDate;try{authDate=Long.parseLong(fields.getOrDefault("auth_date",""));}catch(NumberFormatException ex){throw new IllegalArgumentException("Telegram data has no valid auth_date.");}
        var issued=Instant.ofEpochSecond(authDate);var now=clock.instant();
        if(issued.isAfter(now.plusSeconds(60))||issued.plus(maxAge).isBefore(now))throw new IllegalArgumentException("Telegram data has expired. Reopen the Mini App.");
        return Collections.unmodifiableMap(fields);
    }
    /** Test/support helper: signs a field set exactly as Telegram does. */
    public String sign(Map<String,String> fields){
        var sorted=new TreeMap<>(fields);sorted.remove("hash");var check=new StringJoiner("\n");sorted.forEach((k,v)->check.add(k+"="+v));
        return HexFormat.of().formatHex(hmac(hmac("WebAppData".getBytes(StandardCharsets.UTF_8),botToken),check.toString()));
    }
    private static byte[] hmac(byte[] key,String data){
        try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));}
        catch(java.security.GeneralSecurityException ex){throw new IllegalStateException(ex);}
    }
}
