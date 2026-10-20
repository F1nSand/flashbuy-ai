-- Per-account login throttling shared by all auth service replicas.
CREATE TABLE auth_login_attempt (
    account_key CHAR(64) NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_key),
    KEY ix_auth_login_locked_until (locked_until),
    CONSTRAINT ck_auth_login_attempts_nonnegative CHECK (failed_attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
