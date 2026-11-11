package com.example.e_hospital.management.security;

import com.example.e_hospital.management.audit.AuditService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Uniform asynchronous responses prevent mail delivery timing from revealing account existence. */
@Component
public class RecoveryDispatcher implements DisposableBean {
    private final PasswordRecoveryService recovery;
    private final AuditService audit;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    public RecoveryDispatcher(PasswordRecoveryService recovery, AuditService audit) {
        this.recovery = recovery; this.audit = audit;
        executor.setCorePoolSize(2); executor.setMaxPoolSize(2); executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("recovery-"); executor.initialize();
    }
    public void submit(String username) {
        try {
            executor.execute(() -> {
                try { recovery.request(username); }
                catch (Exception exception) {
                    // Do not log mail exceptions, recipient details, URLs or token values.
                    audit.record("SYSTEM", "PASSWORD_RESET_DELIVERY_FAILED", "authentication");
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException exception) {
            audit.record("SYSTEM", "PASSWORD_RESET_CAPACITY_REACHED", "authentication");
        }
    }
    @Override public void destroy() { executor.shutdown(); }
}
