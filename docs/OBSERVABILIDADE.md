# Observabilidade - Pix Wallet Service

## Visão Geral

Este documento descreve a infraestrutura completa de observabilidade implementada no Pix Wallet Service, incluindo logs estruturados com MDC, métricas customizadas e endpoints de monitoramento.

---

## 🔍 Logs Estruturados com MDC

### O que é MDC?

**MDC (Mapped Diagnostic Context)** é uma funcionalidade do SLF4J que permite adicionar informações de contexto que são automaticamente incluídas em todos os logs dentro do mesmo fluxo de execução.

### Benefícios do MDC

1. **Rastreamento de Requisições**: Acompanhe uma requisição através de múltiplas camadas (controller → service → repository)
2. **Correlação de Logs**: Filtre logs por `traceId`, `endToEndId`, `eventId` em ferramentas de logging (ELK, Splunk, Datadog)
3. **Debugging Facilitado**: Identifique rapidamente todos os logs relacionados a uma transação específica
4. **Menos Código**: Não precisa passar IDs manualmente em cada método

### Campos MDC Implementados

| Campo | Descrição | Adicionado por | Exemplo |
|-------|-----------|----------------|---------|
| `traceId` | ID de correlação único por requisição HTTP | `LoggingFilter` | `550e8400-e29b-41d4-a716-446655440000` |
| `idempotencyKey` | Chave de idempotência da transferência Pix | `LoggingFilter` / `PixTransferService` | `idempotency-123` |
| `endToEndId` | ID único da transação Pix | `PixTransferService` / `WebhookService` | `e2e-abc-123` |
| `eventId` | ID único do evento de webhook | `WebhookService` | `evt-xyz-789` |
| `walletId` | ID da carteira em operação | `PixTransferService` | `42` |

### Exemplo de Log com MDC

**Antes (sem MDC):**
```
2025-11-30 23:27:48.722 INFO [main] c.p.a.service.WebhookService - Recebido webhook Pix - eventId=8c8f4d0f-503f-4bb8-af93-c913661fbb97, endToEndId=08087818-a43e-3887-8871-63f16a7539f2, eventType=REJECTED
```

**Depois (com MDC):**
```
2025-11-30 23:27:48.722 INFO [main] [traceId=550e8400-e29b-41d4-a716-446655440000 endToEndId=08087818-a43e-3887-8871-63f16a7539f2 eventId=8c8f4d0f-503f-4bb8-af93-c913661fbb97 idempotencyKey=-] c.p.a.service.WebhookService - Recebido webhook Pix - eventType=REJECTED
```

✅ **Vantagens:**
- IDs no início do log (fácil de encontrar)
- Pode filtrar por `grep "endToEndId=08087818"` para ver toda a timeline
- Menos poluição no log (não repete IDs em cada mensagem)

---

## 🏗️ Infraestrutura de Logging

### 1. LoggingFilter

**Localização:** `com.pixservice.infrastructure.logging.LoggingFilter`

**Responsabilidade:**
- Intercepta todas as requisições HTTP
- Gera ou propaga `traceId` (X-Trace-Id header)
- Captura `Idempotency-Key` header se presente
- Adiciona ao MDC automaticamente
- Limpa MDC após processamento

**Uso:**
```java
@Component
public class LoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        // Gera traceId
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        
        // Captura Idempotency-Key
        String idempotencyKey = httpRequest.getHeader("Idempotency-Key");
        if (idempotencyKey != null) {
            MDC.put("idempotencyKey", idempotencyKey);
        }
        
        chain.doFilter(request, response);
        MDC.clear(); // Limpa após resposta
    }
}
```

### 2. MdcUtils

**Localização:** `com.pixservice.infrastructure.logging.MdcUtils`

**Responsabilidade:**
- Utilitário centralizado para gerenciar MDC
- Métodos para adicionar/remover contexto
- Type-safe (evita erros de string)

**Uso nos Services:**
```java
@Service
public class PixTransferService {
    public PixTransferResponse transfer(String idempotencyKey, PixTransferRequest request) {
        // Adicionar contexto ao MDC
        MdcUtils.setIdempotencyKey(idempotencyKey);
        MdcUtils.setWalletId(request.getFromWalletId());
        
        try {
            String endToEndId = generateEndToEndId(idempotencyKey);
            MdcUtils.setEndToEndId(endToEndId);
            
            // Todos os logs dentro deste método terão os IDs automaticamente
            log.info("Transferência criada"); // Log já inclui endToEndId, idempotencyKey, walletId
            
            return processTransfer(...);
        } finally {
            // Limpar contexto específico (traceId permanece)
            MdcUtils.clearEndToEndId();
            MdcUtils.clearWalletId();
        }
    }
}
```

### 3. Logback Configuration

**Localização:** `src/main/resources/logback-spring.xml`

**Pattern:**
```xml
<property name="CONSOLE_LOG_PATTERN" 
    value="%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%thread] [traceId=%X{traceId:-} endToEndId=%X{endToEndId:-} eventId=%X{eventId:-} idempotencyKey=%X{idempotencyKey:-}] %logger{36} - %msg%n"/>
```

**Explicação:**
- `%X{traceId:-}`: Obtém valor do MDC, ou `-` se não existir
- Todos os campos MDC aparecem no início do log
- Fácil de parsear e filtrar

---

## 📊 Métricas Customizadas (Micrometer)

### Métricas de Transferências Pix

#### `pix.transfer.initiated`
- **Tipo:** Counter
- **Descrição:** Total de transferências Pix iniciadas
- **Tags:** `service=pix-transfer`

#### `pix.transfer.idempotent`
- **Tipo:** Counter
- **Descrição:** Total de requisições idempotentes detectadas
- **Tags:** `service=pix-transfer`
- **Uso:** Monitorar taxa de retries de clientes

#### `pix.transfer.duration`
- **Tipo:** Timer
- **Descrição:** Latência de transferências Pix (em ms)
- **Tags:** `service=pix-transfer`
- **Métricas Derivadas:**
  - `pix.transfer.duration.count` - Total processado
  - `pix.transfer.duration.sum` - Tempo total
  - `pix.transfer.duration.mean` - Média
  - Percentis: p50, p95, p99

### Métricas de Webhooks

#### `pix.webhook.received`
- **Tipo:** Counter
- **Descrição:** Total de webhooks recebidos
- **Tags:** `service=webhook`

#### `pix.webhook.duplicate`
- **Tipo:** Counter
- **Descrição:** Total de webhooks duplicados detectados
- **Tags:** `service=webhook`

#### `pix.webhook.confirmed`
- **Tipo:** Counter
- **Descrição:** Total de transações confirmadas via webhook
- **Tags:** `service=webhook`

#### `pix.webhook.rejected`
- **Tipo:** Counter
- **Descrição:** Total de transações rejeitadas via webhook
- **Tags:** `service=webhook`

### Consultar Métricas

**Via Actuator (Linux/macOS/Windows - curl funciona em todos):**
```bash
# Listar todas as métricas
curl http://localhost:8080/actuator/metrics

# Métrica específica de transferências iniciadas
curl http://localhost:8080/actuator/metrics/pix.transfer.initiated

# Métrica de duração (Timer) com percentis
curl http://localhost:8080/actuator/metrics/pix.transfer.duration

# Métrica com filtro por tag
curl http://localhost:8080/actuator/metrics/pix.transfer.duration?tag=service:pix-transfer

# Métricas de webhook
curl http://localhost:8080/actuator/metrics/pix.webhook.received
curl http://localhost:8080/actuator/metrics/pix.webhook.duplicate
curl http://localhost:8080/actuator/metrics/pix.webhook.confirmed
curl http://localhost:8080/actuator/metrics/pix.webhook.rejected
```

**Alternativa Windows (PowerShell - se curl não estiver disponível):**
```powershell
# Listar todas as métricas
Invoke-WebRequest http://localhost:8080/actuator/metrics | Select-Object -ExpandProperty Content

# Métrica específica
Invoke-WebRequest http://localhost:8080/actuator/metrics/pix.transfer.initiated | Select-Object -ExpandProperty Content

# Formatar JSON (opcional)
Invoke-RestMethod http://localhost:8080/actuator/metrics/pix.transfer.initiated | ConvertTo-Json -Depth 10
```

---

## 🧪 Como Testar as Métricas

### Provocando pix.transfer.idempotent

⚠️ **Importante:** Esta métrica SÓ sobe quando você **repete** uma transferência com a **MESMA Idempotency-Key**.

**Windows (PowerShell):**
```powershell
# Primeira transferência (pix.transfer.initiated sobe +1)
$body = '{ "fromWalletId": 1, "toPixKey": "user@email.com", "amount": 100.00 }'
$headers = @{ "Idempotency-Key" = "test-abc-123"; "Content-Type" = "application/json" }
Invoke-RestMethod -Uri http://localhost:8080/pix/transfers -Method Post -Body $body -Headers $headers

# Repetir MESMA chave (pix.transfer.idempotent sobe +1)
Invoke-RestMethod -Uri http://localhost:8080/pix/transfers -Method Post -Body $body -Headers $headers

# Verificar contador
Invoke-RestMethod http://localhost:8080/actuator/metrics/pix.transfer.idempotent
```

**Linux/macOS:**
```bash
# Primeira transferência
curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-abc-123" \
  -d '{"fromWalletId":1,"toPixKey":"user@email.com","amount":100.00}'

# Repetir MESMA chave
curl -X POST http://localhost:8080/pix/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-abc-123" \
  -d '{"fromWalletId":1,"toPixKey":"user@email.com","amount":100.00}'

# Verificar contador
curl http://localhost:8080/actuator/metrics/pix.transfer.idempotent
```

**Resultado esperado:**
```json
{
  "name": "pix.transfer.idempotent",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1.0
    }
  ]
}
```

### Provocando pix.webhook.duplicate

Similar: envie o mesmo webhook (mesmo `eventId`) duas vezes.

---

## 🎯 Cenários de Uso

### 1. Rastrear uma Transferência Específica

**Objetivo:** Ver todos os logs de uma transferência do início ao fim

**Passo a Passo:**
1. Cliente faz `POST /pix/transfers` com `Idempotency-Key: abc-123`
2. Todos os logs terão `idempotencyKey=abc-123` e `endToEndId=<gerado>`
3. Filtre logs:
   ```bash
   grep "idempotencyKey=abc-123" application.log
   # ou
   grep "endToEndId=e2e-xyz-456" application.log
   ```

**Output Esperado:**
```
2025-11-30 23:27:48.155 INFO [http-nio-8080-exec-1] [traceId=550e8400... idempotencyKey=abc-123 ...] PixTransferService - Iniciando transferência Pix
2025-11-30 23:27:48.166 INFO [http-nio-8080-exec-1] [traceId=550e8400... idempotencyKey=abc-123 endToEndId=e2e-xyz-456 ...] PixTransferService - Idempotent response registrada
2025-11-30 23:27:48.193 INFO [http-nio-8080-exec-1] [traceId=550e8400... idempotencyKey=abc-123 endToEndId=e2e-xyz-456 ...] PixTransferService - Debitando carteira de origem
2025-11-30 23:27:48.205 INFO [http-nio-8080-exec-1] [traceId=550e8400... idempotencyKey=abc-123 endToEndId=e2e-xyz-456 ...] PixTransferService - Transferência Pix criada - status=PENDING
```

### 2. Rastrear Processamento de Webhook

**Objetivo:** Ver timeline completa de um evento de webhook

**Passo a Passo:**
1. Webhook chega com `eventId=evt-123` e `endToEndId=e2e-456`
2. Todos os logs terão ambos os IDs no MDC
3. Filtre:
   ```bash
   grep "eventId=evt-123" application.log
   ```

**Output Esperado:**
```
2025-11-30 23:27:48.722 INFO [http-nio-8080-exec-2] [traceId=... endToEndId=e2e-456 eventId=evt-123 ...] WebhookService - Recebido webhook Pix - eventType=CONFIRMED
2025-11-30 23:27:48.728 INFO [http-nio-8080-exec-2] [traceId=... endToEndId=e2e-456 eventId=evt-123 ...] WebhookService - Processando CONFIRMED
2025-11-30 23:27:48.732 INFO [http-nio-8080-exec-2] [traceId=... endToEndId=e2e-456 eventId=evt-123 ...] WebhookService - Crédito efetivado
2025-11-30 23:27:48.734 INFO [http-nio-8080-exec-2] [traceId=... endToEndId=e2e-456 eventId=evt-123 ...] WebhookService - Webhook processado - finalStatus=CONFIRMED
```

### 3. Debugging de Concorrência

**Objetivo:** Entender o que aconteceu quando múltiplas threads processaram a mesma transferência

**Passo a Passo:**
1. 5 requisições simultâneas com mesmo `Idempotency-Key`
2. Filtre por `idempotencyKey`:
   ```bash
   grep "idempotencyKey=abc-123" application.log | sort
   ```

**Output Esperado:**
```
[thread-1] PixTransferService - Iniciando transferência
[thread-2] PixTransferService - Iniciando transferência
[thread-1] PixTransferService - Idempotent response registrada
[thread-2] PixTransferService - Requisição idempotente detectada (cache hit)
[thread-3] PixTransferService - Iniciando transferência
[thread-3] PixTransferService - Requisição idempotente detectada (cache hit)
```

---

## 🚀 Recomendações de Produção

### 1. Integração com APM (Application Performance Monitoring)

**Ferramentas Sugeridas:**
- **Datadog**: Suporte nativo a MDC e métricas Micrometer
- **New Relic**: APM Java com rastreamento distribuído
- **Elastic APM**: Integração com ELK Stack

**Configuração:**
```properties
# application.properties
management.metrics.export.datadog.enabled=true
management.metrics.export.datadog.api-key=${DATADOG_API_KEY}
```

### 2. Logging em JSON (Estruturado)

**Para ambientes de produção, use formato JSON:**

```xml
<!-- logback-spring.xml -->
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdc>true</includeMdc>
        <includeContext>false</includeContext>
    </encoder>
</appender>
```

**Output:**
```json
{
  "timestamp": "2025-11-30T23:27:48.722Z",
  "level": "INFO",
  "logger": "com.pixservice.application.service.WebhookService",
  "message": "Recebido webhook Pix - eventType=CONFIRMED",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "endToEndId": "e2e-456",
  "eventId": "evt-123"
}
```

### 3. Alertas Sugeridos

**Métricas:**
```yaml
# Prometheus alerts
- alert: HighIdempotencyRate
  expr: rate(pix_transfer_idempotent_total[5m]) / rate(pix_transfer_initiated_total[5m]) > 0.2
  annotations:
    summary: "Alta taxa de requisições idempotentes (>20%)"

- alert: SlowPixTransfers
  expr: histogram_quantile(0.95, pix_transfer_duration_bucket) > 2000
  annotations:
    summary: "95% das transferências levam mais de 2s"
```

### 4. Dashboards

**Grafana Dashboard Sugerido:**
- Taxa de transferências iniciadas vs idempotentes (gauge)
- Latência p50/p95/p99 (time series)
- Taxa de webhooks duplicados (counter)
- Taxa de confirmações vs rejeições (pie chart)

---

## 🔧 Troubleshooting

### Métrica pix.transfer.idempotent está zerada

**Causa:** Você não está repetindo a mesma `Idempotency-Key`.

**Verificação:**
1. Cheque os logs:
   ```powershell
   # Windows
   Select-String -Path .\application.log -Pattern 'Requisição idempotente detectada'
   
   # Linux/macOS
   grep "Requisição idempotente detectada" application.log
   ```
2. Se essa linha NÃO aparece, significa que todas as suas requisições usaram chaves diferentes.
3. Para testar: use o script acima na seção "Como Testar as Métricas".

### Métrica não aparece na lista

**Causa:** Métrica só aparece depois do primeiro evento que a incrementa.

**Solução:** Execute a operação relacionada (ex: fazer uma transferência para ver `pix.transfer.initiated`).

### Comando grep não funciona no Windows

**Causa:** `grep` é um comando Unix/Linux. No Windows use `Select-String`.

**Solução:** Veja os exemplos PowerShell acima em cada seção.

---

## 📋 Checklist de Observabilidade

- ✅ **Logs estruturados** com contexto (MDC)
- ✅ **Correlation ID** em todas as requisições (traceId)
- ✅ **Métricas customizadas** para operações de negócio
- ✅ **Rastreamento de IDs** críticos (endToEndId, eventId, idempotencyKey)
- ✅ **Níveis de log** apropriados (INFO/WARN/ERROR/DEBUG)
- ✅ **Actuator endpoints** expostos
- ⚠️ **APM Integration** (recomendado para produção)
- ⚠️ **JSON Logging** (recomendado para produção)
- ⚠️ **Alerting** (configurar em produção)

---

## 🎓 Exemplos de Queries

### ELK Stack (Elasticsearch)

```json
// Buscar todos os logs de uma transferência
{
  "query": {
    "term": {
      "endToEndId": "e2e-xyz-456"
    }
  }
}

// Buscar requisições idempotentes
{
  "query": {
    "match": {
      "message": "Requisição idempotente detectada"
    }
  }
}

// Taxa de erro por endpoint
{
  "aggs": {
    "error_rate": {
      "terms": {
        "field": "level",
        "include": ["ERROR"]
      }
    }
  }
}
```

### Splunk

```spl
# Timeline de uma transferência
index=pix-service idempotencyKey="abc-123" | sort _time

# Eventos duplicados detectados
index=pix-service "Evento duplicado detectado" | stats count by eventId

# Latência de transferências
index=pix-service "Transferência Pix criada" | timechart avg(duration_ms)
```

---

## ✅ Conclusão

A observabilidade implementada permite:
1. **Rastreamento completo** de requisições através de MDC
2. **Correlação automática** de logs relacionados
3. **Métricas de negócio** para monitoramento proativo
4. **Debugging facilitado** com contexto preservado
5. **Preparação para produção** com infraestrutura escalável

