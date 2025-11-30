package com.pixservice.application.service;

import com.pixservice.application.dto.PixWebhookRequest;
import com.pixservice.application.dto.PixWebhookResponse;
import com.pixservice.domain.model.*;
import com.pixservice.domain.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;

import static java.util.Objects.isNull;

@Service
@Slf4j
public class WebhookService {

    private static final String METRIC_TAG_SERVICE_KEY = "service";
    private static final String METRIC_TAG_SERVICE_VALUE = "webhook";
    private static final String RESPONSE_SUCCESS = "SUCCESS";
    private static final String RESPONSE_ERROR = "ERROR";

    @PersistenceContext
    private EntityManager entityManager;

    private final PixEventRepository pixEventRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // Métricas customizadas
    private final Counter webhookReceivedCounter;
    private final Counter webhookDuplicateCounter;
    private final Counter webhookConfirmedCounter;
    private final Counter webhookRejectedCounter;

    public WebhookService(PixEventRepository pixEventRepository,
                          PixTransactionRepository pixTransactionRepository,
                          WalletRepository walletRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          MeterRegistry meterRegistry) {
        this.pixEventRepository = pixEventRepository;
        this.pixTransactionRepository = pixTransactionRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;

        // Inicializar métricas
        this.webhookReceivedCounter = Counter.builder("pix.webhook.received")
                .description("Total de webhooks Pix recebidos")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);

        this.webhookDuplicateCounter = Counter.builder("pix.webhook.duplicate")
                .description("Total de webhooks duplicados detectados")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);

        this.webhookConfirmedCounter = Counter.builder("pix.webhook.confirmed")
                .description("Total de transações Pix confirmadas via webhook")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);

        this.webhookRejectedCounter = Counter.builder("pix.webhook.rejected")
                .description("Total de transações Pix rejeitadas via webhook")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);

        log.info("WebhookService inicializado - métricas registradas com tag {}={}.", METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE);
    }

    @Transactional
    public PixWebhookResponse processWebhookEvent(PixWebhookRequest request) {
        webhookReceivedCounter.increment();
        logRequest(request);

        // Idempotência do evento (duplicado completo já finalizado)
        PixWebhookResponse earlyDuplicateResponse = handleEarlyDuplicate(request);
        if (earlyDuplicateResponse != null) {
            return earlyDuplicateResponse;
        }

        // Persistir evento (garantir exatamente-uma vez por (eventId, endToEndId))
        PixWebhookResponse persistedDuplicateResponse = persistEvent(request);
        if (persistedDuplicateResponse != null) {
            return persistedDuplicateResponse; // Evento já registrado em corrida -> não reprocesar efeitos
        }

        // Carregar e bloquear transação para processamento seguro
        PixTransaction pixTransaction = loadAndLockTransaction(request.getEndToEndId());

        if (isAlreadyFinalized(pixTransaction)) {
            return new PixWebhookResponse(RESPONSE_SUCCESS, "Transação já em estado final ou processada. Evento registrado.");
        }

        // Processar com retry para conflitos otimistas
        return processWithRetry(pixTransaction, request);
    }

    // ---------------- Métodos privados (SRP) ----------------

    private void logRequest(PixWebhookRequest request) {
        log.info("Recebido webhook Pix - eventId={}, endToEndId={}, eventType={}, occurredAt={}",
                request.getEventId(), request.getEndToEndId(), request.getEventType(), request.getOccurredAt());
    }

    /**
     * Verifica duplicidade pelo par (eventId, endToEndId) e se a transação já está finalizada.
     * Se sim, retorna resposta imediata; se não, retorna null para continuar.
     */
    private PixWebhookResponse handleEarlyDuplicate(PixWebhookRequest request) {
        boolean duplicateForTx = pixEventRepository.existsByEventIdAndEndToEndId(request.getEventId(), request.getEndToEndId());
        if (!duplicateForTx) {
            return null;
        }
        PixTransaction existingTx = pixTransactionRepository.findById(request.getEndToEndId()).orElse(null);
        if (existingTx != null && existingTx.getStatus() != PixTransactionStatus.PENDING) {
            webhookDuplicateCounter.increment();
            log.info("Evento duplicado já aplicado - ignorando. eventId={}, endToEndId={}", request.getEventId(), request.getEndToEndId());
            return new PixWebhookResponse(RESPONSE_SUCCESS, "Evento já processado.");
        }
        log.warn("Evento duplicado porém transação ainda PENDING — processando. eventId={}, endToEndId={}", request.getEventId(), request.getEndToEndId());
        return null;
    }

    /**
     * Tenta persistir o evento. Se falhar por duplicidade concorrente, retorna resposta
     * de sucesso sem reaplicar efeitos. Caso contrário retorna null para continuar.
     */
    private PixWebhookResponse persistEvent(PixWebhookRequest request) {
        try {
            PixEvent event = new PixEvent(request.getEventId(), request.getEndToEndId(), request.getEventType(), request.getOccurredAt());
            pixEventRepository.save(event);
            log.debug("Evento Pix registrado - eventId={}", request.getEventId());
            return null; // Persistido com sucesso
        } catch (DataIntegrityViolationException e) {
            webhookDuplicateCounter.increment();
            log.warn("Evento duplicado detectado (race) - eventId={}, endToEndId={}", request.getEventId(), request.getEndToEndId());
            return new PixWebhookResponse(RESPONSE_SUCCESS, "Evento já processado.");
        }
    }

    private PixTransaction loadAndLockTransaction(String endToEndId) {
        PixTransaction tx = pixTransactionRepository.findById(endToEndId)
                .orElseThrow(() -> new IllegalArgumentException("Transação Pix não encontrada para o endToEndId: " + endToEndId));
        if (entityManager != null) {
            try { entityManager.lock(tx, LockModeType.PESSIMISTIC_WRITE); } catch (Exception e) { log.warn("Falha ao aplicar lock pessimista - endToEndId={}, motivo={}", endToEndId, e.getMessage()); }
        } else {
            log.debug("EntityManager nulo - lock pessimista ignorado. endToEndId={}", endToEndId);
        }
        return tx;
    }

    private boolean isAlreadyFinalized(PixTransaction pixTransaction) {
        return pixTransaction.getStatus() != PixTransactionStatus.PENDING;
    }

    private PixWebhookResponse processWithRetry(PixTransaction pixTransaction, PixWebhookRequest request) {
        int attempt = 0;
        final int maxAttempts = 3;
        while (true) {
            try {
                PixWebhookResponse response = applyEventEffects(pixTransaction, request);
                PixTransaction saved = pixTransactionRepository.save(pixTransaction);
                flushEntityManager(request.getEndToEndId());
                log.info("Webhook processado - eventId={}, endToEndId={}, finalStatus={}, version={}", request.getEventId(), request.getEndToEndId(), saved.getStatus(), saved.getVersion());
                return response;
            } catch (OptimisticLockException ole) {
                attempt++;
                if (attempt >= maxAttempts) {
                    log.error("Conflito otimista após {} tentativas - endToEndId={}", attempt, request.getEndToEndId());
                    throw ole;
                }
                log.warn("Conflito otimista retry attempt={} - endToEndId={}", attempt, request.getEndToEndId());
                pixTransaction = reloadAndLock(request.getEndToEndId());
            }
        }
    }

    /**
     * Força sincronização das mudanças persistentes com o banco de dados.
     * Ignora falhas silenciosamente pois não são críticas para o fluxo.
     *
     * @param endToEndId Identificador da transação para contexto de log
     */
    private void flushEntityManager(String endToEndId) {
        if (entityManager != null) {
            try {
                entityManager.flush();
            } catch (Exception e) {
                log.warn("Falha ao dar flush - endToEndId={}, motivo={}", endToEndId, e.getMessage());
            }
        }
    }

    private PixTransaction reloadAndLock(String endToEndId) {
        PixTransaction tx = pixTransactionRepository.findById(endToEndId)
                .orElseThrow(() -> new IllegalArgumentException("Transação Pix não encontrada para o endToEndId: " + endToEndId));
        if (entityManager != null) {
            try { entityManager.lock(tx, LockModeType.PESSIMISTIC_WRITE); } catch (Exception e) { log.warn("Falha lock pess retry - endToEndId={}, motivo={}", endToEndId, e.getMessage()); }
        }
        return tx;
    }

    private PixWebhookResponse applyEventEffects(PixTransaction pixTransaction, PixWebhookRequest request) {
        return switch (request.getEventType()) {
            case CONFIRMED -> processConfirmed(pixTransaction, request);
            case REJECTED -> processRejected(pixTransaction, request);
            default -> {
                log.error("Evento desconhecido - eventType={}, endToEndId={}", request.getEventType(), request.getEndToEndId());
                yield new PixWebhookResponse(RESPONSE_ERROR, "Tipo de evento Pix desconhecido.");
            }
        };
    }

    private PixWebhookResponse processConfirmed(PixTransaction pixTransaction, PixWebhookRequest request) {
        webhookConfirmedCounter.increment();
        log.info("Processando CONFIRMED - endToEndId={}, amount={}", request.getEndToEndId(), pixTransaction.getAmount());
        pixTransaction.confirm();
        Wallet toWallet = pixTransaction.getToWallet();
        if (isNull(toWallet)) throw new IllegalStateException("Carteira de destino não encontrada na transação Pix.");
        toWallet = walletRepository.findByIdForUpdate(toWallet.getId()).orElseThrow(() -> new IllegalArgumentException("Carteira de destino não encontrada."));
        BigDecimal before = toWallet.getBalance();
        toWallet.deposit(pixTransaction.getAmount());
        walletRepository.save(toWallet);
        BigDecimal after = toWallet.getBalance();
        log.info("Crédito efetivado - endToEndId={}, toWallet={}, amount={}, before={}, after={}", request.getEndToEndId(), toWallet.getId(), pixTransaction.getAmount(), before, after);
        LedgerEntry inEffective = LedgerEntry.deposit(toWallet, pixTransaction.getAmount(), before, after,
                "Crédito Pix - Transferência de " + pixTransaction.getFromWallet().getUserId());
        inEffective.setTransactionId(pixTransaction.getEndToEndId());
        inEffective.setType(LedgerEntryType.PIX_TRANSFER_IN);
        ledgerEntryRepository.save(inEffective);
        return new PixWebhookResponse(RESPONSE_SUCCESS, "Transação Pix confirmada e saldo creditado.");
    }

    private PixWebhookResponse processRejected(PixTransaction pixTransaction, PixWebhookRequest request) {
        webhookRejectedCounter.increment();
        log.info("Processando REJECTED - endToEndId={}, amount={}", request.getEndToEndId(), pixTransaction.getAmount());
        pixTransaction.reject();
        Wallet fromWallet = pixTransaction.getFromWallet();
        if (fromWallet == null) throw new IllegalStateException("Carteira de origem não encontrada na transação Pix.");
        fromWallet = walletRepository.findByIdForUpdate(fromWallet.getId()).orElseThrow(() -> new IllegalArgumentException("Carteira de origem não encontrada."));
        BigDecimal before = fromWallet.getBalance();
        fromWallet.deposit(pixTransaction.getAmount());
        walletRepository.save(fromWallet);
        BigDecimal after = fromWallet.getBalance();
        log.info("Estorno efetivado - endToEndId={}, fromWallet={}, amount={}, before={}, after={}", request.getEndToEndId(), fromWallet.getId(), pixTransaction.getAmount(), before, after);
        LedgerEntry reversal = LedgerEntry.deposit(fromWallet, pixTransaction.getAmount(), before, after,
                "Estorno Pix - Transação rejeitada de " + pixTransaction.getToPixKey());
        reversal.setTransactionId(pixTransaction.getEndToEndId());
        reversal.setType(LedgerEntryType.PIX_TRANSFER_REVERSAL);
        ledgerEntryRepository.save(reversal);
        return new PixWebhookResponse(RESPONSE_SUCCESS, "Transação Pix rejeitada e débito estornado.");
    }
}
