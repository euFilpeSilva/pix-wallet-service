# 🚀 Guia Rápido de Observabilidade - Windows PowerShell

Este é um guia de referência rápida para desenvolvedores Windows que precisam trabalhar com logs e métricas do Pix Wallet Service.

---

## 📝 1. Capturar Logs em Arquivo

Por padrão, logs vão só para o console. Para filtrar, capture em arquivo primeiro:

```powershell
# Opção 1: Redirecionar com Tee-Object (mantém no console também)
mvn spring-boot:run | Tee-Object -FilePath .\application.log

# Opção 2: Definir arquivo via JVM argument
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dlogging.file.name=application.log"

# Opção 3: Se rodar JAR
java -Dlogging.file.name=application.log -jar .\target\pix-wallet-service-0.0.1-SNAPSHOT.jar
```

---

## 🔍 2. Filtrar Logs (equivalente ao grep)

### Buscar por Idempotency-Key
```powershell
Select-String -Path .\application.log -Pattern 'idempotencyKey=abc-123'
```

### Buscar por endToEndId
```powershell
Select-String -Path .\application.log -Pattern 'endToEndId=e2e-xyz-456'
```

### Buscar por eventId
```powershell
Select-String -Path .\application.log -Pattern 'eventId=evt-123'
```

### "Tail" em tempo real (acompanhar logs ao vivo)
```powershell
Get-Content .\application.log -Wait | Select-String -Pattern 'idempotencyKey=abc-123'
```

### Buscar múltiplos padrões
```powershell
Select-String -Path .\application.log -Pattern 'endToEndId=','idempotencyKey='
```

---

## 📊 3. Consultar Métricas

### Listar todas as métricas disponíveis
```powershell
# Com curl (se disponível)
curl http://localhost:8080/actuator/metrics

# Ou com PowerShell nativo
Invoke-WebRequest http://localhost:8080/actuator/metrics | Select-Object -ExpandProperty Content
```

### Ver métrica específica
```powershell
# Transferências iniciadas
Invoke-WebRequest http://localhost:8080/actuator/metrics/pix.transfer.initiated | Select-Object -ExpandProperty Content

# Requisições idempotentes
Invoke-WebRequest http://localhost:8080/actuator/metrics/pix.transfer.idempotent | Select-Object -ExpandProperty Content

# Latência de transferências
Invoke-WebRequest http://localhost:8080/actuator/metrics/pix.transfer.duration | Select-Object -ExpandProperty Content
```

### Formatar JSON bonito (opcional)
```powershell
Invoke-RestMethod http://localhost:8080/actuator/metrics/pix.transfer.initiated | ConvertTo-Json -Depth 10
```

---

## 🧪 4. Testar Métricas na Prática

### Como fazer pix.transfer.idempotent subir de 0

⚠️ **Importante:** Esta métrica SÓ incrementa quando você REPETE a MESMA Idempotency-Key.

```powershell
# 1. Primeira transferência (pix.transfer.initiated sobe +1)
$body = '{ "fromWalletId": 1, "toPixKey": "user@email.com", "amount": 100.00 }'
$headers = @{ 
    "Idempotency-Key" = "test-abc-123"
    "Content-Type" = "application/json" 
}
Invoke-RestMethod -Uri http://localhost:8080/pix/transfers -Method Post -Body $body -Headers $headers

# 2. Repetir com a MESMA chave (pix.transfer.idempotent sobe +1)
Invoke-RestMethod -Uri http://localhost:8080/pix/transfers -Method Post -Body $body -Headers $headers

# 3. Verificar contador
Invoke-RestMethod http://localhost:8080/actuator/metrics/pix.transfer.idempotent
```

**Resultado esperado:**
```json
{
  "name": "pix.transfer.idempotent",
  "measurements": [{ "statistic": "COUNT", "value": 1.0 }]
}
```

---

## 🔍 5. Verificar se Idempotência Funcionou

### Checar logs
```powershell
Select-String -Path .\application.log -Pattern 'Requisição idempotente detectada'
```

Se essa linha aparece → idempotência funcionou ✅  
Se NÃO aparece → você não repetiu a mesma chave ❌

---

## 📋 6. Referência Rápida - Comandos Essenciais

| Ação | Comando PowerShell |
|------|-------------------|
| **Capturar logs** | `mvn spring-boot:run \| Tee-Object -FilePath .\application.log` |
| **Filtrar logs** | `Select-String -Path .\application.log -Pattern 'texto'` |
| **Tail ao vivo** | `Get-Content .\application.log -Wait \| Select-String -Pattern 'texto'` |
| **Listar métricas** | `Invoke-WebRequest http://localhost:8080/actuator/metrics` |
| **Ver métrica** | `Invoke-RestMethod http://localhost:8080/actuator/metrics/nome.metrica` |
| **Teste idempotência** | Ver script acima (seção 4) |

---

## 🐛 7. Troubleshooting Comum

### Problema: "grep não é reconhecido"
**Solução:** Use `Select-String` no PowerShell (não é erro, grep é Unix).

### Problema: Métrica em 0.0 mesmo testando
**Causa provável:** Você não está repetindo a mesma chave.
**Solução:** Use exatamente o script da seção 4 acima.

### Problema: application.log não existe
**Causa:** Logs vão só para console por padrão.
**Solução:** Use um dos métodos da seção 1 para capturar.

### Problema: Select-String não retorna nada
**Verificar:**
1. O arquivo application.log existe? (`Test-Path .\application.log`)
2. Você rodou a operação que gera o log?
3. O padrão está correto? (case-sensitive)

---

## ✅ 8. Checklist Rápido

Antes de reportar "métricas não funcionam":

- [ ] Iniciei a aplicação capturando logs em arquivo
- [ ] Fiz a operação (ex: POST /pix/transfers)
- [ ] Para idempotência: usei a MESMA Idempotency-Key duas vezes
- [ ] Consultei a métrica APÓS a operação
- [ ] Verifiquei os logs com Select-String

---

## 📚 Documentação Completa

Para mais detalhes, veja: `docs/OBSERVABILIDADE.md`


