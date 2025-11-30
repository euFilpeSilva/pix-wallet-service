# Métricas Customizadas - Pix Service

Este documento descreve as métricas customizadas disponíveis no Pix Service para monitoramento e observabilidade.

## 📊 Métricas Disponíveis

### Transferências Pix

#### `pix.transfer.initiated`
- **Tipo:** Counter
- **Descrição:** Total de transferências Pix iniciadas
- **Tags:** `service=pix-transfer`
- **Uso:** Monitorar volume total de transferências iniciadas

#### `pix.transfer.idempotent`
- **Tipo:** Counter
- **Descrição:** Total de requisições idempotentes detectadas (requisições duplicadas)
- **Tags:** `service=pix-transfer`
- **Uso:** Monitorar taxa de requisições duplicadas (retries de clientes)
- **Alertas Sugeridos:**
  - Alta taxa de idempotência pode indicar problemas de rede ou timeouts no cliente
  - Taxa > 20% do total de transferências iniciadas

#### `pix.transfer.duration`
- **Tipo:** Timer
- **Descrição:** Tempo de processamento de transferências Pix (em milissegundos)
- **Tags:** `service=pix-transfer`
- **Métricas Derivadas:**
  - `pix.transfer.duration.count` - Total de transferências processadas
  - `pix.transfer.duration.sum` - Tempo total de processamento
  - `pix.transfer.duration.max` - Tempo máximo de processamento
  - `pix.transfer.duration.mean` - Tempo médio de processamento
  - Percentis: p50, p95, p99
- **Uso:** Monitorar latência e performance das transferências
- **Alertas Sugeridos:**
  - p99 > 5 segundos (latência alta)
  - p95 > 2 segundos (degradação de performance)

### Webhooks Pix

#### `pix.webhook.received`
- **Tipo:** Counter
- **Descrição:** Total de webhooks Pix recebidos
- **Tags:** `service=webhook`
- **Uso:** Monitorar volume total de webhooks recebidos

#### `pix.webhook.duplicate`
- **Tipo:** Counter
- **Descrição:** Total de webhooks duplicados detectados
- **Tags:** `service=webhook`
- **Uso:** Monitorar eventos duplicados (comportamento normal mas deve ser baixo)
- **Alertas Sugeridos:**
  - Taxa > 10% do total de webhooks pode indicar problema no sistema externo

#### `pix.webhook.confirmed`
- **Tipo:** Counter
- **Descrição:** Total de transações Pix confirmadas via webhook
- **Tags:** `service=webhook`
- **Uso:** Monitorar taxa de sucesso das transferências

#### `pix.webhook.rejected`
- **Tipo:** Counter
- **Descrição:** Total de transações Pix rejeitadas via webhook
- **Tags:** `service=webhook`
- **Uso:** Monitorar taxa de falha das transferências
- **Alertas Sugeridos:**
  - Taxa de rejeição > 5% pode indicar problemas sistêmicos

## 📈 KPIs Derivados

### Taxa de Sucesso de Transferências
```
(pix.webhook.confirmed / (pix.webhook.confirmed + pix.webhook.rejected)) * 100
```
**Meta:** > 95%

### Taxa de Idempotência
```
(pix.transfer.idempotent / pix.transfer.initiated) * 100
```
**Meta:** < 20% (baixa taxa indica boa resiliência de rede)

### Taxa de Webhooks Duplicados
```
(pix.webhook.duplicate / pix.webhook.received) * 100
```
**Meta:** < 10%

### Latência Média de Transferências
```
pix.transfer.duration.mean
```
**Meta:** < 500ms (p95)

## 🔍 Acessando Métricas

### Endpoint Actuator
As métricas estão disponíveis via Spring Boot Actuator:

```bash
# Listar todas as métricas disponíveis
curl http://localhost:8080/actuator/metrics

# Ver métrica específica
curl http://localhost:8080/actuator/metrics/pix.transfer.initiated
curl http://localhost:8080/actuator/metrics/pix.transfer.duration
curl http://localhost:8080/actuator/metrics/pix.webhook.confirmed
```

### Resposta de Exemplo
```json
{
  "name": "pix.transfer.initiated",
  "description": "Total de transferências Pix iniciadas",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1543.0
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

## 📊 Integração com Sistemas de Monitoramento

### Prometheus
Adicione ao `pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Habilite endpoint Prometheus em `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

Acesse: `http://localhost:8080/actuator/prometheus`

### Grafana Dashboard
Exemplo de queries Prometheus:

```promql
# Taxa de transferências por minuto
rate(pix_transfer_initiated_total[1m])

# Latência p99
histogram_quantile(0.99, pix_transfer_duration_seconds_bucket)

# Taxa de sucesso
rate(pix_webhook_confirmed_total[5m]) / 
(rate(pix_webhook_confirmed_total[5m]) + rate(pix_webhook_rejected_total[5m])) * 100

# Taxa de webhooks duplicados
rate(pix_webhook_duplicate_total[5m]) / rate(pix_webhook_received_total[5m]) * 100
```

### CloudWatch (AWS)
Adicione ao `pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-cloudwatch2</artifactId>
</dependency>
```

Configure em `application.properties`:
```properties
management.metrics.export.cloudwatch.namespace=PixService
management.metrics.export.cloudwatch.step=1m
```

## 🚨 Alertas Recomendados

### Alertas Críticos (P0)
1. **Taxa de Sucesso < 90%**
   - Métrica: `pix.webhook.confirmed / (confirmed + rejected)`
   - Ação: Investigar imediatamente

2. **Latência p99 > 10 segundos**
   - Métrica: `pix.transfer.duration` (p99)
   - Ação: Verificar performance do banco e concorrência

3. **Sem webhooks recebidos em 10 minutos**
   - Métrica: `pix.webhook.received`
   - Ação: Verificar conectividade com sistema externo

### Alertas de Atenção (P1)
1. **Taxa de Idempotência > 30%**
   - Métrica: `pix.transfer.idempotent / initiated`
   - Ação: Verificar timeouts no cliente

2. **Taxa de Rejeição > 10%**
   - Métrica: `pix.webhook.rejected / (confirmed + rejected)`
   - Ação: Analisar motivos de rejeição

3. **Taxa de Webhooks Duplicados > 15%**
   - Métrica: `pix.webhook.duplicate / received`
   - Ação: Verificar sistema externo

## 📝 Logs Correlacionados

Todas as métricas são correlacionadas com logs estruturados contendo:
- `idempotencyKey` - Para rastreamento de requisições
- `endToEndId` - Para rastreamento de transações
- `eventId` - Para rastreamento de eventos webhook
- `walletId` - Para rastreamento por carteira

Exemplo de query de logs correlacionada:
```
# Buscar logs de uma transferência específica
idempotencyKey:"abc-123" OR endToEndId:"e2e-xyz"

# Buscar todas as rejeições nas últimas 24h
level:INFO AND "Processando rejeição Pix" AND timestamp:[now-24h TO now]
```

## 🎯 Dashboard Sugerido

Layout de dashboard recomendado:

### Row 1: Visão Geral
- Total de transferências (hoje)
- Taxa de sucesso (%)
- Latência média (ms)
- Total de webhooks processados

### Row 2: Transferências
- Gráfico: Transferências iniciadas vs. Tempo
- Gráfico: Taxa de idempotência vs. Tempo
- Gráfico: Latência (p50, p95, p99) vs. Tempo

### Row 3: Webhooks
- Gráfico: Webhooks recebidos vs. Tempo
- Gráfico: Confirmações vs. Rejeições vs. Tempo
- Gráfico: Taxa de webhooks duplicados vs. Tempo

### Row 4: Alertas Ativos
- Lista de alertas críticos ativos
- Últimas 10 rejeições (com motivo se disponível)
- Taxa de sucesso por hora (últimas 24h)

