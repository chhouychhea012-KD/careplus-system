package com.example.e_hospital.management.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_events", indexes = @Index(name = "idx_audit_occurred", columnList = "occurredAt"))
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String actor;
    @Column(nullable = false, length = 50) private String action;
    @Column(nullable = false, length = 120) private String target;
    @Column(nullable = false, updatable = false) private Instant occurredAt = Instant.now();

    protected AuditEvent() {}
    public AuditEvent(String actor, String action, String target) {
        this.actor = actor; this.action = action; this.target = target;
    }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public Instant getOccurredAt() { return occurredAt; }
}
