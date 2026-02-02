-- Demo Merchant Configuration
INSERT INTO merchant_configs (id, merchant_id, merchant_name, api_key, webhook_url, webhook_secret, is_active, created_at, updated_at)
VALUES 
    (UUID_TO_BIN(UUID()), 'MERCHANT_001', 'Demo E-Commerce Store', 'demo-api-key-001', 'http://localhost:9090/webhook', 'webhook-secret-001', true, NOW(), NOW()),
    (UUID_TO_BIN(UUID()), 'MERCHANT_002', 'Test Marketplace', 'demo-api-key-002', NULL, NULL, true, NOW(), NOW());

-- Demo Routing Rules
-- Default rule: Use MOCK_PROVIDER for all transactions
INSERT INTO routing_rules (id, merchant_id, currency, card_bin_prefix, provider_name, priority, is_active, created_at)
VALUES 
    -- Global fallback rule (lowest priority)
    (UUID_TO_BIN(UUID()), NULL, NULL, NULL, 'MOCK_PROVIDER', 0, true, NOW()),
    
    -- TRY currency rule
    (UUID_TO_BIN(UUID()), NULL, 'TRY', NULL, 'MOCK_PROVIDER', 10, true, NOW()),
    
    -- USD currency rule  
    (UUID_TO_BIN(UUID()), NULL, 'USD', NULL, 'MOCK_PROVIDER', 10, true, NOW()),
    
    -- EUR currency rule
    (UUID_TO_BIN(UUID()), NULL, 'EUR', NULL, 'MOCK_PROVIDER', 10, true, NOW()),
    
    -- Merchant specific rule (highest priority)
    (UUID_TO_BIN(UUID()), 'MERCHANT_001', NULL, NULL, 'MOCK_PROVIDER', 100, true, NOW());
