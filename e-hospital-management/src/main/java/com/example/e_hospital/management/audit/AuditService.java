package com.example.e_hospital.management.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository events;
    public AuditService(AuditEventRepository events) { this.events = events; }

    // Callers pass only fixed event names and record identifiers, never request bodies or credentials.
    @Transactional
    public void record(String actor, String action, String target) {
        events.save(new AuditEvent(actor, action, target));
    }
}
