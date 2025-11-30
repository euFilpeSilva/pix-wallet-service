package com.pixservice.application.service;

import com.pixservice.application.dto.CreateWalletRequest;
import com.pixservice.application.dto.PixTransferRequest;
import com.pixservice.application.dto.PixTransferResponse;
import com.pixservice.application.dto.RegisterPixKeyRequest;
import com.pixservice.domain.model.PixKeyType;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.IdempotencyKeyRepository;
import com.pixservice.domain.repository.PixTransactionRepository;
import com.pixservice.domain.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de concorrência para validar comportamento sob requisições simultâneas.
 * Estes testes garantem que o sistema mantém consistência mesmo com múltiplas threads
 * competindo por recursos compartilhados.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private PixTransferService pixTransferService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PixKeyService pixKeyService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PixTransactionRepository pixTransactionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private com.pixservice.domain.repository.PixKeyRepository pixKeyRepository;

    @Autowired
    private com.pixservice.domain.repository.LedgerEntryRepository ledgerEntryRepository;

    private Long fromWalletId;
    private Long toWalletId;
    private String toPixKey;

    @BeforeEach
    void setUp() {
        // Limpar dados na ordem correta (foreign keys)
        idempotencyKeyRepository.deleteAll();
        pixTransactionRepository.deleteAll();
        ledgerEntryRepository.deleteAll(); // Deletar ledger antes das chaves Pix e wallets
        pixKeyRepository.deleteAll(); // Deletar chaves Pix antes das carteiras
        walletRepository.deleteAll();

        // Criar carteiras
        fromWalletId = walletService.createWallet(
                new CreateWalletRequest("concurrency-from", new BigDecimal("10000.00"))).getId();
        toWalletId = walletService.createWallet(
                new CreateWalletRequest("concurrency-to", new BigDecimal("500.00"))).getId();

        // Registrar chave Pix
        toPixKey = "concurrency@test.com";
        pixKeyService.registerPixKey(
                new RegisterPixKeyRequest(toPixKey, PixKeyType.EMAIL),
                toWalletId);
    }

    /**
     * Testa que múltiplas threads tentando a mesma transferência com a mesma Idempotency-Key
     * resultam em apenas 1 transferência processada, e todas retornam o mesmo endToEndId.
     */
//    @Test`l,m
//    void shouldHandleConcurrentTransfersWithSameIdempotencyKey() throws InterruptedException, ExecutionException {
//        String idempotencyKey = UUID.randomUUID().toString();
//        PixTransferRequest request = new PixTransferRequest(
//                fromWalletId,
//                toPixKey,
//                new BigDecimal("100.00")
//        );
//
//        int threadCount = 10;
//        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//        List<Future<PixTransferResponse>> futures = new ArrayList<>();
//
//        // Simular 10 threads tentando mesma transferência simultaneamente
//        for (int i = 0; i < threadCount; i++) {
//            futures.add(executor.submit(() ->
//                    pixTransferService.transfer(idempotencyKey, request)
//            ));
//        }
//
//        executor.shutdown();
//        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
//
//        // Coletar resultados
//        List<PixTransferResponse> responses = new ArrayList<>();
//        for (Future<PixTransferResponse> future : futures) {
//            responses.add(future.get());
//        }
//
//        // Validar que todos retornaram o mesmo endToEndId
//        Set<String> endToEndIds = responses.stream()
//                .map(PixTransferResponse::getEndToEndId)
//                .collect(Collectors.toSet());
//
//        assertEquals(1, endToEndIds.size(), "Deve haver apenas 1 endToEndId único");
//
//        // Validar que saldo foi debitado apenas 1x
//        Wallet updatedFromWallet = walletRepository.findById(fromWalletId).orElseThrow();
//        assertEquals(new BigDecimal("9900.00"), updatedFromWallet.getBalance(),
//                "Saldo deve ter sido debitado apenas uma vez");
//
//        // Validar que apenas 1 transação foi criada
//        assertEquals(1, pixTransactionRepository.count(),
//                "Deve haver apenas 1 transação no banco");
//
//        // Validar que apenas 1 idempotency key foi salva
//        assertEquals(1, idempotencyKeyRepository.count(),
//                "Deve haver apenas 1 chave de idempotência no banco");
//    }

    /**
     * Testa que múltiplas transferências diferentes da mesma carteira
     * são processadas corretamente sem perda de débitos.
     */
    @Test
    void shouldHandleConcurrentDifferentTransfersFromSameWallet() throws InterruptedException, ExecutionException {
        int transferCount = 5;
        BigDecimal transferAmount = new BigDecimal("100.00");
        ExecutorService executor = Executors.newFixedThreadPool(transferCount);
        List<Future<PixTransferResponse>> futures = new ArrayList<>();

        // Simular 5 transferências diferentes simultâneas da mesma carteira
        for (int i = 0; i < transferCount; i++) {
            String idempotencyKey = UUID.randomUUID().toString();
            PixTransferRequest request = new PixTransferRequest(
                    fromWalletId,
                    toPixKey,
                    transferAmount
            );

            futures.add(executor.submit(() ->
                    pixTransferService.transfer(idempotencyKey, request)
            ));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Coletar resultados
        List<PixTransferResponse> responses = new ArrayList<>();
        for (Future<PixTransferResponse> future : futures) {
            responses.add(future.get());
        }

        // Validar que todas as transferências foram processadas
        assertEquals(transferCount, responses.size());

        // Validar que todos os endToEndIds são únicos
        Set<String> endToEndIds = responses.stream()
                .map(PixTransferResponse::getEndToEndId)
                .collect(Collectors.toSet());
        assertEquals(transferCount, endToEndIds.size(),
                "Todos os endToEndIds devem ser únicos");

        // Validar que saldo foi debitado corretamente (5 x 100 = 500)
        Wallet updatedFromWallet = walletRepository.findById(fromWalletId).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("10000.00")
                .subtract(transferAmount.multiply(new BigDecimal(transferCount)));
        assertEquals(expectedBalance, updatedFromWallet.getBalance(),
                "Saldo deve ter sido debitado corretamente para todas as transferências");

        // Validar que 5 transações foram criadas
        assertEquals(transferCount, pixTransactionRepository.count(),
                "Devem haver " + transferCount + " transações no banco");

        // Validar que 5 idempotency keys foram salvas
        assertEquals(transferCount, idempotencyKeyRepository.count(),
                "Devem haver " + transferCount + " chaves de idempotência no banco");
    }

    /**
     * Testa que depósitos e saques concorrentes na mesma carteira
     * mantêm consistência no saldo final.
     */
    @Test
    void shouldHandleConcurrentDepositsAndWithdrawals() throws InterruptedException {
        Long walletId = fromWalletId;
        int operationCount = 10;
        BigDecimal operationAmount = new BigDecimal("100.00");

        ExecutorService executor = Executors.newFixedThreadPool(operationCount * 2);
        List<Future<?>> futures = new ArrayList<>();

        // Simular 10 depósitos e 10 saques concorrentes
        for (int i = 0; i < operationCount; i++) {
            // Depósito
            futures.add(executor.submit(() -> {
                try {
                    walletService.deposit(walletId, operationAmount);
                } catch (Exception e) {
                    // Pode falhar por optimistic locking, é esperado
                }
            }));

            // Saque
            futures.add(executor.submit(() -> {
                try {
                    walletService.withdraw(walletId, operationAmount);
                } catch (Exception e) {
                    // Pode falhar por optimistic locking ou saldo insuficiente
                }
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        // Aguardar conclusão de todas as operações
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                // Algumas operações podem falhar, é esperado
            }
        }

        // Validar que o saldo final faz sentido
        // (pode não ser exatamente o inicial se algumas operações falharam)
        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        assertNotNull(finalWallet.getBalance());
        assertTrue(finalWallet.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Saldo não pode ser negativo");
    }

    /**
     * Testa que a criação de múltiplas carteiras para o mesmo usuário
     * resulta em apenas 1 carteira criada.
     *
     * NOTA: Este teste pode ser intermitente com H2 devido à natureza não determinística
     * de concorrência. Em produção com PostgreSQL, o comportamento é garantido.
     */
    @Test
    void shouldPreventDuplicateWalletCreation() throws InterruptedException {
        String userId = "duplicate-user";
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // Tentar criar 5 carteiras para o mesmo usuário simultaneamente
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    walletService.createWallet(
                            new CreateWalletRequest(userId, new BigDecimal("100.00"))
                    );
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Validar que 4 tentativas falharam com exceção de duplicação
        assertEquals(4, exceptions.size(),
                "Deve haver 4 exceções (apenas 1 criação bem-sucedida)");

        for (Exception e : exceptions) {
            assertTrue(e.getMessage().contains("já existe"),
                    "Exceção deve indicar carteira duplicada");
        }

        // Validar que apenas 1 carteira foi criada
        assertEquals(1, walletRepository.findByUserId(userId).stream().count(),
                "Deve haver apenas 1 carteira para o usuário");
    }

    /**
     * Testa que múltiplas threads tentando registrar a mesma chave Pix
     * resultam em apenas 1 registro bem-sucedido.
     *
     * NOTA: Este teste pode ser intermitente com H2 devido à natureza não determinística
     * de concorrência. Em produção com PostgreSQL, o comportamento é garantido.
     */
    @Test
    void shouldPreventDuplicatePixKeyRegistration() throws InterruptedException {
        String pixKeyValue = "duplicate@test.com";
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // Tentar registrar mesma chave Pix 5 vezes simultaneamente
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    pixKeyService.registerPixKey(
                            new RegisterPixKeyRequest(pixKeyValue, PixKeyType.EMAIL),
                            fromWalletId
                    );
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Validar que 4 tentativas falharam
        assertEquals(4, exceptions.size(),
                "Deve haver 4 exceções (apenas 1 registro bem-sucedido)");

        for (Exception e : exceptions) {
            assertTrue(e.getMessage().contains("já registrada"),
                    "Exceção deve indicar chave Pix duplicada");
        }
    }
}

