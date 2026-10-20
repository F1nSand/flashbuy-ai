-- Production-only commerce schema. This is intentionally isolated from historical demo migrations.
-- MySQL 8.0.16+ is required so CHECK constraints are enforced.

CREATE TABLE commerce_activity (
    activity_id BIGINT NOT NULL AUTO_INCREMENT,
    activity_code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    state VARCHAR(24) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    published_version BIGINT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (activity_id),
    UNIQUE KEY uk_commerce_activity_code (activity_code),
    KEY ix_commerce_activity_state_window (state, starts_at, ends_at),
    CONSTRAINT ck_commerce_activity_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_commerce_activity_state CHECK (state IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'ENDED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_sku (
    sku_id BIGINT NOT NULL AUTO_INCREMENT,
    sku_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    sku_name VARCHAR(200) NOT NULL,
    status VARCHAR(24) NOT NULL,
    list_price_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (sku_id),
    UNIQUE KEY uk_commerce_sku_code (sku_code),
    CONSTRAINT ck_commerce_sku_price CHECK (list_price_minor >= 0),
    CONSTRAINT ck_commerce_sku_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_commerce_sku_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_activity_sku (
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    sku_name_snapshot VARCHAR(200) NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    purchase_limit_per_user INT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (activity_id, sku_id),
    CONSTRAINT fk_commerce_activity_sku_activity FOREIGN KEY (activity_id)
        REFERENCES commerce_activity (activity_id),
    CONSTRAINT fk_commerce_activity_sku_sku FOREIGN KEY (sku_id)
        REFERENCES commerce_sku (sku_id),
    CONSTRAINT ck_commerce_activity_sku_price CHECK (unit_price_minor >= 0),
    CONSTRAINT ck_commerce_activity_sku_limit CHECK (purchase_limit_per_user > 0),
    CONSTRAINT ck_commerce_activity_sku_currency CHECK (currency = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_inventory (
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    allocated_quantity BIGINT NOT NULL,
    available_quantity BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    sold_quantity BIGINT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (activity_id, sku_id),
    CONSTRAINT fk_commerce_inventory_activity_sku FOREIGN KEY (activity_id, sku_id)
        REFERENCES commerce_activity_sku (activity_id, sku_id),
    CONSTRAINT ck_commerce_inventory_nonnegative CHECK (
        allocated_quantity >= 0 AND available_quantity >= 0 AND reserved_quantity >= 0 AND sold_quantity >= 0
    ),
    CONSTRAINT ck_commerce_inventory_conservation CHECK (
        allocated_quantity = available_quantity + reserved_quantity + sold_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_purchase_quota (
    buyer_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    purchased_quantity BIGINT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (buyer_id, activity_id, sku_id),
    CONSTRAINT fk_commerce_quota_activity_sku FOREIGN KEY (activity_id, sku_id)
        REFERENCES commerce_activity_sku (activity_id, sku_id),
    CONSTRAINT ck_commerce_quota_nonnegative CHECK (reserved_quantity >= 0 AND purchased_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_order (
    order_id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    activity_version BIGINT NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    sku_name_snapshot VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    shipping_snapshot JSON NOT NULL,
    order_state VARCHAR(32) NOT NULL,
    fulfillment_state VARCHAR(24) NOT NULL DEFAULT 'UNFULFILLED',
    idempotency_scope VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payment_deadline DATETIME(6) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_commerce_order_no (order_no),
    UNIQUE KEY uk_commerce_order_buyer_idempotency (buyer_id, idempotency_scope, idempotency_key),
    KEY ix_commerce_order_buyer_created (buyer_id, created_at, order_id),
    KEY ix_commerce_order_state_deadline (order_state, payment_deadline),
    KEY ix_commerce_order_activity_sku (activity_id, sku_id, order_state),
    CONSTRAINT fk_commerce_order_activity_sku FOREIGN KEY (activity_id, sku_id)
        REFERENCES commerce_activity_sku (activity_id, sku_id),
    CONSTRAINT ck_commerce_order_quantity CHECK (quantity > 0),
    CONSTRAINT ck_commerce_order_amount CHECK (
        unit_price_minor >= 0 AND total_amount_minor >= 0 AND total_amount_minor = unit_price_minor * quantity
    ),
    CONSTRAINT ck_commerce_order_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_commerce_order_state CHECK (order_state IN (
        'PENDING_PAYMENT', 'PAYMENT_CONFIRMING', 'PAID', 'CLOSED', 'REFUNDING', 'REFUNDED'
    )),
    CONSTRAINT ck_commerce_order_fulfillment CHECK (fulfillment_state IN (
        'UNFULFILLED', 'PACKING', 'SHIPPED', 'DELIVERED', 'CANCELLED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_reservation (
    reservation_id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_no VARCHAR(40) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    reservation_state VARCHAR(24) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uk_commerce_reservation_no (reservation_no),
    UNIQUE KEY uk_commerce_reservation_order (order_no),
    KEY ix_commerce_reservation_buyer_quota (buyer_id, activity_id, sku_id, reservation_state),
    KEY ix_commerce_reservation_expiry (reservation_state, expires_at),
    CONSTRAINT fk_commerce_reservation_order FOREIGN KEY (order_no) REFERENCES commerce_order (order_no),
    CONSTRAINT fk_commerce_reservation_inventory FOREIGN KEY (activity_id, sku_id)
        REFERENCES commerce_inventory (activity_id, sku_id),
    CONSTRAINT ck_commerce_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_commerce_reservation_state CHECK (reservation_state IN ('HELD', 'CONSUMED', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_inventory_ledger (
    ledger_id BIGINT NOT NULL AUTO_INCREMENT,
    operation_key VARCHAR(128) NOT NULL,
    activity_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    order_no VARCHAR(40) NULL,
    actor_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    available_delta BIGINT NOT NULL,
    reserved_delta BIGINT NOT NULL,
    sold_delta BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ledger_id),
    UNIQUE KEY uk_commerce_inventory_ledger_operation (operation_key),
    KEY ix_commerce_inventory_ledger_activity (activity_id, sku_id, created_at),
    KEY ix_commerce_inventory_ledger_order (order_no),
    CONSTRAINT fk_commerce_inventory_ledger_inventory FOREIGN KEY (activity_id, sku_id)
        REFERENCES commerce_inventory (activity_id, sku_id),
    CONSTRAINT ck_commerce_inventory_ledger_nonzero CHECK (
        available_delta <> 0 OR reserved_delta <> 0 OR sold_delta <> 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_order_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    transition_key VARCHAR(128) NOT NULL,
    from_state VARCHAR(32) NULL,
    to_state VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    row_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_commerce_order_transition (transition_key),
    UNIQUE KEY uk_commerce_order_version (order_no, row_version),
    CONSTRAINT fk_commerce_order_history_order FOREIGN KEY (order_no) REFERENCES commerce_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_idempotency_request (
    idempotency_id BIGINT NOT NULL AUTO_INCREMENT,
    buyer_id VARCHAR(64) NOT NULL,
    operation_scope VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    request_state VARCHAR(24) NOT NULL,
    result_order_no VARCHAR(40) NULL,
    result_code VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_id),
    UNIQUE KEY uk_commerce_idempotency (buyer_id, operation_scope, idempotency_key),
    KEY ix_commerce_idempotency_created (created_at),
    CONSTRAINT fk_commerce_idempotency_order FOREIGN KEY (result_order_no) REFERENCES commerce_order (order_no),
    CONSTRAINT ck_commerce_idempotency_state CHECK (request_state IN ('IN_PROGRESS', 'COMPLETED', 'REJECTED')),
    CONSTRAINT ck_commerce_idempotency_result CHECK (
        (request_state = 'IN_PROGRESS' AND result_order_no IS NULL AND result_code IS NULL)
        OR (request_state = 'COMPLETED' AND result_order_no IS NOT NULL AND result_code IS NULL)
        OR (request_state = 'REJECTED' AND result_order_no IS NULL AND result_code IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_outbox (
    event_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version INT NOT NULL,
    payload JSON NOT NULL,
    publish_state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_owner VARCHAR(80) NULL,
    lease_until DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    PRIMARY KEY (event_id),
    KEY ix_commerce_outbox_pending (publish_state, available_at, created_at),
    KEY ix_commerce_outbox_aggregate (aggregate_type, aggregate_id, created_at),
    CONSTRAINT ck_commerce_outbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_commerce_outbox_version CHECK (event_version > 0),
    CONSTRAINT ck_commerce_outbox_state CHECK (publish_state IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_compensation_task (
    task_id BIGINT NOT NULL AUTO_INCREMENT,
    task_key VARCHAR(128) NOT NULL,
    task_type VARCHAR(48) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    task_state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_owner VARCHAR(80) NULL,
    lease_until DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_commerce_compensation_task_key (task_key),
    KEY ix_commerce_compensation_task_claim (task_state, available_at, lease_until, task_id),
    KEY ix_commerce_compensation_task_aggregate (task_type, aggregate_id, task_state),
    CONSTRAINT ck_commerce_compensation_task_state CHECK (
        task_state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'DEAD')
    ),
    CONSTRAINT ck_commerce_compensation_task_attempts CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_event_inbox (
    consumer_name VARCHAR(80) NOT NULL,
    event_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version INT NOT NULL,
    payload JSON NOT NULL,
    event_state VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_owner VARCHAR(80) NULL,
    lease_until DATETIME(6) NULL,
    last_error_code VARCHAR(80) NULL,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY ix_commerce_event_inbox_claim (consumer_name, event_state, available_at, lease_until),
    KEY ix_commerce_event_inbox_aggregate (aggregate_type, aggregate_id, event_version),
    CONSTRAINT ck_commerce_event_inbox_version CHECK (event_version > 0),
    CONSTRAINT ck_commerce_event_inbox_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_commerce_event_inbox_state CHECK (
        event_state IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'DEAD')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_consumed_event (
    consumer_name VARCHAR(80) NOT NULL,
    event_id CHAR(36) NOT NULL,
    event_version INT NOT NULL,
    consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (consumer_name, event_id),
    KEY ix_commerce_consumed_event_time (consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_payment_attempt (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(40) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    provider VARCHAR(24) NOT NULL,
    provider_trade_no VARCHAR(128) NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    payment_state VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(128) NULL,
    paid_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_commerce_payment_no (payment_no),
    UNIQUE KEY uk_commerce_payment_provider_trade (provider, provider_trade_no),
    KEY ix_commerce_payment_order (order_no, created_at),
    CONSTRAINT fk_commerce_payment_order FOREIGN KEY (order_no) REFERENCES commerce_order (order_no),
    CONSTRAINT ck_commerce_payment_amount CHECK (amount_minor >= 0),
    CONSTRAINT ck_commerce_payment_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_commerce_payment_state CHECK (payment_state IN (
        'CREATED', 'PENDING', 'CONFIRMING', 'SUCCEEDED', 'FAILED', 'CLOSED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_refund (
    refund_id BIGINT NOT NULL AUTO_INCREMENT,
    refund_no VARCHAR(40) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    provider_refund_no VARCHAR(128) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    refund_state VARCHAR(32) NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    refunded_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (refund_id),
    UNIQUE KEY uk_commerce_refund_no (refund_no),
    UNIQUE KEY uk_commerce_refund_order (order_no),
    UNIQUE KEY uk_commerce_refund_idempotency (idempotency_key),
    UNIQUE KEY uk_commerce_provider_refund (provider_refund_no),
    CONSTRAINT fk_commerce_refund_order FOREIGN KEY (order_no) REFERENCES commerce_order (order_no),
    CONSTRAINT ck_commerce_refund_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_commerce_refund_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_commerce_refund_state CHECK (refund_state IN (
        'PENDING', 'CONFIRMING', 'SUCCEEDED', 'FAILED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_fulfillment (
    fulfillment_id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    state VARCHAR(24) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(40) NOT NULL,
    address_snapshot JSON NOT NULL,
    carrier_code VARCHAR(40) NULL,
    tracking_no VARCHAR(100) NULL,
    shipped_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (fulfillment_id),
    UNIQUE KEY uk_commerce_fulfillment_order (order_no),
    KEY ix_commerce_fulfillment_state (state, updated_at),
    CONSTRAINT fk_commerce_fulfillment_order FOREIGN KEY (order_no) REFERENCES commerce_order (order_no),
    CONSTRAINT ck_commerce_fulfillment_state CHECK (state IN (
        'UNFULFILLED', 'PACKING', 'SHIPPED', 'DELIVERED', 'CANCELLED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_audit_event (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id VARCHAR(64) NOT NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(80) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    result VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NULL,
    changes JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    KEY ix_commerce_audit_target (target_type, target_id, created_at),
    KEY ix_commerce_audit_actor (actor_id, created_at),
    KEY ix_commerce_audit_request (request_id),
    KEY ix_commerce_audit_created (created_at, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_file_asset (
    file_id CHAR(32) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    object_key VARCHAR(160) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    visibility VARCHAR(24) NOT NULL,
    asset_state VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_commerce_file_object (object_key),
    KEY ix_commerce_file_owner (owner_id, asset_state, created_at),
    KEY ix_commerce_file_cleanup (asset_state, updated_at),
    CONSTRAINT ck_commerce_file_size CHECK (size_bytes > 0),
    CONSTRAINT ck_commerce_file_visibility CHECK (visibility IN ('PRODUCT_PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_commerce_file_state CHECK (asset_state IN ('UPLOADING', 'ACTIVE', 'FAILED', 'DELETED', 'CLEANING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE commerce_file_quota (
    owner_id VARCHAR(64) NOT NULL,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (owner_id),
    CONSTRAINT ck_commerce_file_quota_used CHECK (used_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
