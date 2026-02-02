-- Payments tablosuna eksik alanlar
ALTER TABLE payments
    ADD COLUMN order_id VARCHAR(128) NULL AFTER currency,
    ADD COLUMN customer_email VARCHAR(255) NULL AFTER order_id,
    ADD COLUMN description VARCHAR(500) NULL AFTER customer_email,
    ADD COLUMN provider_reference VARCHAR(128) NULL AFTER description;

CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(status);

-- Payment attempts tablosuna eksik alanlar
ALTER TABLE payment_attempts
    ADD COLUMN provider_reference VARCHAR(128) NULL AFTER status,
    ADD COLUMN error_code VARCHAR(64) NULL AFTER provider_reference,
    ADD COLUMN error_message VARCHAR(500) NULL AFTER error_code,
    ADD COLUMN request_masked TEXT NULL AFTER error_message,
    ADD COLUMN response_masked TEXT NULL AFTER request_masked;

CREATE INDEX idx_attempts_payment_id ON payment_attempts(payment_id);

-- Transactions tablosu (para hareketleri kaydı)
CREATE TABLE transactions (
    id BINARY(16) PRIMARY KEY,
    payment_id BINARY(16) NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transaction_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_transactions_payment_id ON transactions(payment_id);

-- Outbox events tablosu (transactional outbox pattern)
CREATE TABLE outbox_events (
    id BINARY(16) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NULL
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);

-- Webhook deliveries tablosu
CREATE TABLE webhook_deliveries (
    id BINARY(16) PRIMARY KEY,
    event_id BINARY(16) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    response_code INT NULL,
    response_body TEXT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    last_attempt_at TIMESTAMP NULL,
    CONSTRAINT fk_webhook_event FOREIGN KEY (event_id) REFERENCES outbox_events(id)
);

CREATE INDEX idx_webhook_status ON webhook_deliveries(status);
CREATE INDEX idx_webhook_next_retry ON webhook_deliveries(next_retry_at);

-- Merchant configuration tablosu
CREATE TABLE merchant_configs (
    id BINARY(16) PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL UNIQUE,
    merchant_name VARCHAR(255) NOT NULL,
    api_key VARCHAR(128) NOT NULL,
    webhook_url VARCHAR(500) NULL,
    webhook_secret VARCHAR(128) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_merchant_api_key ON merchant_configs(api_key);

-- Provider routing rules tablosu
CREATE TABLE routing_rules (
    id BINARY(16) PRIMARY KEY,
    merchant_id VARCHAR(64) NULL,
    currency CHAR(3) NULL,
    card_bin_prefix VARCHAR(6) NULL,
    provider_name VARCHAR(64) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_routing_merchant ON routing_rules(merchant_id);
CREATE INDEX idx_routing_currency ON routing_rules(currency);
