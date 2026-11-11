package com.example.e_hospital.management.audit;

import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
@Transactional(readOnly = true)
public class AuditReadService {
    private final AuditEventRepository events;
    public AuditReadService(AuditEventRepository events) { this.events = events; }
    public Page<AuditEvent> list(String actor, int page) {
        return events.findByActorContainingIgnoreCase(actor, PageRequest.of(Math.max(0, Math.min(page, 100000)), 20,
            Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
    }
    public long count() { return events.count(); }
}
