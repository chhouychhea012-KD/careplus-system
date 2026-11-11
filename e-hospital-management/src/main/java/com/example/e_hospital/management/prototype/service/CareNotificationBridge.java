package com.example.e_hospital.management.prototype.service;

import org.springframework.stereotype.Component;

/** Forwards generic care events from the shared hospital service to opt-in Telegram notifications. */
@Component
public class CareNotificationBridge {
    public CareNotificationBridge(HospitalService hospital,TelegramAccessService telegram){
        if(hospital instanceof InMemoryHospitalService memory)memory.onEvent((type,facts)->telegram.notifyPatient(facts.get("patient"),type,facts));
    }
}
