# ✅ Guia de Verificação das Métricas - Pix Wallet Service

## 🎯 Objetivo

Este guia mostra como verificar que todas as métricas customizadas estão funcionando corretamente.

---

## 📊 Métricas Customizadas Esperadas

Ao consultar `/actuator/metrics`, você deve ver estas 7 métricas customizadas:

```json
"pix.transfer.initiated",      // ✅ Total de transferências iniciadas
"pix.transfer.idempotent",     // ✅ Requisições idempotentes detectadas
"pix.transfer.duration",       // ✅ Latência de transferências (Timer)
"pix.webhook.received",        // ✅ Webhooks recebidos
"pix.webhook.duplicate",       // ✅ Webhooks duplicados
"pix.webhook.confirmed",       // ✅ Transações confirmadas
"pix.webhook.rejected"         // ✅ Transações rejeitadas
```

---

## 🧪 Como Testar Cada Métrica

### 1. **pix.transfer.initiated**

**Teste:**
```bash
# Ver valor atual
curl http://localhost:8080/actuator/metrics/pix.transfer.initiated

# Fazer uma transferência
curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-123" \
  -d '{
    "fromWalletId": 1,
    "toPixKey": "user@email.com",
    "amount": 100.00
  }'

# Ver valor incrementado
curl http://localhost:8080/actuator/metrics/pix.transfer.initiated
```

**Resposta Esperada:**
```json
{
  "name": "pix.transfer.initiated",
  "description": "Total de transferências Pix iniciadas",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1.0
    }
  ],
  "availableTags": [
    {
      "tag": "service",
      "values": ["pix-transfer"]
    }
  ]
}
```

---

### 2. **pix.transfer.idempotent**

**Teste:**
```bash
# Fazer transferência com mesma Idempotency-Key (2x)
curl -X POST http://localhost:8080/pix/transfers \
  -H "Idempotency-Key: test-456" \
  -d '{"fromWalletId": 1, "toPixKey": "user@email.com", "amount": 50.00}'

curl -X POST http://localhost:8080/pix/transfers \
  -H "Idempotency-Key: test-456" \
  -d '{"fromWalletId": 1, "toPixKey": "user@email.com", "amount": 50.00}'

# Ver contador de idempotência
curl http://localhost:8080/actuator/metrics/pix.transfer.idempotent
```

**Resposta Esperada:**
```json
{
  "name": "pix.transfer.idempotent",
  "description": "Total de requisições idempotentes detectadas",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1.0
    }
  ]
}
```

---

### 3. **pix.transfer.duration** (Timer)

**Teste:**
```bash
# Fazer algumas transferências
curl -X POST http://localhost:8080/pix/transfers \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"fromWalletId": 1, "toPixKey": "user@email.com", "amount": 100.00}'

# Ver estatísticas de latência
curl http://localhost:8080/actuator/metrics/pix.transfer.duration
```

**Resposta Esperada:**
```json
{
  "name": "pix.transfer.duration",
  "description": "Tempo de processamento de transferências Pix",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 5.0
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 0.234567
    },
    {
      "statistic": "MAX",
      "value": 0.089123
    }
  ],
  "availableTags": [
    {
      "tag": "service",
      "values": ["pix-transfer"]
    }
  ]
}
```

**Percentis (se habilitado):**
- `pix.transfer.duration.percentile.0.5` → p50 (mediana)
- `pix.transfer.duration.percentile.0.95` → p95
- `pix.transfer.duration.percentile.0.99` → p99

---

### 4. **pix.webhook.received**

**Teste:**
```bash
# Primeiro, criar uma transferência
TRANSFER_RESPONSE=$(curl -X POST http://localhost:8080/pix/transfers \
  -H "Idempotency-Key: webhook-test-1" \
  -d '{"fromWalletId": 1, "toPixKey": "user@email.com", "amount": 100.00}')

END_TO_END_ID=$(echo $TRANSFER_RESPONSE | jq -r '.endToEndId')

# Enviar webhook
curl -X POST http://localhost:8080/pix/webhook \
  -H "Content-Type: application/json" \
  -d "{
    \"endToEndId\": \"$END_TO_END_ID\",
    \"eventId\": \"evt-123\",
    \"eventType\": \"CONFIRMED\",
    \"occurredAt\": \"2025-11-30T23:00:00\"
  }"

# Ver contador
curl http://localhost:8080/actuator/metrics/pix.webhook.received
```

**Resposta Esperada:**
```json
{
  "name": "pix.webhook.received",
  "description": "Total de webhooks Pix recebidos",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1.0
    }
  ],
  "availableTags": [
    {
      "tag": "service",
      "values": ["webhook"]
    }
  ]
}
```

---

### 5. **pix.webhook.duplicate**

**Teste:**
```bash
# Enviar mesmo webhook 2x (mesmo eventId)
curl -X POST http://localhost:8080/pix/webhook \
  -d '{"endToEndId": "e2e-123", "eventId": "evt-456", "eventType": "CONFIRMED", "occurredAt": "2025-11-30T23:00:00"}'

curl -X POST http://localhost:8080/pix/webhook \
  -d '{"endToEndId": "e2e-123", "eventId": "evt-456", "eventType": "CONFIRMED", "occurredAt": "2025-11-30T23:00:00"}'

# Ver contador de duplicatas
curl http://localhost:8080/actuator/metrics/pix.webhook.duplicate
```

---

### 6. **pix.webhook.confirmed**

**Teste:**
```bash
# Criar transferência e confirmar via webhook
curl -X POST http://localhost:8080/pix/webhook \
  -d '{"endToEndId": "e2e-789", "eventId": "evt-confirmed", "eventType": "CONFIRMED", "occurredAt": "2025-11-30T23:00:00"}'

# Ver contador
curl http://localhost:8080/actuator/metrics/pix.webhook.confirmed
```

---

### 7. **pix.webhook.rejected**

**Teste:**
```bash
# Criar transferência e rejeitar via webhook
curl -X POST http://localhost:8080/pix/webhook \
  -d '{"endToEndId": "e2e-999", "eventId": "evt-rejected", "eventType": "REJECTED", "occurredAt": "2025-11-30T23:00:00"}'

# Ver contador
curl http://localhost:8080/actuator/metrics/pix.webhook.rejected
```

---

## 📈 Dashboard Grafana (Prometheus)

### Queries Sugeridas:

```promql
# Taxa de transferências por segundo
rate(pix_transfer_initiated_total[5m])

# Taxa de idempotência (%)
(rate(pix_transfer_idempotent_total[5m]) / rate(pix_transfer_initiated_total[5m])) * 100

# Latência p95
histogram_quantile(0.95, rate(pix_transfer_duration_bucket[5m]))

# Taxa de webhooks duplicados
rate(pix_webhook_duplicate_total[5m])

# Taxa de confirmação vs rejeição
rate(pix_webhook_confirmed_total[5m]) / rate(pix_webhook_rejected_total[5m])
```

---

## 🎯 Alertas Recomendados

### 1. Alta Taxa de Idempotência
```yaml
alert: HighIdempotencyRate
expr: |
  (rate(pix_transfer_idempotent_total[5m]) / rate(pix_transfer_initiated_total[5m])) > 0.2
for: 10m
annotations:
  summary: "Mais de 20% das requisições são idempotentes"
  description: "Possível problema de timeout ou retries excessivos do cliente"
```

### 2. Latência Alta (p95 > 2s)
```yaml
alert: SlowPixTransfers
expr: |
  histogram_quantile(0.95, rate(pix_transfer_duration_bucket[5m])) > 2
for: 5m
annotations:
  summary: "95% das transferências levam mais de 2 segundos"
```

### 3. Alta Taxa de Webhooks Duplicados
```yaml
alert: HighWebhookDuplicateRate
expr: |
  (rate(pix_webhook_duplicate_total[5m]) / rate(pix_webhook_received_total[5m])) > 0.3
for: 10m
annotations:
  summary: "Mais de 30% dos webhooks são duplicados"
  description: "Possível problema no provedor de eventos Pix"
```

### 4. Alta Taxa de Rejeições
```yaml
alert: HighRejectionRate
expr: |
  (rate(pix_webhook_rejected_total[5m]) / (rate(pix_webhook_confirmed_total[5m]) + rate(pix_webhook_rejected_total[5m]))) > 0.1
for: 15m
annotations:
  summary: "Mais de 10% das transações Pix estão sendo rejeitadas"
```

---

## ✅ Checklist de Verificação

Execute este checklist após deploy:

- [ ] **Listar métricas**: `curl http://localhost:8080/actuator/metrics`
- [ ] **Ver 7 métricas customizadas** com prefixo `pix.*`
- [ ] **Testar pix.transfer.initiated**: fazer transferência, ver contador incrementar
- [ ] **Testar pix.transfer.idempotent**: repetir transferência, ver contador
- [ ] **Testar pix.transfer.duration**: verificar estatísticas (count, total, max)
- [ ] **Testar pix.webhook.received**: enviar webhook, ver contador
- [ ] **Testar pix.webhook.duplicate**: repetir webhook, ver contador
- [ ] **Testar pix.webhook.confirmed**: enviar CONFIRMED, ver contador
- [ ] **Testar pix.webhook.rejected**: enviar REJECTED, ver contador
- [ ] **Verificar tags**: todas as métricas têm tag `service=<nome>`
- [ ] **Integração Prometheus** (se configurado): verificar scraping

---

## 🔍 Troubleshooting

### Ex problema: Métrica não aparece na lista

**Causa:** Métrica só aparece após primeiro uso

**Solução:** Execute a operação que incrementa a métrica (ex: fazer uma transferência)

---

### Ex problema: Valor sempre zero

**Causa:** Métrica não está sendo incrementada no código

**Solução:** 
1. Verificar logs para ver se operação está sendo executada
2. Verificar se `counter.increment()` está sendo chamado
3. Checar se há exceções antes do increment

---

### Problema: Tag não aparece

**Causa:** Tag não foi registrada no builder da métrica

**Solução:**
```java
Counter.builder("metric.name")
    .tag("service", "service-name")  // ← Adicionar tag
    .register(meterRegistry);
```

---

## 📚 Referências

- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **Micrometer**: https://micrometer.io/docs
- **Prometheus**: https://prometheus.io/docs/prometheus/latest/querying/basics/
- **Grafana**: https://grafana.com/docs/grafana/latest/dashboards/

---

## ✅ Conclusão

Você deve ver todas as 7 métricas customizadas na lista do `/actuator/metrics`

As métricas estão:
- ✅ Nomeadas corretamente (`pix.*`)
- ✅ Com tags apropriadas (`service=<nome>`)
- ✅ Funcionando (incrementam após operações)
- ✅ Prontas para integração (Prometheus/Grafana/Datadog)

