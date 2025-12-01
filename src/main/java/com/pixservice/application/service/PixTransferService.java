package com.pixservice.application.service;

import com.pixservice.application.dto.PixTransferRequest;
import com.pixservice.application.dto.PixTransferResponse;
import com.pixservice.application.idempotency.IdempotencyService;
import com.pixservice.application.idempotency.IdempotentResponse;
import com.pixservice.application.validation.PixTransferValidator;
import com.pixservice.domain.model.*;
import com.pixservice.domain.repository.*;
import com.pixservice.infrastructure.logging.MdcUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PixTransferService {

    private static final String METRIC_TAG_SERVICE_KEY = "service";
    private static final String METRIC_TAG_SERVICE_VALUE = "pix-transfer";

    private final WalletRepository walletRepository;
    private final PixKeyRepository pixKeyRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyService idempotencyService;
    private final PixTransferValidator validator;
    private final PixEventRepository pixEventRepository;

    // Métricas customizadas
    private final Counter pixTransferInitiatedCounter;
    private final Counter pixTransferIdempotentCounter;
    private final Timer pixTransferTimer;

    @Autowired
    public PixTransferService(WalletRepository walletRepository,
                               PixKeyRepository pixKeyRepository,
                               PixTransactionRepository pixTransactionRepository,
                               LedgerEntryRepository ledgerEntryRepository,
                               IdempotencyService idempotencyService,
                               PixTransferValidator validator,
                               MeterRegistry meterRegistry,
                               PixEventRepository pixEventRepository) {
        this.walletRepository = walletRepository;
        this.pixKeyRepository = pixKeyRepository;
        this.pixTransactionRepository = pixTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyService = idempotencyService;
        this.validator = validator;
        this.pixEventRepository = pixEventRepository;

        // Inicializar métricas
        this.pixTransferInitiatedCounter = Counter.builder("pix.transfer.initiated")
                .description("Total de transferências Pix iniciadas")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);

        this.pixTransferIdempotentCounter = Counter.builder("pix.transfer.idempotent")
                .description("Total de requisições idempotentes detectadas")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);

        this.pixTransferTimer = Timer.builder("pix.transfer.duration")
                .description("Tempo de processamento de transferências Pix")
                .tag(METRIC_TAG_SERVICE_KEY, METRIC_TAG_SERVICE_VALUE)
                .register(meterRegistry);
    }

    @Transactional
    public PixTransferResponse transfer(String idempotencyKeyHeader, PixTransferRequest request) {
        // Adicionar idempotencyKey ao MDC para rastreamento automático em todos os logs
        MdcUtils.setIdempotencyKey(idempotencyKeyHeader);
        MdcUtils.setWalletId(request.getFromWalletId());

        try {
            log.info("Iniciando transferência Pix - fromWallet={}, toPixKey={}, amount={}",
                    request.getFromWalletId(), request.getToPixKey(), request.getAmount());
            pixTransferInitiatedCounter.increment();

            Optional<PixTransferResponse> cached = checkIdempotency(idempotencyKeyHeader);
            if (cached.isPresent()) {
                pixTransferIdempotentCounter.increment();
                String endToEndId = cached.get().getEndToEndId();
                MdcUtils.setEndToEndId(endToEndId);
                log.info("Requisição idempotente detectada - endToEndId={}", endToEndId);
                return cached.get();
            }
            return processTransfer(idempotencyKeyHeader, request);
        } finally {
            // Limpar MDC após processamento
            MdcUtils.clearEndToEndId();
            MdcUtils.clearWalletId();
        }
    }

    private PixTransferResponse processTransfer(String idempotencyKeyHeader, PixTransferRequest request) {
        Wallet fromWalletRead = walletRepository.findById(request.getFromWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Carteira de origem não encontrada."));
        PixKey toPixKeyRead = findToPixKey(request.getToPixKey());
        Wallet toWalletRead = toPixKeyRead.getWallet();
        validator.validateTransfer(request.getAmount(), fromWalletRead, toWalletRead, toPixKeyRead.getKeyValue());

        String endToEndId = UUID.nameUUIDFromBytes(idempotencyKeyHeader.getBytes(StandardCharsets.UTF_8)).toString();
        MdcUtils.setEndToEndId(endToEndId); // Adicionar ao MDC para rastreamento
        PixTransferResponse provisionalResponse = new PixTransferResponse(endToEndId, PixTransactionStatus.PENDING);

        try {
            idempotencyService.saveIdempotentResponse(idempotencyKeyHeader, provisionalResponse, HttpStatus.ACCEPTED);
            log.info("Idempotent response registrada - endToEndId={}", endToEndId);
        } catch (DataIntegrityViolationException e) {
            pixTransferIdempotentCounter.increment();
            log.warn("Concorrência idempotente detectada - idempotencyKey={}", idempotencyKeyHeader);
            return idempotencyService.getIdempotentResponse(idempotencyKeyHeader, PixTransferResponse.class)
                    .map(IdempotentResponse::response)
                    .orElse(provisionalResponse);
        }

        Wallet fromWalletLocked = walletRepository.findByIdForUpdate(fromWalletRead.getId())
                .orElseThrow(() -> new IllegalArgumentException("Carteira de origem não encontrada (lock)."));
        if (pixTransactionRepository.existsById(endToEndId)) {
            log.info("Transação já existente após lock - endToEndId={}", endToEndId);
            return provisionalResponse;
        }

        log.info("Debitando carteira de origem - fromWallet={}, amount={}, endToEndId={}",
                fromWalletLocked.getId(), request.getAmount(), endToEndId);
        debitFromWallet(fromWalletLocked, request.getAmount(), endToEndId, toPixKeyRead.getKeyValue());

        PixTransaction pixTransaction = createPendingTransaction(endToEndId, fromWalletLocked, toWalletRead, toPixKeyRead, request, idempotencyKeyHeader);
        registerPendingCreditLedger(toWalletRead, fromWalletLocked, request.getAmount(), endToEndId);
        log.info("Transferência Pix criada - endToEndId={}, status={}, idempotencyKey={}", endToEndId, pixTransaction.getStatus(), idempotencyKeyHeader);
        return provisionalResponse;
    }

    private Optional<PixTransferResponse> checkIdempotency(String idempotencyKey) {
        return idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class)
                .map(IdempotentResponse::response);
    }



    private PixKey findToPixKey(String keyValue) {
        return pixKeyRepository.findByKeyValue(keyValue)
                .orElseThrow(() -> new IllegalArgumentException("Chave Pix de destino não encontrada."));
    }


    private void debitFromWallet(Wallet fromWallet, BigDecimal amount, String endToEndId, String toPixKey) {
        BigDecimal before = fromWallet.getBalance();
        fromWallet.withdraw(amount);
        walletRepository.save(fromWallet);
        BigDecimal after = fromWallet.getBalance();

        LedgerEntry outEntry = LedgerEntry.withdraw(fromWallet, amount, before, after,
                "Débito Pix - Transferência para " + toPixKey);
        outEntry.setTransactionId(endToEndId);
        ledgerEntryRepository.save(outEntry);
    }

    private PixTransaction createPendingTransaction(String endToEndId, Wallet fromWallet, Wallet toWallet,
                                                     PixKey toPixKey, PixTransferRequest request, String idempotencyKey) {
        PixTransaction pixTransaction = new PixTransaction(
                endToEndId, fromWallet, toPixKey.getKeyValue(), toPixKey.getType(), request.getAmount(), idempotencyKey);
        pixTransaction.setToWallet(toWallet);
        PixTransaction saved = pixTransactionRepository.save(pixTransaction);
        // Registrar evento INITIATED para rastreabilidade (usar UUID próprio para evitar colisão com header)
        if (pixEventRepository != null) {
            try {
                String initiatedEventId = UUID.randomUUID().toString();
                log.debug("Gerando evento INITIATED - endToEndId={}, eventId={}, fromWallet={}, toPixKey={}",
                    endToEndId, initiatedEventId, fromWallet.getId(), toPixKey.getKeyValue());
                PixEvent initiatedEvent = new PixEvent(initiatedEventId, endToEndId, PixEventType.INITIATED, saved.getInitiatedAt());
                pixEventRepository.save(initiatedEvent);
                log.info("Evento INITIATED registrado com sucesso - endToEndId={}, eventId={}", endToEndId, initiatedEventId);
            } catch (DataIntegrityViolationException e) {
                log.error("Falha ao registrar evento INITIATED por violação de constraint - endToEndId={}, motivo={}",
                    endToEndId, e.getMessage());
                // Não falhar a transação por causa do evento, apenas logar
            } catch (Exception e) {
                log.warn("Falha ao registrar evento INITIATED - endToEndId={}, motivo={}", endToEndId, e.getMessage());
            }
        }
        return saved;
    }

    private void registerPendingCreditLedger(Wallet toWallet, Wallet fromWallet, BigDecimal amount, String endToEndId) {
        BigDecimal toBefore = toWallet.getBalance();
        BigDecimal toAfter = toWallet.getBalance().add(amount);
        LedgerEntry inPendingEntry = LedgerEntry.deposit(toWallet, amount, toBefore, toAfter,
                "Crédito Pix - Transferência de " + fromWallet.getUserId() + " (PENDING)");
        inPendingEntry.setTransactionId(endToEndId);
        ledgerEntryRepository.save(inPendingEntry);
    }
}
