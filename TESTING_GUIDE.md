# Guia Completo de Testes - Pix Service

## 1. Importar Coleção no Postman
1. Abra o Postman
2. Clique em **Import** (botão no canto superior esquerdo)
3. Selecione o arquivo `postman/Pix-Service-Collection.json`
4. A coleção "Pix Service - Code Assessment" será importada com todos os endpoints organizados

## 2. Fluxo de Teste Recomendado

### Fase 1: Setup Inicial - Criar Carteiras e Chaves Pix
Execute na ordem:

1. **Wallets → 1. Criar Carteira User1**
   - Cria carteira com saldo inicial de R$ 1000,00
   - Anote o `id` retornado (provavelmente será `1`)

2. **Wallets → 2. Criar Carteira User2**
   - Cria carteira com saldo inicial de R$ 500,00
   - Anote o `id` retornado (provavelmente será `2`)

3. **Pix Keys → 1. Registrar Chave Pix User1 (Email)**
   - Vincula email `user1@email.com` à carteira 1

4. **Pix Keys → 2. Registrar Chave Pix User2 (Email)**
   - Vincula email `user2@email.com` à carteira 2

5. **Pix Keys → 3. Registrar Chave Pix User2 (Telefone)**
   - Vincula telefone `+5511987654321` à carteira 2

### Fase 2: Operações Básicas
Execute para validar funcionalidades:

6. **Wallets → 3. Consultar Saldo da Carteira**
   - Deve retornar `{ "balance": 1000.00 }`

7. **Wallets → 5. Depósito na Carteira**
   - Adiciona R$ 200,00 à carteira 1
   - Novo saldo: R$ 1200,00

8. **Wallets → 6. Saque da Carteira**
   - Remove R$ 50,00 da carteira 1
   - Novo saldo: R$ 1150,00

9. **Wallets → 4. Consultar Saldo Histórico**
   - Ajuste o parâmetro `at` para um timestamp futuro
   - Deve retornar o saldo calculado até aquele momento

### Fase 3: Transferências Pix

10. **Pix Transfers → 1. Transferência Pix Normal**
    - Transfere R$ 100,00 de User1 para User2
    - Resposta: `{ "endToEndId": "<uuid>", "status": "PENDING" }`
    - **IMPORTANTE:** Copie o `endToEndId` retornado

11. **Pix Transfers → 2. Transferência Pix Idempotente (Duplicada)**
    - Execute esta requisição DUAS VEZES (mesmo Idempotency-Key)
    - Primeira: deve debitar R$ 50,00 e retornar PENDING
    - Segunda: deve retornar a MESMA resposta SEM novo débito
    - Valide que o saldo da carteira 1 só foi debitado UMA vez

12. **Pix Transfers → 3. Transferência com Saldo Insuficiente**
    - Tenta transferir R$ 10.000,00 (mais que o saldo)
    - Deve retornar erro 400 ou 422 com mensagem "Saldo insuficiente"

### Fase 4: Webhooks e Confirmação

13. **Pix Webhook → 1. Webhook CONFIRMED**
    - Cole o `endToEndId` da transferência do passo 10
    - Confirma a transferência
    - Valide que:
      - Carteira 1: saldo foi debitado (já estava pending)
      - Carteira 2: saldo foi CREDITADO agora (R$ 600,00)

14. **Pix Webhook → 2. Webhook CONFIRMED Duplicado (Idempotente)**
    - Envie o MESMO evento (mesmo `eventId`)
    - Deve retornar sucesso MAS não alterar saldos
    - Valide que o saldo da carteira 2 NÃO aumentou novamente

15. **Pix Webhook → 3. Webhook REJECTED**
    - Use um novo `endToEndId` de outra transferência
    - Deve rejeitar a transação e estornar o valor para a carteira de origem

16. **Pix Webhook → 4. Webhook REJECTED após CONFIRMED (Teste Ordem)**
    - Use o `endToEndId` já confirmado do passo 13
    - Simula evento REJECTED chegando fora de ordem
    - Deve apenas registrar evento SEM alterar saldos/estado
    - Estado da transação deve permanecer CONFIRMED

### Fase 5: Observabilidade

17. **Actuator → Health Check**
    - Valida que a aplicação está UP
    - Verifica status do Postgres

18. **Actuator → Metrics**
    - Lista métricas disponíveis (JVM, HTTP, etc.)

19. **Actuator → Info**
    - Informações da aplicação

## 3. Cenários de Teste de Concorrência (Manual)

### Teste A: Duplo Disparo de Transferência
1. Abra duas janelas do Postman
2. Configure a mesma requisição "Transferência Pix Idempotente" com o MESMO `Idempotency-Key`
3. Execute simultaneamente (clique rápido em Send nas duas janelas)
4. Valide:
   - Ambas retornam a mesma resposta
   - Apenas UM débito foi efetuado na carteira
   - Apenas UMA transação foi criada no banco

### Teste B: Webhook Duplicado
1. Execute "Webhook CONFIRMED"
2. Execute novamente com o mesmo `eventId`
3. Valide:
   - Ambas retornam sucesso
   - Saldo só foi creditado UMA vez
   - Apenas UM evento foi registrado no banco

### Teste C: Ordem Trocada de Webhooks
1. Execute "Webhook REJECTED" primeiro (com um novo endToEndId)
2. Tente executar "Webhook CONFIRMED" com o mesmo endToEndId
3. Valide:
   - Estado final respeita a máquina de estados
   - Não há inconsistência de saldo

## 4. Validações no Banco de Dados (Opcional)

Conecte ao Postgres e execute:

```sql
-- Verificar carteiras
SELECT * FROM wallet;

-- Verificar chaves Pix
SELECT * FROM pix_key;

-- Verificar transações
SELECT * FROM pix_transaction;

-- Verificar eventos (idempotência)
SELECT * FROM pix_event;

-- Verificar ledger (auditoria)
SELECT * FROM ledger_entry ORDER BY created_at;

-- Verificar idempotência de transferências
SELECT * FROM idempotency_key;
```

## 5. Resultados Esperados

### Saldos Finais (após todos os testes)
- **Carteira User1:** ~R$ 1000,00 (ajustado por depósitos/saques/transferências)
- **Carteira User2:** ~R$ 600,00 (saldo inicial + créditos Pix)

### Ledger Entries
- Devem existir entradas para:
  - Depósitos (PIX_TRANSFER_IN ou DEPOSIT)
  - Saques (WITHDRAWAL)
  - Transferências OUT (PIX_TRANSFER_OUT)
  - Transferências IN (PIX_TRANSFER_IN)
  - Estornos (PIX_TRANSFER_REVERSAL se houve rejected)

### Transações Pix
- Todas devem estar em estado final: CONFIRMED ou REJECTED
- Nenhuma deve permanecer PENDING após webhook

### Eventos
- `eventId` únicos (sem duplicatas no banco mesmo com reenvios)

### Idempotency Keys
- Cada `Idempotency-Key` único tem uma resposta armazenada
- Reenvios retornam a mesma resposta

## 6. Troubleshooting

### Erro 404 Not Found
- Verifique se os controllers estão implementados
- Verifique se a aplicação subiu corretamente

### Erro 500 Internal Server Error
- Verifique logs da aplicação
- Pode ser falha de validação ou constraint do banco

### Saldo não atualiza após webhook
- Verifique se o `endToEndId` está correto
- Verifique se a transação existe no banco
- Cheque logs de processamento do webhook

### Transferência não valida idempotência
- Verifique se o header `Idempotency-Key` está sendo enviado
- Confirme que está usando o MESMO valor nas requisições duplicadas

## 7. Cobertura de Requisitos

Esta coleção cobre:
- ✅ Criar Conta/Carteira
- ✅ Registrar Chave Pix
- ✅ Consultar Saldo
- ✅ Saldo Histórico
- ✅ Depósito
- ✅ Saque
- ✅ Transferência Pix (interna)
- ✅ Webhook Pix (CONFIRMED/REJECTED)
- ✅ Idempotência (transferências e webhooks)
- ✅ Concorrência (via testes manuais)
- ✅ Auditabilidade (ledger entries)
- ✅ Observabilidade (Actuator)

## 8. Próximos Passos

Após validar todos os endpoints:
1. Executar os testes automatizados: `mvn test`
2. Verificar cobertura de código
3. Revisar logs estruturados
4. Validar métricas no Actuator
5. Documentar quaisquer bugs ou melhorias identificadas

