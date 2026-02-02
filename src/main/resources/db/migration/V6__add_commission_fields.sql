-- Add commission and provider fields to payments table
ALTER TABLE payments 
    ADD COLUMN commission_rate DECIMAL(5,2) NULL,
    ADD COLUMN commission_amount DECIMAL(19,2) NULL,
    ADD COLUMN net_amount DECIMAL(19,2) NULL,
    ADD COLUMN provider_name VARCHAR(64) NULL;

-- Update existing captured payments with default commission (for demo)
UPDATE payments 
SET commission_rate = 1.99,
    commission_amount = amount * 0.0199,
    net_amount = amount - (amount * 0.0199),
    provider_name = 'MOCK_PROVIDER'
WHERE status = 'CAPTURED' AND commission_rate IS NULL;
