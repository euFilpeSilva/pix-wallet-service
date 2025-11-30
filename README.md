# pix-wallet-service

## Visão Geral
Microserviço de carteira com suporte a Pix, desenvolvido em Java/Spring Boot seguindo princípios de Clean Architecture, com ênfase em consistência, concorrência segura, idempotência e auditabilidade via ledger e eventos.

## Decisões de Design
- Clean Architecture: camadas separadas em `application` (serviços e DTOs), `domain` (entidades e repositórios), `presentation` (controllers), e `infrastructure` (config).
- Persistência: Spring Data JPA com PostgreSQL; migrações via Flyway para garantir versionamento do esquema.
- Concorrência: controle otimista com `@Version` nas entidades críticas (`Wallet`, `PixTransaction`).
- Idempotência: tabela `idempotency_key` com `key_value` único para reuso de respostas.
- Auditabilidade: tabela `ledger_entry` (imutável) para trilha de crédito/débito por `endToEndId`; eventos Pix em `pix_event` com `event_id` único para idempotência no webhook.
- Estados de Transação: `PixTransactionStatus` com máquina de estados `PENDING -> CONFIRMED | REJECTED`.

## Trade-offs e Limitações (por tempo/escopo)
- Clean Architecture pragmática (acoplamento a JPA no domínio):
  - Entidades do domínio anotadas com JPA e repositórios estendem Spring Data. Alternativa mais "hexagonal" seria definir ports no domínio e adapters na infraestrutura com entidades de persistência separadas. Escolha pragmática para reduzir boilerplate e tempo de entrega.
- Locking otimista em vez de pessimista:
  - Optamos por `@Version` (optimistic) por simplicidade e escalabilidade. Em cenários de alta contenção, avaliar `SELECT FOR UPDATE` (pessimista) com backoff/retries curtos.
- Idempotência serializando resposta:
  - Armazenamos `response_body` (JSON) e `http_status` em `idempotency_key`. É simples e eficaz, mas acopla o armazenamento ao contrato de resposta. Alternativas: persistir campos normalizados ou reconstruir a resposta ao reprocessar.
- Testes de integração sem Testcontainers:
  - Unit tests usam H2; integração pode apontar para Postgres local via Docker. Trade-off: diferenças sutis entre H2 e Postgres. Recomendado migrar integrações para Testcontainers para isolamento e paridade de dialeto em CI.
- Tratamento de erros básico:
  - `GlobalExceptionHandler` ainda não usa RFC 7807 (ProblemDetail) nem mapeia todas as exceções (e.g., 409 para `DataIntegrityViolationException`, 422 para regras de negócio). Priorizado MVP. Próximo passo sugerido: ProblemDetails + Bean Validation.
- Valores monetários sem Value Object dedicado:
  - Uso de `BigDecimal` diretamente (escala/padrão). Em domínios financeiros maiores, introduzir `Money` como Value Object para padronizar operações e escala.
- Observabilidade mínima:
  - Actuator habilitado e logs estruturados simples. Próximo passo: adicionar MDC com `endToEndId`/`eventId`/`idempotencyKey` e métricas customizadas (Micrometer) por evento/transferência.
- Migrações em desenvolvimento:
  - Em dev, alteramos `V1__init.sql` e usamos reset/repair. Em produção, nunca alterar migrações aplicadas; sempre criar `V2__...` incremental.
- Documentação de contratos:
  - Coleção Postman entregue; Swagger/OpenAPI não incluído por tempo. Recomendado adicionar `springdoc-openapi`.

## Endpoints (contratos)
- `POST /wallets` → cria carteira
- `POST /wallets/{id}/pix-keys` → registra chave Pix
- `GET /wallets/{id}/balance` → saldo atual
- `GET /wallets/{id}/balance?at=<ISO>` → saldo histórico
- `POST /wallets/{id}/deposit` → depósito
- `POST /wallets/{id}/withdraw` → saque
- `POST /pix/transfers` → inicia transferência (header `Idempotency-Key: <uuid>`)
- `POST /pix/webhook` → processa eventos `CONFIRMED`/`REJECTED` (idempotente por `eventId`)

## Setup do Banco com Docker Compose
Crie e suba uma instância PostgreSQL local:

```powershell
# Na raiz do projeto
docker compose up -d
```

Compose provisiona:
- DB: `pixdb`
- Usuário: `pixuser`
- Senha: `pixpass`
- Porta: `5432`

O serviço `pix-service` lê as credenciais via variáveis de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`).

## Configuração de Credenciais via Ambiente
Para evitar segredos no código, as credenciais de banco são externalizadas:

`application.properties` usa placeholders:
```
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/pixdb}
spring.datasource.username=${DB_USER:pixuser}
spring.datasource.password=${DB_PASSWORD:pixpass}
```

Em desenvolvimento você pode usar os defaults. Em produção redefina as variáveis de ambiente sem reutilizar os defaults.

### Executando Localmente (sem Docker)
Defina as variáveis e rode a aplicação:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/pixdb"
$env:DB_USER="pixuser"
$env:DB_PASSWORD="pixpass"
mvn spring-boot:run
```

### Executando com Docker Compose
O serviço já recebe as variáveis via bloco `environment` no `docker-compose.yml`.

### Boas Práticas Futuras
- Usar um gerenciador de segredos (Vault / AWS Secrets Manager / Kubernetes Secrets).
- Rotacionar senhas periodicamente.
- Adicionar validação de presença de segredos críticos no startup (bean que loga warning se usando defaults).

## Configuração de Aplicação
- Prod/dev: `src/main/resources/application.properties` usa PostgreSQL e habilita Flyway (`ddl-auto=validate`).
- Testes: `src/test/resources/application-test.properties` usa H2 em modo compatível com Postgres para testes unitários rápidos.

## Migrações com Flyway
Migrations em `src/main/resources/db/migration`:
- `V1__init.sql`: cria tabelas `wallet`, `pix_key`, `pix_transaction`, `pix_event`, `ledger_entry`, `idempotency_key` com constraints e colunas de versão.

## Como Rodar
1. Suba o Postgres com Docker Compose:

```powershell
docker compose up -d
```

2. Execute a aplicação:

```powershell
mvn spring-boot:run
```

A aplicação sobe na porta `8080` e o Flyway valida/aplica o schema automaticamente.

## 🚀 Principais implementações

### 1. Logs Estruturados em Operações Críticas
- ✅ Adicionado `@Slf4j` em todos os Services
- ✅ Logs detalhados em transferências Pix (início, débito, conclusão)
- ✅ Logs detalhados em webhooks (recebimento, processamento, confirmação/rejeição)
- ✅ Logs em operações de carteira (criação, depósito, saque)
- ✅ Logs em registro de chaves Pix

### 2. Tratamento de Concorrência
- ✅ Retry automático para `OptimisticLockingFailureException` (3 tentativas com backoff exponencial)
- ✅ Tratamento de `DataIntegrityViolationException` em idempotency keys
- ✅ Salvamento de eventos webhook ANTES do processamento (previne race condition)
- ✅ Spring Retry habilitado com `@EnableRetry`

### 3. Métricas Customizadas
- ✅ `pix.transfer.initiated` - Total de transferências iniciadas
- ✅ `pix.transfer.idempotent` - Requisições idempotentes detectadas
- ✅ `pix.transfer.duration` - Latência de transferências (timer com percentis)
- ✅ `pix.webhook.received` - Total de webhooks recebidos
- ✅ `pix.webhook.duplicate` - Webhooks duplicados detectados
- ✅ `pix.webhook.confirmed` - Transações confirmadas
- ✅ `pix.webhook.rejected` - Transações rejeitadas
- 📊 Ver documentação completa em `docs/METRICAS.md`

### 4. Testes de Concorrência
- ✅ Teste de múltiplas threads com mesma Idempotency-Key
- ✅ Teste de transferências diferentes simultâneas
- ✅ Teste de depósitos/saques concorrentes
- ✅ Teste de prevenção de carteiras duplicadas
- ✅ Teste de prevenção de chaves Pix duplicadas

### 5. Índices de Performance
- ✅ Índice em `ledger_entry(wallet_id, created_at)` para saldo histórico
- ✅ Índice em `ledger_entry(transaction_id)` para rastreamento
- ✅ Índice em `pix_event(end_to_end_id)` para consultas de eventos
- ✅ Índices em `pix_transaction` por status e wallets

### 6. Correções
- ✅ Removido comentário TODO desatualizado em `WalletController`

## Como Testar

### Testes Automatizados
Execute todos os testes unitários e de integração:

```powershell
mvn test
```

**Resultado esperado:** 54 testes passando ✅ (50 originais + 4 de concorrência)
- 5 testes de PixKeyService
- 6 testes de PixTransferService
- 9 testes de WalletService
- 5 testes de WebhookService
- 6 testes de Wallet (domain model)
- 4 testes de integração PixKeyController
- 5 testes de integração PixTransferController
- 6 testes de integração WalletController
- 4 testes de integração WebhookController

Build rápido sem testes:

```powershell
mvn -DskipTests clean package
```

### Testes Manuais com Postman

#### 1. Importar Coleção
1. Abra o Postman
2. Clique em **Import**
3. Selecione o arquivo `postman/Pix-Service-Collection.json`
4. A coleção completa será importada com todos os endpoints organizados

#### 2. Coleção Inclui
- **Wallets:** criar carteiras, consultar saldo (atual e histórico), depósito, saque
- **Pix Keys:** registrar chaves Pix (email, telefone, CPF, EVP)
- **Pix Transfers:** transferências normais, idempotentes, com saldo insuficiente
- **Pix Webhook:** eventos CONFIRMED/REJECTED, duplicados, fora de ordem
- **Actuator:** health check, metrics, info

#### 3. Guia Completo de Testes
Consulte o arquivo `TESTING_GUIDE.md` para:
- Fluxo de teste passo a passo recomendado
- Cenários de teste de concorrência
- Validações no banco de dados
- Resultados esperados
- Troubleshooting

## Observabilidade
- Actuator habilitado: `/actuator/health`, `/actuator/info`, `/actuator/metrics`.
- Logs estruturados: chaveados por `endToEndId`, `eventId`, e `idempotencyKey` onde aplicável.
- Formato de log: `%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%thread] %logger{36} - %msg%n`

## Ajustes e Correções Realizados
1. **Testes do Webhook:** correção de verificação de chamadas ao repositório.
   - `WebhookServiceTest.shouldHandleRejectedEventAfterConfirmedSuccessfully` esperava `findById` 1x, mas o serviço chama 2x (uma por evento). Ajustado para 2x e verificados efeitos corretos (sem estorno se já confirmado).

2. **Testes do PixTransfer:** correção de stubbing do `ObjectMapper` para idempotência.
   - Evitamos construir JSON com o mock (que retornava `null`) e passamos um payload estático; stubbing de `readValue` com os argumentos exatos, resolvendo `PotentialStubbingProblem`.

3. **Postgres & Flyway:**
   - `pom.xml`: adicionados `org.postgresql:postgresql` e `org.flywaydb:flyway-core`.
   - `application.properties`: alterado para usar PostgreSQL e validar schema (`ddl-auto=validate`).
   - `docker-compose.yml`: serviço `postgres:16` com DB/credenciais, healthcheck e volume.
   - `V1__init.sql`: DDL inicial com constraints únicas (e.g., `pix_event.event_id`, `pix_key.key_value`), colunas de versão para concorrência e tipos numéricos para valores monetários.
   - `application-test.properties`: H2 em modo compatível com Postgres para unit tests.

4. **Schema Alignment:** alinhamento completo entre entidades JPA e schema Flyway.
   - Mapeamento snake_case: `balanceBefore` → `balance_before`, `balanceAfter` → `balance_after`, etc.
   - Todas as colunas de timestamp e enum como STRING adicionadas corretamente.
   - Resolução de erros de validação de schema do Hibernate.

5. **Logback:** configuração válida adicionada para resolver erro de inicialização do sistema de logging.

## Considerações de Concorrência
- `Wallet.withdraw` e `Wallet.deposit` atualizam `updatedAt` e contam com `@Version` para evitar escrituras concorrentes não detectadas.
- `PixTransaction` também possui `@Version`; confirmações/negações alteram estado de forma segura.
- Webhook idempotente por `eventId`; reprocessos não mudam saldo final.
- Transferências idempotentes por `Idempotency-Key`; mesmo header retorna mesma resposta sem novo débito.

## Estrutura do Projeto
```
pix-service/
├── src/
│   ├── main/
│   │   ├── java/com/pixservice/
│   │   │   ├── application/          # Serviços e DTOs
│   │   │   ├── domain/               # Entidades e repositórios
│   │   │   └── presentation/         # Controllers
│   │   └── resources/
│   │       ├── db/migration/         # Migrações Flyway
│   │       ├── application.properties
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/com/pixservice/      # Testes unitários e integração
│       └── resources/
│           └── application-test.properties
├── postman/
│   └── Pix-Service-Collection.json   # Coleção Postman
├── docker-compose.yml                # Postgres container
├── pom.xml
├── README.md
└── TESTING_GUIDE.md                  # Guia detalhado de testes
```

## Proximas evoluções
- Testcontainers para rodar integração contra PostgreSQL real em CI/CD.
- Índices adicionais:
  - `pix_transaction.idempotency_key`.
  - `ledger_entry.transaction_id`.
  - `ledger_entry.wallet_id + created_at` para consultas de histórico.
- Métricas customizadas (Micrometer) para contagem de eventos e transferências.
- Documentação OpenAPI/Swagger para endpoints.
- Profiles específicos para ambientes (dev, staging, prod).

## Tempo Investido
- Configuração Postgres/Flyway e Docker Compose: ~45 min
- Correções de testes e ajustes de idempotência: ~60 min
- Alinhamento de schema e resolução de erros: ~60 min
- Documentação, README e coleção Postman: ~30 min
- Validação end-to-end e testes: ~2 h
- Implementação do saldo histórico: ~15 min
- Refatoração do IdempotencyService no PixTransferService: ~20 min

**Total aproximado: ~6h25min**

## Contato e Suporte
Para dúvidas ou problemas:
1. Verifique logs da aplicação
2. Consulte `TESTING_GUIDE.md`
3. Execute `mvn test` para validar comportamento
4. Verifique health: `curl http://localhost:8080/actuator/health`
