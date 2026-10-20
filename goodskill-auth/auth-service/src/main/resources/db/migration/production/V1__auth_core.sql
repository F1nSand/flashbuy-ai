-- Empty production identity schema. No demo accounts or passwords are inserted here.

CREATE TABLE user (
    id INT NOT NULL AUTO_INCREMENT,
    account VARCHAR(120) NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(120) NOT NULL,
    locked TINYINT NOT NULL DEFAULT 0,
    avatar VARCHAR(500) NULL,
    last_login_time DATETIME(6) NULL,
    mobile VARCHAR(40) NULL,
    email_addr VARCHAR(254) NULL,
    delete_flag TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    create_user VARCHAR(64) NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account (account),
    KEY ix_user_username (username),
    KEY ix_user_status (locked, delete_flag),
    CONSTRAINT ck_user_locked CHECK (locked IN (0, 1)),
    CONSTRAINT ck_user_delete_flag CHECK (delete_flag IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role (
    role_id INT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(120) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    role_code VARCHAR(80) NOT NULL,
    delete_flag TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    create_user VARCHAR(64) NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_code (role_code),
    CONSTRAINT ck_role_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_role_delete_flag CHECK (delete_flag IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permission (
    permission_id INT NOT NULL AUTO_INCREMENT,
    permission_name VARCHAR(160) NOT NULL,
    permission_menu VARCHAR(255) NULL,
    parent_permission_id INT NULL,
    is_dir VARCHAR(16) NULL,
    order_no INT NOT NULL DEFAULT 0,
    permission_code VARCHAR(160) NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    create_user VARCHAR(64) NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY ix_permission_parent_order (parent_permission_id, order_no),
    CONSTRAINT fk_permission_parent FOREIGN KEY (parent_permission_id) REFERENCES permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_role (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    create_user VARCHAR(64) NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_pair (user_id, role_id),
    KEY ix_user_role_role (role_id, user_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permission (
    id INT NOT NULL AUTO_INCREMENT,
    permission_id INT NOT NULL,
    role_id INT NOT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    create_user VARCHAR(64) NULL,
    update_user VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission_pair (role_id, permission_id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO role (role_name, status, role_code) VALUES
    ('Buyer', 1, 'buyer'),
    ('Goods manager', 1, 'goods-manager'),
    ('Flash sale manager', 1, 'seckill-manager'),
    ('System manager', 1, 'system-manager');
