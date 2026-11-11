ALTER TABLE hospital_users ADD COLUMN email VARCHAR(254) NULL;
ALTER TABLE hospital_users ADD COLUMN phone VARCHAR(30) NULL;
ALTER TABLE hospital_users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_user_email ON hospital_users (email);
CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    CONSTRAINT uk_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES hospital_users(id)
);
CREATE INDEX idx_reset_user ON password_reset_tokens(user_id);
