# Teknik Özellikler

<div align="center">

## ⚙️ Technical Specifications

**API Referansı ve Entegrasyon Kılavuzu**

---

</div>

## 🔌 API Referansı

### Base URL

| Ortam | URL |
|-------|-----|
| Development | `http://localhost:8080` |
| Staging | `https://staging-api.payment-gateway.com` |
| Production | `https://api.payment-gateway.com` |

### Authentication

Tüm API istekleri aşağıdaki header'ları içermelidir:

```http
X-Merchant-Id: YOUR_MERCHANT_ID
X-API-Key: sk_live_xxxxxxxxxxxx
Content-Type: application/json
```

---

## 📝 Payment API

### 1. Ödeme Oluşturma

```http
POST /v1/payments
```

#### Request

```json
{
  "amount": 1500.00,
  "currency": "TRY",
  "orderId": "ORDER-12345",
  "customerEmail": "customer@email.com",
  "description": "Sipariş açıklaması"
}
```

#### Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "TRENDYOL",
  "amount": 1500.00,
  "currency": "TRY",
  "orderId": "ORDER-12345",
  "customerEmail": "c***@email.com",
  "description": "Sipariş açıklaması",
  "status": "CREATED",
  "providerReference": null,
  "createdAt": "2026-02-02T10:00:00Z",
  "updatedAt": "2026-02-02T10:00:00Z"
}
```

#### Parametreler

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|---------|----------|
| `amount` | decimal | ✅ | Ödeme tutarı (min: 0.01) |
| `currency` | string | ✅ | Para birimi (TRY, USD, EUR) |
| `orderId` | string | ❌ | Sipariş numarası |
| `customerEmail` | string | ❌ | Müşteri email |
| `description` | string | ❌ | Açıklama |

---

### 2. Ödeme Yetkilendirme (Authorize)

```http
POST /v1/payments/{paymentId}/authorize
```

#### Request

```json
{
  "cardToken": "tok_xxxxxxxxxxxx",
  "cardBin": "454678",
  "threeDsPreference": "WHEN_REQUIRED"
}
```

#### Response (Başarılı)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "AUTHORIZED",
  "providerReference": "GRN-2026020210001234",
  "threeDsRequired": false,
  "updatedAt": "2026-02-02T10:00:15Z"
}
```

#### Response (3DS Gerekli)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CREATED",
  "threeDsRequired": true,
  "threeDsUrl": "https://gateway.com/3ds/verify?session=abc123",
  "threeDsSessionId": "session-xxxx"
}
```

#### Parametreler

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|---------|----------|
| `cardToken` | string | ✅ | Tokenize edilmiş kart |
| `cardBin` | string | ✅ | Kartın ilk 6 hanesi |
| `threeDsPreference` | enum | ❌ | `ALWAYS`, `WHEN_REQUIRED`, `NEVER` |

---

### 3. Ödeme Çekimi (Capture)

```http
POST /v1/payments/{paymentId}/capture
```

#### Request

```json
{
  "amount": 1500.00
}
```

#### Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CAPTURED",
  "amount": 1500.00,
  "providerReference": "GRN-2026020210001234",
  "updatedAt": "2026-02-02T14:30:00Z"
}
```

#### Parametreler

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|---------|----------|
| `amount` | decimal | ❌ | Kısmi capture için tutar (default: full amount) |

---

### 4. İade (Refund)

```http
POST /v1/payments/{paymentId}/refund
```

#### Request

```json
{
  "amount": 500.00,
  "reason": "Kısmi iade - ürün iade edildi"
}
```

#### Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PARTIALLY_REFUNDED",
  "amount": 1500.00,
  "refundedAmount": 500.00,
  "updatedAt": "2026-02-05T09:00:00Z"
}
```

---

### 5. Ödeme Sorgulama

```http
GET /v1/payments/{paymentId}
```

#### Response

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "TRENDYOL",
  "amount": 1500.00,
  "currency": "TRY",
  "orderId": "ORDER-12345",
  "status": "CAPTURED",
  "providerReference": "GRN-2026020210001234",
  "createdAt": "2026-02-02T10:00:00Z",
  "updatedAt": "2026-02-02T14:30:00Z"
}
```

---

### 6. İşlem Geçmişi

```http
GET /v1/payments/{paymentId}/attempts
```

#### Response

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "attempts": [
    {
      "id": "att-xxxx-1",
      "operationType": "AUTHORIZE",
      "providerName": "GARANTI_VPOS",
      "status": "SUCCESS",
      "providerReference": "GRN-2026020210001234",
      "errorCode": null,
      "errorMessage": null,
      "createdAt": "2026-02-02T10:00:15Z"
    },
    {
      "id": "att-xxxx-2",
      "operationType": "CAPTURE",
      "providerName": "GARANTI_VPOS",
      "status": "SUCCESS",
      "providerReference": "GRN-2026020210001234",
      "createdAt": "2026-02-02T14:30:00Z"
    }
  ]
}
```

---

## ❌ Error Responses

### Error Format

```json
{
  "errorCode": "INVALID_PARAMETER",
  "message": "Amount must be greater than 0",
  "traceId": "corr-xxxx-xxxx-xxxx",
  "timestamp": "2026-02-02T10:00:00Z",
  "details": {
    "field": "amount",
    "rejectedValue": -100
  }
}
```

### Error Codes

| Code | HTTP Status | Açıklama |
|------|-------------|----------|
| `INVALID_PARAMETER` | 400 | Geçersiz parametre |
| `PAYMENT_NOT_FOUND` | 404 | Ödeme bulunamadı |
| `INVALID_PAYMENT_STATE` | 409 | Geçersiz durum geçişi |
| `IDEMPOTENCY_CONFLICT` | 409 | Farklı istek aynı key |
| `PROVIDER_ERROR` | 502 | Banka hatası |
| `INTERNAL_ERROR` | 500 | Sistem hatası |

---

## 🔔 Webhook Events

### Event Types

| Event | Trigger |
|-------|---------|
| `PaymentCreated` | Ödeme oluşturulduğunda |
| `PaymentAuthorized` | Kart blokesi yapıldığında |
| `PaymentCaptured` | Tutar çekildiğinde |
| `PaymentRefunded` | İade yapıldığında |
| `PaymentFailed` | İşlem başarısız olduğunda |

### Webhook Format

```http
POST https://your-webhook-url.com/payments
Content-Type: application/json
X-Webhook-Id: evt_xxxxxxxxxxxx
X-Webhook-Signature: sha256=xxxxxxxxxxxx
X-Webhook-Timestamp: 1706875800
```

```json
{
  "eventId": "evt_xxxxxxxxxxxx",
  "eventType": "PaymentCaptured",
  "timestamp": "2026-02-02T14:30:00Z",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "merchantId": "TRENDYOL",
    "status": "CAPTURED",
    "amount": 1500.00,
    "currency": "TRY",
    "orderId": "ORDER-12345"
  }
}
```

### Signature Verification

```javascript
const crypto = require('crypto');

function verifyWebhookSignature(payload, signature, secret) {
  const expectedSignature = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('hex');
  
  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(expectedSignature)
  );
}
```

---

## 🗄️ Veritabanı Şeması

### Core Tables

```sql
-- Ödemeler
CREATE TABLE payments (
    id              VARCHAR(36) PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL,
    amount          DECIMAL(19,4) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    order_id        VARCHAR(100),
    customer_email  VARCHAR(255),
    description     VARCHAR(500),
    status          VARCHAR(30) NOT NULL,
    provider_ref    VARCHAR(100),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_order_id (order_id),
    INDEX idx_status (status)
);

-- İşlem denemeleri
CREATE TABLE payment_attempts (
    id              VARCHAR(36) PRIMARY KEY,
    payment_id      VARCHAR(36) NOT NULL,
    operation_type  VARCHAR(20) NOT NULL,
    provider_name   VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    provider_ref    VARCHAR(100),
    error_code      VARCHAR(50),
    error_message   VARCHAR(500),
    created_at      TIMESTAMP NOT NULL,
    
    FOREIGN KEY (payment_id) REFERENCES payments(id),
    INDEX idx_payment_id (payment_id)
);

-- Finansal işlemler
CREATE TABLE transactions (
    id              VARCHAR(36) PRIMARY KEY,
    payment_id      VARCHAR(36) NOT NULL,
    type            VARCHAR(20) NOT NULL,  -- CAPTURE, REFUND
    amount          DECIMAL(19,4) NOT NULL,
    provider_ref    VARCHAR(100),
    created_at      TIMESTAMP NOT NULL,
    
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);
```

### Event Tables

```sql
-- Outbox (Event Sourcing)
CREATE TABLE outbox_events (
    id              VARCHAR(36) PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(36) NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload         JSON NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- NEW, SENT, FAILED
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    
    INDEX idx_status_created (status, created_at)
);

-- Webhook deliveries
CREATE TABLE webhook_deliveries (
    id              VARCHAR(36) PRIMARY KEY,
    merchant_id     VARCHAR(50) NOT NULL,
    event_id        VARCHAR(36) NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    payload         JSON NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- PENDING, DELIVERED, FAILED
    attempt_count   INT DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_retry_at   TIMESTAMP,
    
    INDEX idx_status_retry (status, next_retry_at)
);
```

---

## 🔧 Konfigürasyon

### Application Properties

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/payment_gateway
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

payment:
  routing:
    default-provider: MOCK_PROVIDER
    enable-bin-routing: true
  
  threeds:
    session-timeout-minutes: 5
    max-attempts: 3
  
  webhook:
    retry-intervals: [0, 30, 120, 600, 3600]  # seconds
    max-retries: 5
  
  outbox:
    poll-interval-seconds: 5
    batch-size: 100

logging:
  level:
    org.taha.paymentgateway: DEBUG
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 3306 |
| `DB_NAME` | Database name | payment_gateway |
| `DB_USERNAME` | Database user | - |
| `DB_PASSWORD` | Database password | - |
| `WEBHOOK_SECRET` | Webhook signing secret | - |
| `LOG_LEVEL` | Log level | INFO |

---

## 🐳 Docker Deployment

### docker-compose.yml

```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_NAME=payment_gateway
      - DB_USERNAME=payment_user
      - DB_PASSWORD=${DB_PASSWORD}
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: payment_gateway
      MYSQL_USER: payment_user
      MYSQL_PASSWORD: ${DB_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  mysql_data:
```

---

## 📊 SDK Örnekleri

### Java SDK

```java
PaymentGatewayClient client = PaymentGatewayClient.builder()
    .apiKey("sk_live_xxxx")
    .merchantId("TRENDYOL")
    .build();

// Create payment
PaymentResponse payment = client.payments().create(
    CreatePaymentRequest.builder()
        .amount(new BigDecimal("1500.00"))
        .currency("TRY")
        .orderId("ORDER-12345")
        .build()
);

// Authorize
AuthorizeResponse auth = client.payments().authorize(
    payment.getId(),
    AuthorizeRequest.builder()
        .cardToken("tok_xxxx")
        .cardBin("454678")
        .build()
);

// Capture
CaptureResponse capture = client.payments().capture(payment.getId());
```

### JavaScript/Node.js SDK

```javascript
const PaymentGateway = require('@company/payment-gateway-sdk');

const client = new PaymentGateway({
  apiKey: 'sk_live_xxxx',
  merchantId: 'TRENDYOL'
});

// Create payment
const payment = await client.payments.create({
  amount: 1500.00,
  currency: 'TRY',
  orderId: 'ORDER-12345'
});

// Authorize
const auth = await client.payments.authorize(payment.id, {
  cardToken: 'tok_xxxx',
  cardBin: '454678'
});

// Capture
const capture = await client.payments.capture(payment.id);
```

---

<div align="center">

[← İş Değeri](./05-BUSINESS-VALUE.md) | [Operasyon Kılavuzu →](./07-OPERATIONS.md)

</div>
