-- Flyway baseline schema for Pix Service

CREATE TABLE IF NOT EXISTS wallet (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    balance NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pix_key (
    id BIGSERIAL PRIMARY KEY,
    key_value VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    wallet_id BIGINT NOT NULL REFERENCES wallet(id),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS pix_transaction (
    end_to_end_id VARCHAR(255) PRIMARY KEY,
    from_wallet_id BIGINT REFERENCES wallet(id),
    to_wallet_id BIGINT REFERENCES wallet(id),
    to_pix_key VARCHAR(255) NOT NULL,
    to_pix_key_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255),
    initiated_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    rejected_at TIMESTAMP,
    last_update_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pix_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    end_to_end_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ledger_entry (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES wallet(id),
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    balance_before NUMERIC(19,2) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,
    transaction_id VARCHAR(255),
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS idempotency_key (
    id BIGSERIAL PRIMARY KEY,
    key_value VARCHAR(255) NOT NULL UNIQUE,
    response_body TEXT,
    http_status INT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
