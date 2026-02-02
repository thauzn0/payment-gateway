# Operasyon Kılavuzu

<div align="center">

## 🔧 Operations Guide

**Deployment, Monitoring ve Troubleshooting**

---

</div>

## 🚀 Deployment

### Gereksinimler

| Bileşen | Minimum | Önerilen |
|---------|---------|----------|
| **CPU** | 2 core | 4 core |
| **RAM** | 4 GB | 8 GB |
| **Disk** | 20 GB SSD | 50 GB SSD |
| **Java** | 17 | 17 LTS |
| **MySQL** | 8.0 | 8.0 |
| **Docker** | 24.x | Latest |

### Quick Start

```bash
# 1. Repository'yi klonla
git clone https://github.com/company/payment-gateway.git
cd payment-gateway

# 2. Environment variables ayarla
cp .env.example .env
# .env dosyasını düzenle

# 3. Docker ile başlat
docker compose up -d

# 4. Health check
curl http://localhost:8080/v1/admin/health
```

### Production Deployment

```bash
# 1. Build
./mvnw clean package -DskipTests

# 2. Docker image oluştur
docker build -t payment-gateway:v1.0.0 .

# 3. Push to registry
docker push registry.company.com/payment-gateway:v1.0.0

# 4. Deploy (Kubernetes örneği)
kubectl apply -f k8s/deployment.yaml
```

---

## 📊 Monitoring

### Health Endpoints

| Endpoint | Açıklama |
|----------|----------|
| `/v1/admin/health` | Application health |
| `/v1/admin/metrics` | System metrics |
| `/actuator/info` | Application info |

### Health Check Response

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "SELECT 1"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 53687091200,
        "free": 42949672960,
        "threshold": 10485760
      }
    }
  }
}
```

### Key Metrics

```
┌─────────────────────────────────────────────────────────────────┐
│                     DASHBOARD METRICS                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  📊 Business Metrics                                             │
│  ───────────────────                                            │
│  • payment_total_count                                          │
│  • payment_success_rate                                         │
│  • payment_amount_total                                         │
│  • payment_by_status{status="CAPTURED"}                         │
│                                                                  │
│  ⚡ Performance Metrics                                          │
│  ─────────────────────                                          │
│  • http_request_duration_seconds                                │
│  • provider_latency_seconds{provider="GARANTI"}                 │
│  • database_query_duration_seconds                              │
│                                                                  │
│  🔄 System Metrics                                               │
│  ────────────────                                               │
│  • jvm_memory_used_bytes                                        │
│  • hikari_connections_active                                    │
│  • outbox_pending_count                                         │
│  • webhook_delivery_pending                                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Prometheus Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'payment-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['payment-gateway:8080']
    scrape_interval: 15s
```

### Grafana Dashboard

Önerilen paneller:

1. **Request Rate** - İstek/saniye
2. **Error Rate** - Hata oranı
3. **Latency (p50, p95, p99)** - Yanıt süreleri
4. **Payment Success Rate** - Başarı oranı
5. **Provider Distribution** - Provider dağılımı
6. **Webhook Delivery Rate** - Webhook teslimat oranı

---

## 🚨 Alerting

### Critical Alerts (P1)

```yaml
# alertmanager rules
groups:
  - name: payment-gateway-critical
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status="5xx"}[5m]) > 0.1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          
      - alert: DatabaseDown
        expr: up{job="mysql"} == 0
        for: 1m
        labels:
          severity: critical
          
      - alert: PaymentSuccessRateLow
        expr: payment_success_rate < 0.9
        for: 5m
        labels:
          severity: critical
```

### Warning Alerts (P2)

```yaml
      - alert: HighLatency
        expr: histogram_quantile(0.95, http_request_duration_seconds) > 1
        for: 5m
        labels:
          severity: warning
          
      - alert: WebhookBacklog
        expr: webhook_delivery_pending > 1000
        for: 10m
        labels:
          severity: warning
          
      - alert: OutboxBacklog
        expr: outbox_pending_count > 500
        for: 5m
        labels:
          severity: warning
```

---

## 🔍 Troubleshooting

### Common Issues

#### 1. Ödeme CREATED'da Kalıyor

```
Semptom: Ödeme status = CREATED, authorize yapılamıyor
```

**Kontrol Listesi:**
```bash
# 1. Payment detaylarını kontrol et
curl http://localhost:8080/v1/payments/{id}

# 2. Logları incele
docker logs payment_gateway_app | grep {payment_id}

# 3. Provider durumunu kontrol et
curl http://localhost:8080/v1/admin/health
```

**Olası Sebepler:**
- Provider timeout
- Invalid card token
- 3DS bekleniyor (threeDsRequired: true)

---

#### 2. Webhook Ulaşmıyor

```
Semptom: Merchant webhook almıyor
```

**Kontrol Listesi:**
```bash
# 1. Webhook delivery durumunu kontrol et
SELECT * FROM webhook_deliveries 
WHERE merchant_id = 'TRENDYOL' 
ORDER BY created_at DESC 
LIMIT 10;

# 2. Outbox durumunu kontrol et
SELECT * FROM outbox_events 
WHERE status = 'NEW' 
ORDER BY created_at DESC;

# 3. Webhook URL'i doğrula
SELECT webhook_url FROM merchant_configs 
WHERE merchant_id = 'TRENDYOL';
```

**Olası Sebepler:**
- Webhook URL yanlış/ulaşılamıyor
- Merchant server 2xx dönmüyor
- Signature validation hatası
- Outbox processor çalışmıyor

---

#### 3. Yüksek Latency

```
Semptom: İşlemler 1 saniyeden uzun sürüyor
```

**Kontrol Listesi:**
```bash
# 1. Database connection pool
SELECT * FROM information_schema.processlist;

# 2. Slow query log
SHOW VARIABLES LIKE 'slow_query%';

# 3. Provider latency
SELECT provider_name, AVG(latency_ms) 
FROM payment_attempts 
WHERE created_at > NOW() - INTERVAL 1 HOUR
GROUP BY provider_name;
```

**Olası Sebepler:**
- Database connection exhaustion
- Provider yavaş yanıt veriyor
- Network latency
- GC pauses

---

#### 4. 3DS Session Expired

```
Semptom: 3DS OTP doğrulama başarısız
```

**Kontrol:**
```sql
SELECT * FROM three_ds_sessions 
WHERE payment_id = '{payment_id}';
```

**Olası Sebepler:**
- Session 5 dakikayı geçti
- Max deneme (3) aşıldı
- Yanlış OTP

---

### Log Analysis

#### Correlation ID ile İzleme

```bash
# Belirli bir işlemi loglardan bul
docker logs payment_gateway_app 2>&1 | grep "corr-xxxx-xxxx"

# Payment ID ile filtreleme
docker logs payment_gateway_app 2>&1 | grep "payment_id=550e8400"
```

#### Log Format

```
2026-02-02 10:00:00.123 [corr-xxxx] [TRENDYOL] INFO  o.t.p.o.PaymentOrchestrator - Payment authorized successfully - paymentId: 550e8400
```

| Alan | Açıklama |
|------|----------|
| Timestamp | ISO 8601 format |
| Correlation ID | Request tracking |
| Merchant ID | İşlem sahibi |
| Level | DEBUG/INFO/WARN/ERROR |
| Class | Source class |
| Message | Log message |

---

## 🔄 Maintenance

### Database Maintenance

```sql
-- Günlük: Eski API loglarını temizle
DELETE FROM api_logs WHERE created_at < NOW() - INTERVAL 90 DAY;

-- Haftalık: Index optimize
OPTIMIZE TABLE payments, payment_attempts, transactions;

-- Aylık: Tablo istatistiklerini güncelle
ANALYZE TABLE payments, payment_attempts, outbox_events;
```

### Log Rotation

```bash
# logrotate config
/var/log/payment-gateway/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 app app
    sharedscripts
    postrotate
        systemctl reload payment-gateway
    endscript
}
```

### Backup Strategy

```bash
# Günlük full backup
mysqldump -u root -p payment_gateway > backup_$(date +%Y%m%d).sql

# Saatlik incremental (binlog)
mysqlbinlog --start-datetime="2026-02-02 00:00:00" \
            --stop-datetime="2026-02-02 01:00:00" \
            /var/log/mysql/mysql-bin.000001 > incremental.sql
```

---

## 📈 Scaling

### Horizontal Scaling

```yaml
# Kubernetes HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-gateway-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-gateway
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### Database Scaling

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATABASE TOPOLOGY                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│                    ┌─────────────┐                              │
│                    │   Primary   │                              │
│                    │   (Write)   │                              │
│                    └──────┬──────┘                              │
│                           │                                      │
│              ┌────────────┼────────────┐                        │
│              │            │            │                        │
│              ▼            ▼            ▼                        │
│       ┌──────────┐ ┌──────────┐ ┌──────────┐                   │
│       │ Replica1 │ │ Replica2 │ │ Replica3 │                   │
│       │  (Read)  │ │  (Read)  │ │  (Read)  │                   │
│       └──────────┘ └──────────┘ └──────────┘                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Security Operations

### Secret Rotation

```bash
# 1. Yeni secret oluştur
NEW_SECRET=$(openssl rand -hex 32)

# 2. Kubernetes secret güncelle
kubectl create secret generic payment-secrets \
    --from-literal=webhook-secret=$NEW_SECRET \
    --dry-run=client -o yaml | kubectl apply -f -

# 3. Rolling restart
kubectl rollout restart deployment/payment-gateway
```

### Access Management

```sql
-- Read-only user oluştur
CREATE USER 'reader'@'%' IDENTIFIED BY 'xxxx';
GRANT SELECT ON payment_gateway.* TO 'reader'@'%';

-- Application user
CREATE USER 'app'@'%' IDENTIFIED BY 'xxxx';
GRANT SELECT, INSERT, UPDATE ON payment_gateway.* TO 'app'@'%';
```

---

## 📞 Destek

### Escalation Matrix

| Severity | Response Time | Escalation Path |
|----------|---------------|-----------------|
| P1 - Critical | 15 dk | On-call → Team Lead → CTO |
| P2 - High | 1 saat | On-call → Team Lead |
| P3 - Medium | 4 saat | Team |
| P4 - Low | 24 saat | Backlog |

### Contact

| Rol | Email | Slack |
|-----|-------|-------|
| On-Call | oncall@company.com | #payment-alerts |
| Team Lead | lead@company.com | @lead |
| DevOps | devops@company.com | #devops |

---

## 📋 Runbook Checklist

### Daily

- [ ] Health check endpoints kontrol
- [ ] Error rate dashboard kontrol
- [ ] Webhook backlog kontrol
- [ ] Disk usage kontrol

### Weekly

- [ ] Log analysis
- [ ] Performance review
- [ ] Security scan results
- [ ] Dependency updates

### Monthly

- [ ] Database maintenance
- [ ] Secret rotation
- [ ] Capacity planning review
- [ ] Disaster recovery drill

---

<div align="center">

[← Teknik Özellikler](./06-TECHNICAL-SPECS.md) | [İçindekiler](./00-CONTENTS.md)

</div>
