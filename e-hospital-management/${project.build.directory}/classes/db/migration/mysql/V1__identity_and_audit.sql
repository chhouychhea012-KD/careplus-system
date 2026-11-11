-- Additive first migration. Existing tables cause failure instead of being overwritten.
CREATE TABLE hospital_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);
CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target VARCHAR(120) NOT NULL,
    occurred_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_audit_occurred ON audit_events (occurred_at);
