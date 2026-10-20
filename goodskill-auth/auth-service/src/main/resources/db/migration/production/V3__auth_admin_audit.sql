-- Minimal audit trail for security-critical identity administration.
CREATE TABLE auth_admin_audit_event (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id VARCHAR(64) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_user_id INT NOT NULL,
    request_id CHAR(36) NOT NULL,
    result VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_auth_admin_audit_request (request_id),
    KEY ix_auth_admin_audit_actor (actor_id, created_at),
    KEY ix_auth_admin_audit_target (target_user_id, created_at),
    CONSTRAINT ck_auth_admin_audit_result CHECK (result IN ('SUCCESS', 'FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
