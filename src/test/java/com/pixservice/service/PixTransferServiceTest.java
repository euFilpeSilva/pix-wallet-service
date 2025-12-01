package com.pixservice.service;

import com.pixservice.application.dto.PixTransferRequest;
import com.pixservice.application.dto.PixTransferResponse;
import com.pixservice.application.service.PixTransferService;
import com.pixservice.domain.model.*;
import com.pixservice.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PixTransferServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private PixKeyRepository pixKeyRepository;
    @Mock
    private PixTransactionRepository pixTransactionRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private com.pixservice.application.idempotency.IdempotencyService idempotencyService;
    @Mock
    private com.pixservice.application.validation.PixTransferValidator validator;
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock
    private PixEventRepository pixEventRepository;

    private PixTransferService pixTransferService;

    private Wallet fromWallet;
    private Wallet toWallet;
    private PixKey toPixKey;

    @BeforeEach
    void setUp() {
        // Inicializar PixTransferService manualmente com os mocks
        pixTransferService = new PixTransferService(
                walletRepository,
                pixKeyRepository,
                pixTransactionRepository,
                ledgerEntryRepository,
                idempotencyService,
                validator,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                pixEventRepository
        );

        fromWallet = new Wallet("user1", new BigDecimal("1000.00"));
        fromWallet.setId(1L);

        toWallet = new Wallet("user2", new BigDecimal("500.00"));
        toWallet.setId(2L);

        toPixKey = new PixKey("recipient@email.com", PixKeyType.EMAIL, toWallet);
        toPixKey.setId(10L);
    }

    @Test
    void shouldTransferSuccessfully() {
        String idempotencyKey = "transfer-123";
        PixTransferRequest request = new PixTransferRequest(1L, "recipient@email.com", new BigDecimal("100.00"));

        when(idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class)).thenReturn(Optional.empty());
        when(walletRepository.findById(1L)).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fromWallet));
        when(pixKeyRepository.findByKeyValue("recipient@email.com")).thenReturn(Optional.of(toPixKey));
        when(walletRepository.save(any(Wallet.class))).thenReturn(fromWallet);
        when(pixTransactionRepository.save(any(PixTransaction.class))).thenAnswer(invocation -> {
            PixTransaction transaction = invocation.getArgument(0);
            transaction.setEndToEndId(UUID.randomUUID().toString());
            return transaction;
        });

        PixTransferResponse response = pixTransferService.transfer(idempotencyKey, request);

        assertNotNull(response);
        assertNotNull(response.getEndToEndId());
        assertEquals(PixTransactionStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("900.00"), fromWallet.getBalance());

        verify(idempotencyService, times(1)).getIdempotentResponse(idempotencyKey, PixTransferResponse.class);
        verify(walletRepository, times(1)).findById(1L);
        verify(walletRepository, times(1)).findByIdForUpdate(1L);
        verify(pixKeyRepository, times(1)).findByKeyValue("recipient@email.com");
        verify(walletRepository, times(1)).save(fromWallet);
        verify(pixTransactionRepository, times(1)).save(any(PixTransaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(idempotencyService, times(1)).saveIdempotentResponse(eq(idempotencyKey), any(PixTransferResponse.class), any());
    }

    @Test
    void shouldReturnCachedResponseForIdempotentRequest() {
        String idempotencyKey = "transfer-123";
        PixTransferRequest request = new PixTransferRequest(1L, "recipient@email.com", new BigDecimal("100.00"));
        PixTransferResponse cachedResponse = new PixTransferResponse("cached-end-to-end-id", PixTransactionStatus.PENDING);

        var idempotentResponse = new com.pixservice.application.idempotency.IdempotentResponse<>(
            cachedResponse
        );

        when(idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class))
            .thenReturn(Optional.of(idempotentResponse));

        PixTransferResponse response = pixTransferService.transfer(idempotencyKey, request);

        assertNotNull(response);
        assertEquals(cachedResponse.getEndToEndId(), response.getEndToEndId());
        assertEquals(cachedResponse.getStatus(), response.getStatus());

        verify(idempotencyService, times(1)).getIdempotentResponse(idempotencyKey, PixTransferResponse.class);
        verify(walletRepository, never()).findById(anyLong());
        verify(pixKeyRepository, never()).findByKeyValue(anyString());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(pixTransactionRepository, never()).save(any(PixTransaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void shouldThrowExceptionWhenTransferAmountIsInvalid() {
        String idempotencyKey = "transfer-123";
        PixTransferRequest request = new PixTransferRequest(1L, "recipient@email.com", BigDecimal.ZERO);
        when(idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class)).thenReturn(Optional.empty());
        when(walletRepository.findById(1L)).thenReturn(Optional.of(fromWallet));
        when(pixKeyRepository.findByKeyValue("recipient@email.com")).thenReturn(Optional.of(toPixKey));

        // Validator agora lança a exceção
        doThrow(new IllegalArgumentException("O valor da transferência deve ser positivo."))
            .when(validator).validateTransfer(eq(BigDecimal.ZERO), any(), any(), anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixTransferService.transfer(idempotencyKey, request);
        });

        assertEquals("O valor da transferência deve ser positivo.", exception.getMessage());
        verify(idempotencyService, times(1)).getIdempotentResponse(idempotencyKey, PixTransferResponse.class);
    }

    @Test
    void shouldThrowExceptionWhenFromWalletNotFound() {
        String idempotencyKey = "transfer-123";
        PixTransferRequest request = new PixTransferRequest(99L, "recipient@email.com", new BigDecimal("100.00"));
        when(idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class)).thenReturn(Optional.empty());
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixTransferService.transfer(idempotencyKey, request);
        });

        assertEquals("Carteira de origem não encontrada.", exception.getMessage());
        verify(idempotencyService, times(1)).getIdempotentResponse(idempotencyKey, PixTransferResponse.class);
        verify(walletRepository, times(1)).findById(99L);
        verify(pixKeyRepository, never()).findByKeyValue(anyString());
    }

    @Test
    void shouldThrowExceptionWhenToPixKeyNotFound() {
        String idempotencyKey = "transfer-123";
        PixTransferRequest request = new PixTransferRequest(1L, "nonexistent@email.com", new BigDecimal("100.00"));
        when(idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class)).thenReturn(Optional.empty());
        when(walletRepository.findById(1L)).thenReturn(Optional.of(fromWallet));
        when(pixKeyRepository.findByKeyValue("nonexistent@email.com")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixTransferService.transfer(idempotencyKey, request);
        });

        assertEquals("Chave Pix de destino não encontrada.", exception.getMessage());
        verify(idempotencyService, times(1)).getIdempotentResponse(idempotencyKey, PixTransferResponse.class);
        verify(walletRepository, times(1)).findById(1L);
        verify(pixKeyRepository, times(1)).findByKeyValue("nonexistent@email.com");
    }

    @Test
    void shouldThrowExceptionWhenTransferToSameWallet() {
        String idempotencyKey = "transfer-123";
        PixTransferRequest request = new PixTransferRequest(1L, toPixKey.getKeyValue(), new BigDecimal("100.00"));
        toWallet = fromWallet; // Make toWallet the same as fromWallet
        toPixKey.setWallet(fromWallet);

        when(idempotencyService.getIdempotentResponse(idempotencyKey, PixTransferResponse.class)).thenReturn(Optional.empty());
        when(walletRepository.findById(1L)).thenReturn(Optional.of(fromWallet));
        when(pixKeyRepository.findByKeyValue(toPixKey.getKeyValue())).thenReturn(Optional.of(toPixKey));

        // Validator agora lança a exceção
        doThrow(new IllegalArgumentException("Não é possível transferir para a mesma carteira."))
            .when(validator).validateTransfer(any(BigDecimal.class), eq(fromWallet), eq(fromWallet), anyString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixTransferService.transfer(idempotencyKey, request);
        });

        assertEquals("Não é possível transferir para a mesma carteira.", exception.getMessage());
        verify(idempotencyService, times(1)).getIdempotentResponse(idempotencyKey, PixTransferResponse.class);
        verify(walletRepository, times(1)).findById(1L);
        verify(pixKeyRepository, times(1)).findByKeyValue(toPixKey.getKeyValue());
        verify(walletRepository, never()).save(any(Wallet.class));
    }
}
