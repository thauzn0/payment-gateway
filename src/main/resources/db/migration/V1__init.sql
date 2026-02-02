CREATE TABLE payments (
                          id BINARY(16) PRIMARY KEY,
                          merchant_id VARCHAR(64) NOT NULL,
                          amount DECIMAL(19,2) NOT NULL,
                          currency CHAR(3) NOT NULL,
                          status VARCHAR(32) NOT NULL,
                          created_at TIMESTAMP NOT NULL,
                          updated_at TIMESTAMP NOT NULL
);

CREATE TABLE payment_attempts (
                                  id BINARY(16) PRIMARY KEY,
                                  payment_id BINARY(16) NOT NULL,
                                  provider VARCHAR(64) NOT NULL,
                                  operation VARCHAR(32) NOT NULL,
                                  status VARCHAR(32) NOT NULL,
                                  latency_ms BIGINT NOT NULL,
                                  created_at TIMESTAMP NOT NULL,
                                  CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE idempotency_records (
                                     id BINARY(16) PRIMARY KEY,
                                     idempotency_key VARCHAR(128) NOT NULL,
                                     request_hash VARCHAR(128) NOT NULL,
                                     response_body TEXT NOT NULL,
                                     created_at TIMESTAMP NOT NULL,
                                     UNIQUE KEY uk_idempotency_key (idempotency_key)
);