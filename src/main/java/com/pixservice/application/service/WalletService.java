package com.pixservice.application.service;

import com.pixservice.application.dto.CreateWalletRequest;
import com.pixservice.application.dto.WalletResponse;
import com.pixservice.domain.model.LedgerEntry;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.LedgerEntryRepository;
import com.pixservice.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    public static final String CARTEIRA_NAO_ENCONTRADA = "Carteira não encontrada.";
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Criando carteira - userId={}, initialBalance={}", request.getUserId(), request.getInitialBalance());

        Optional<Wallet> existingWallet = walletRepository.findByUserId(request.getUserId());
        if (existingWallet.isPresent()) {
            log.warn("Tentativa de criar carteira duplicada - userId={}", request.getUserId());
            throw new IllegalArgumentException("Carteira para este usuário já existe.");
        }

        if (request.getInitialBalance() == null || request.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
            log.error("Saldo inicial inválido - userId={}, initialBalance={}", request.getUserId(), request.getInitialBalance());
            throw new IllegalArgumentException("Saldo inicial inválido.");
        }

        try {
            Wallet wallet = new Wallet(request.getUserId(), request.getInitialBalance());
            wallet = walletRepository.save(wallet);

            // Registrar abertura de saldo no ledger quando houver saldo inicial
            if (request.getInitialBalance().compareTo(BigDecimal.ZERO) > 0) {
                LedgerEntry opening = LedgerEntry.openingBalance(wallet, request.getInitialBalance());
                ledgerEntryRepository.save(opening);
            }

            log.info("Carteira criada com sucesso - walletId={}, userId={}, balance={}",
                    wallet.getId(), wallet.getUserId(), wallet.getBalance());

            return toWalletResponse(wallet);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Violação de unicidade ao criar carteira - userId={}", request.getUserId());
            throw new IllegalArgumentException("Carteira para este usuário já existe.");
        }
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletById(Long id) {
        return walletRepository.findById(id)
                .map(this::toWalletResponse)
                .orElseThrow(() -> new IllegalArgumentException(CARTEIRA_NAO_ENCONTRADA));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(String userId) {
        return walletRepository.findByUserId(userId)
                .map(this::toWalletResponse)
                .orElseThrow(() -> new IllegalArgumentException(CARTEIRA_NAO_ENCONTRADA + " para o usuário: " + userId));
    }

    @Transactional
    public WalletResponse deposit(Long walletId, BigDecimal amount) {
        log.info("Realizando depósito - walletId={}, amount={}", walletId, amount);

        if (isNull(amount) || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Valor de depósito inválido - walletId={}, amount={}", walletId, amount);
            throw new IllegalArgumentException("O valor do depósito deve ser positivo.");
        }
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException(CARTEIRA_NAO_ENCONTRADA));

        BigDecimal before = wallet.getBalance();
        wallet.deposit(amount);
        wallet = walletRepository.save(wallet); // Pessimistic lock já adquirido
        BigDecimal after = wallet.getBalance();

        log.info("Depósito realizado com sucesso - walletId={}, amount={}, balanceBefore={}, balanceAfter={}",
                walletId, amount, before, after);

        // salva o historico de saldo
        LedgerEntry ledgerEntry = LedgerEntry.deposit(wallet, amount, before, after, "Depósito");
        ledgerEntryRepository.save(ledgerEntry);

        return toWalletResponse(wallet);
    }

    @Transactional
    public WalletResponse withdraw(Long walletId, BigDecimal amount) {
        log.info("Realizando saque - walletId={}, amount={}", walletId, amount);

        if (isNull(amount) || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Valor de saque inválido - walletId={}, amount={}", walletId, amount);
            throw new IllegalArgumentException("O valor do saque deve ser positivo.");
        }
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new IllegalArgumentException(CARTEIRA_NAO_ENCONTRADA));
        
        BigDecimal before = wallet.getBalance();
        wallet.withdraw(amount);
        wallet = walletRepository.save(wallet);
        BigDecimal after = wallet.getBalance();

        log.info("Saque realizado com sucesso - walletId={}, amount={}, balanceBefore={}, balanceAfter={}",
                walletId, amount, before, after);

        LedgerEntry ledgerEntry = LedgerEntry.withdraw(wallet, amount, before, after, "Saque");
        ledgerEntryRepository.save(ledgerEntry);

        return toWalletResponse(wallet);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance(), wallet.getCreatedAt(), wallet.getUpdatedAt());
    }

    /**
     * Calcula o saldo histórico da carteira em um determinado momento no tempo.
     * Soma todas as entradas do ledger até o timestamp especificado.
     *
     * @param walletId ID da carteira
     * @param at Timestamp para consulta do saldo
     * @return WalletResponse com o saldo no momento especificado
     */
    @Transactional(readOnly = true)
    public WalletResponse getHistoricalBalance(Long walletId, LocalDateTime at) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException(CARTEIRA_NAO_ENCONTRADA));

        // Busca todas as entradas do ledger até o timestamp especificado
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByWalletAndCreatedAtBeforeOrderByCreatedAtDesc(wallet, at);

        // Calcula o saldo somando todas as movimentações (positivas e negativas)
        BigDecimal historicalBalance = entries.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Retorna resposta com saldo histórico (timestamps não são relevantes para consulta histórica)
        return new WalletResponse(wallet.getId(), wallet.getUserId(), historicalBalance, null, at);
    }
}
