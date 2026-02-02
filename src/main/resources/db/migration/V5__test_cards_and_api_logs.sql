-- Test kartları tablosu (demo için fake kartlar)
CREATE TABLE test_cards (
    id BINARY(16) PRIMARY KEY,
    card_number VARCHAR(19) NOT NULL UNIQUE,
    card_holder VARCHAR(100) NOT NULL,
    expiry_month VARCHAR(2) NOT NULL,
    expiry_year VARCHAR(4) NOT NULL,
    cvv VARCHAR(4) NOT NULL,
    bank_name VARCHAR(64) NOT NULL,
    card_brand VARCHAR(32) NOT NULL,
    bin_prefix VARCHAR(6) NOT NULL,
    commission_rate DECIMAL(5,2) NOT NULL,
    should_fail BOOLEAN NOT NULL DEFAULT FALSE,
    fail_reason VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Demo test kartları
INSERT INTO test_cards (id, card_number, card_holder, expiry_month, expiry_year, cvv, bank_name, card_brand, bin_prefix, commission_rate, should_fail, fail_reason) VALUES
-- Başarılı kartlar
(UUID_TO_BIN(UUID()), '4546789012345678', 'AHMET YILMAZ', '12', '2028', '123', 'Garanti BBVA', 'VISA', '454678', 1.89, false, NULL),
(UUID_TO_BIN(UUID()), '5528790012345678', 'MEHMET DEMIR', '06', '2027', '456', 'İş Bankası', 'MASTERCARD', '552879', 1.95, false, NULL),
(UUID_TO_BIN(UUID()), '4006760012345678', 'AYSE KAYA', '03', '2029', '789', 'Yapı Kredi', 'VISA', '400676', 1.79, false, NULL),
(UUID_TO_BIN(UUID()), '5578290012345678', 'FATMA CELIK', '09', '2026', '321', 'Akbank', 'MASTERCARD', '557829', 2.10, false, NULL),

-- Başarısız kartlar (test senaryoları için)
(UUID_TO_BIN(UUID()), '4111111111111111', 'TEST FAIL', '12', '2025', '999', 'Test Bank', 'VISA', '411111', 0.00, true, 'INSUFFICIENT_FUNDS'),
(UUID_TO_BIN(UUID()), '4000000000000002', 'TEST TIMEOUT', '12', '2025', '888', 'Test Bank', 'VISA', '400000', 0.00, true, 'TIMEOUT');

-- API istek/cevap logları tablosu
CREATE TABLE api_logs (
    id BINARY(16) PRIMARY KEY,
    correlation_id VARCHAR(64) NOT NULL,
    payment_id BINARY(16) NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    request_headers TEXT NULL,
    request_body TEXT NULL,
    response_status INT NULL,
    response_body TEXT NULL,
    latency_ms BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_api_logs_correlation (correlation_id),
    INDEX idx_api_logs_payment (payment_id),
    INDEX idx_api_logs_created (created_at DESC)
);

-- 3D Secure işlemleri tablosu
CREATE TABLE three_ds_sessions (
    id BINARY(16) PRIMARY KEY,
    payment_id BINARY(16) NOT NULL,
    otp_code VARCHAR(6) NOT NULL DEFAULT '111111',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_3ds_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_3ds_payment (payment_id),
    INDEX idx_3ds_status (status)
);

-- Payments tablosuna 3DS ve kart alanları ekle
ALTER TABLE payments 
    ADD COLUMN requires_3ds BOOLEAN NOT NULL DEFAULT FALSE AFTER provider_reference,
    ADD COLUMN three_ds_session_id BINARY(16) NULL AFTER requires_3ds,
    ADD COLUMN card_bin VARCHAR(6) NULL AFTER three_ds_session_id,
    ADD COLUMN card_last_four VARCHAR(4) NULL AFTER card_bin;
