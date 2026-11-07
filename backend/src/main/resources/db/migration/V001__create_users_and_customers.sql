-- Users are who can log in; customers hold the personal data of a bank client.

CREATE TABLE users
(
    id                    UUID         NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    status_reason         VARCHAR(255),
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower_case CHECK (email = LOWER(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_users_failed_login_attempts_non_negative CHECK (failed_login_attempts >= 0)
);

CREATE SEQUENCE customer_number_seq START WITH 10000001 MAXVALUE 99999999;

CREATE TABLE customers
(
    id                       UUID         NOT NULL,
    user_id                  UUID         NOT NULL,
    customer_number          CHAR(8)      NOT NULL,
    first_name               VARCHAR(100) NOT NULL,
    last_name                VARCHAR(100) NOT NULL,
    pesel                    CHAR(11)     NOT NULL,
    date_of_birth            DATE         NOT NULL,
    phone_number             VARCHAR(16)  NOT NULL,
    address_street           VARCHAR(100) NOT NULL,
    address_building_number  VARCHAR(10)  NOT NULL,
    address_apartment_number VARCHAR(10),
    address_postal_code      VARCHAR(10)  NOT NULL,
    address_city             VARCHAR(100) NOT NULL,
    address_country_code     CHAR(2)      NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_customers_user_id UNIQUE (user_id),
    CONSTRAINT uk_customers_customer_number UNIQUE (customer_number),
    CONSTRAINT uk_customers_pesel UNIQUE (pesel),
    CONSTRAINT ck_customers_customer_number_format CHECK (customer_number ~ '^[0-9]{8}$'),
    CONSTRAINT ck_customers_pesel_format CHECK (pesel ~ '^[0-9]{11}$')
);
