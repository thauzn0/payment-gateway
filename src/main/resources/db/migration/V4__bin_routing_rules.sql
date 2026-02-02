-- Routing rules tablosuna komisyon oranı ekle
ALTER TABLE routing_rules ADD COLUMN commission_rate DECIMAL(5,2) NULL AFTER priority;

-- BIN Veritabanı tablosu (hangi BIN hangi bankaya ait)
CREATE TABLE bin_database (
    bin_prefix VARCHAR(6) PRIMARY KEY,
    bank_name VARCHAR(64) NOT NULL,
    card_brand VARCHAR(32) NOT NULL,  -- VISA, MASTERCARD, TROY
    card_type VARCHAR(32) NOT NULL,   -- CREDIT, DEBIT, PREPAID
    country_code CHAR(2) NOT NULL DEFAULT 'TR'
);

-- Örnek BIN verileri (Gerçek değerler)
INSERT INTO bin_database (bin_prefix, bank_name, card_brand, card_type) VALUES
-- Garanti Bankası
('454678', 'Garanti BBVA', 'VISA', 'CREDIT'),
('454679', 'Garanti BBVA', 'VISA', 'CREDIT'),
('489458', 'Garanti BBVA', 'VISA', 'DEBIT'),

-- Yapı Kredi
('549220', 'Yapı Kredi', 'VISA', 'CREDIT'),
('552659', 'Yapı Kredi', 'MASTERCARD', 'CREDIT'),

-- İş Bankası
('552879', 'İş Bankası', 'MASTERCARD', 'CREDIT'),
('454671', 'İş Bankası', 'VISA', 'CREDIT'),

-- Akbank
('557829', 'Akbank', 'MASTERCARD', 'CREDIT'),
('402940', 'Akbank', 'VISA', 'CREDIT'),

-- Ziraat Bankası
('979205', 'Ziraat Bankası', 'TROY', 'CREDIT'),
('979206', 'Ziraat Bankası', 'TROY', 'DEBIT');

-- Mevcut routing kurallarını temizle ve yeniden ekle
DELETE FROM routing_rules;

-- Routing kuralları (commission_rate ile)
INSERT INTO routing_rules (id, merchant_id, currency, card_bin_prefix, provider_name, priority, commission_rate, is_active, created_at) VALUES
-- ON-US Kuralları (Aynı banka = Düşük komisyon) - En yüksek priority
(UUID_TO_BIN(UUID()), NULL, NULL, '454678', 'GARANTI_VPOS', 100, 0.99, true, NOW()),  -- Garanti kartı → Garanti POS
(UUID_TO_BIN(UUID()), NULL, NULL, '454679', 'GARANTI_VPOS', 100, 0.99, true, NOW()),
(UUID_TO_BIN(UUID()), NULL, NULL, '549220', 'YAPIKREDI_VPOS', 100, 1.09, true, NOW()), -- YapıKredi kartı → YapıKredi POS
(UUID_TO_BIN(UUID()), NULL, NULL, '552659', 'YAPIKREDI_VPOS', 100, 1.09, true, NOW()),
(UUID_TO_BIN(UUID()), NULL, NULL, '552879', 'ISBANK_VPOS', 100, 1.19, true, NOW()),    -- İşBank kartı → İşBank POS
(UUID_TO_BIN(UUID()), NULL, NULL, '454671', 'ISBANK_VPOS', 100, 1.19, true, NOW()),

-- TRY işlemleri için varsayılan (orta priority)
(UUID_TO_BIN(UUID()), NULL, 'TRY', NULL, 'GARANTI_VPOS', 50, 1.49, true, NOW()),

-- USD/EUR işlemleri için varsayılan (uluslararası)
(UUID_TO_BIN(UUID()), NULL, 'USD', NULL, 'IYZICO', 50, 2.49, true, NOW()),
(UUID_TO_BIN(UUID()), NULL, 'EUR', NULL, 'IYZICO', 50, 2.49, true, NOW()),

-- Global fallback (en düşük priority)
(UUID_TO_BIN(UUID()), NULL, NULL, NULL, 'MOCK_PROVIDER', 0, 1.99, true, NOW());
