# Payment Gateway

Enterprise-grade payment processing platform with multi-provider routing, 3D Secure support, and real-time webhooks.

## Features

- **Multi-step Payment Flow** - Create → Authorize → Capture → Refund
- **Smart Routing** - BIN-based provider selection for optimal commission rates
- **3D Secure 2.0** - Secure cardholder authentication
- **Webhook Delivery** - Reliable event notifications with retry logic
- **Idempotency** - Safe request retries
- **API Logging** - Complete audit trail with correlation IDs

## Tech Stack

### Backend
- Java 17
- Spring Boot 4.0.1
- MySQL 8.0
- Flyway migrations
- Docker

### Frontend
- React 18
- Vite 7
- Tailwind CSS 4
- React Router 6

## Quick Start

```bash
# Clone repository
git clone https://github.com/yourusername/payment-gateway.git
cd payment-gateway

# Start with Docker
docker compose up -d

# Access
# Backend: http://localhost:8080
# Frontend: http://localhost:3000
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/payments` | Create payment |
| POST | `/v1/payments/{id}/authorize` | Authorize payment |
| POST | `/v1/payments/{id}/capture` | Capture payment |
| POST | `/v1/payments/{id}/refund` | Refund payment |
| GET | `/v1/payments/{id}` | Get payment |

## Project Structure

```
payment-gateway/
├── src/main/java/org/taha/paymentgateway/
│   ├── api/           # REST controllers
│   ├── orchestrator/  # Payment flow coordination
│   ├── provider/      # Payment provider adapters
│   ├── routing/       # Smart routing engine
│   ├── threeds/       # 3D Secure handling
│   ├── webhook/       # Webhook delivery
│   ├── event/         # Event processing (outbox)
│   └── persistence/   # JPA entities & repositories
├── frontend/          # React application
└── docs/              # Documentation
```

## Documentation

See the [docs](./docs) folder for detailed documentation:

- [Executive Summary](./docs/01-EXECUTIVE-SUMMARY.md)
- [Architecture](./docs/02-ARCHITECTURE.md)
- [Payment Flow](./docs/03-PAYMENT-FLOW.md)
- [Security](./docs/04-SECURITY.md)
- [Business Value](./docs/05-BUSINESS-VALUE.md)
- [Technical Specs](./docs/06-TECHNICAL-SPECS.md)
- [Operations](./docs/07-OPERATIONS.md)

## License

MIT
