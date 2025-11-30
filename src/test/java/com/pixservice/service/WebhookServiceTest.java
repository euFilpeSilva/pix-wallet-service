package com.pixservice.application.service;

import com.pixservice.application.dto.PixWebhookRequest;
import com.pixservice.application.dto.PixWebhookResponse;
import com.pixservice.domain.model.*;
import com.pixservice.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private PixEventRepository pixEventRepository;
    @Mock
    private PixTransactionRepository pixTransactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private WebhookService webhookService;

    private Wallet fromWallet;
    private Wallet toWallet;
    private PixTransaction pendingPixTransaction;

    @BeforeEach
    void setUp() {
        // Inicializar WebhookService manualmente
        webhookService = new WebhookService(
                pixEventRepository,
                pixTransactionRepository,
                walletRepository,
                ledgerEntryRepository,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );

        fromWallet = new Wallet("user1", new BigDecimal("1000.00"));
        fromWallet.setId(1L);

        toWallet = new Wallet("user2", new BigDecimal("500.00"));
        toWallet.setId(2L);

        pendingPixTransaction = new PixTransaction("e2e123", fromWallet, "recipient@email.com", PixKeyType.EMAIL, new BigDecimal("100.00"), "idem123");
        pendingPixTransaction.setToWallet(toWallet);
        pendingPixTransaction.setStatus(PixTransactionStatus.PENDING);
        pendingPixTransaction.setInitiatedAt(LocalDateTime.now());
        pendingPixTransaction.setLastUpdateAt(LocalDateTime.now());
    }

    @Test
    void shouldProcessConfirmedEventSuccessfully() {
        PixWebhookRequest request = new PixWebhookRequest("e2e123", "event1", PixEventType.CONFIRMED, LocalDateTime.now());

        doAnswer(invocation -> invocation.getArgument(0)).when(pixEventRepository).save(any(PixEvent.class));
        doReturn(Optional.of(pendingPixTransaction)).when(pixTransactionRepository).findById("e2e123");
        doReturn(Optional.of(toWallet)).when(walletRepository).findByIdForUpdate(2L);
        doReturn(toWallet).when(walletRepository).save(any(Wallet.class));
        doReturn(pendingPixTransaction).when(pixTransactionRepository).save(any(PixTransaction.class));

        PixWebhookResponse response = webhookService.processWebhookEvent(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Transação Pix confirmada e saldo creditado.", response.getMessage());
        assertEquals(PixTransactionStatus.CONFIRMED, pendingPixTransaction.getStatus());
        assertEquals(new BigDecimal("600.00"), toWallet.getBalance());

        verify(pixEventRepository, times(1)).save(any(PixEvent.class));
        verify(pixTransactionRepository, times(1)).findById("e2e123");
        verify(walletRepository, times(1)).findByIdForUpdate(2L);
        verify(walletRepository, times(1)).save(toWallet);
        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
        verify(pixTransactionRepository, times(1)).save(pendingPixTransaction);
    }

    @Test
    void shouldProcessRejectedEventSuccessfully() {
        PixWebhookRequest request = new PixWebhookRequest("e2e123", "event1", PixEventType.REJECTED, LocalDateTime.now());

        doAnswer(invocation -> invocation.getArgument(0)).when(pixEventRepository).save(any(PixEvent.class));
        doReturn(Optional.of(pendingPixTransaction)).when(pixTransactionRepository).findById("e2e123");
        doReturn(Optional.of(fromWallet)).when(walletRepository).findByIdForUpdate(1L);
        doReturn(fromWallet).when(walletRepository).save(any(Wallet.class));
        doReturn(pendingPixTransaction).when(pixTransactionRepository).save(any(PixTransaction.class));

        PixWebhookResponse response = webhookService.processWebhookEvent(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Transação Pix rejeitada e débito estornado.", response.getMessage());
        assertEquals(PixTransactionStatus.REJECTED, pendingPixTransaction.getStatus());
        assertEquals(new BigDecimal("1100.00"), fromWallet.getBalance());

        verify(pixEventRepository, times(1)).save(any(PixEvent.class));
        verify(pixTransactionRepository, times(1)).findById("e2e123");
        verify(walletRepository, times(1)).findByIdForUpdate(1L);
        verify(walletRepository, times(1)).save(fromWallet);
        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
        verify(pixTransactionRepository, times(1)).save(pendingPixTransaction);
    }

    @Test
    void shouldReturnSuccessWhenEventAlreadyProcessed() {
        PixWebhookRequest request = new PixWebhookRequest("e2e123", "event1", PixEventType.CONFIRMED, LocalDateTime.now());

        doThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate event"))
                .when(pixEventRepository).save(any(PixEvent.class));

        PixWebhookResponse response = webhookService.processWebhookEvent(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Evento já processado.", response.getMessage());

        verify(pixEventRepository, times(1)).save(any(PixEvent.class));
        verify(pixTransactionRepository, never()).findById(anyString());
        verify(walletRepository, never()).findById(anyLong());
        verify(pixTransactionRepository, never()).save(any(PixTransaction.class));
    }

    @Test
    void shouldThrowExceptionWhenPixTransactionNotFound() {
        PixWebhookRequest request = new PixWebhookRequest("nonexistent-e2e", "event1", PixEventType.CONFIRMED, LocalDateTime.now());

        doAnswer(invocation -> invocation.getArgument(0)).when(pixEventRepository).save(any(PixEvent.class));
        doReturn(Optional.empty()).when(pixTransactionRepository).findById("nonexistent-e2e");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            webhookService.processWebhookEvent(request);
        });

        assertEquals("Transação Pix não encontrada para o endToEndId: nonexistent-e2e", exception.getMessage());
        verify(pixEventRepository, times(1)).save(any(PixEvent.class));
        verify(pixTransactionRepository, times(1)).findById("nonexistent-e2e");
        verify(walletRepository, never()).findById(anyLong());
    }

    @Test
    void shouldHandleRejectedEventAfterConfirmedSuccessfully() {
        PixWebhookRequest confirmedRequest = new PixWebhookRequest("e2e123", "event1", PixEventType.CONFIRMED, LocalDateTime.now());
        PixWebhookRequest rejectedRequest = new PixWebhookRequest("e2e123", "event2", PixEventType.REJECTED, LocalDateTime.now().plusSeconds(10));

        // Simulate confirmation first (removido stubbing existsByEventId desnecessário)
        doAnswer(invocation -> invocation.getArgument(0)).when(pixEventRepository).save(any(PixEvent.class));
        doReturn(Optional.of(pendingPixTransaction)).when(pixTransactionRepository).findById("e2e123");
        doReturn(Optional.of(toWallet)).when(walletRepository).findByIdForUpdate(2L);
        doReturn(toWallet).when(walletRepository).save(any(Wallet.class));
        doReturn(pendingPixTransaction).when(pixTransactionRepository).save(any(PixTransaction.class));

        webhookService.processWebhookEvent(confirmedRequest);
        assertEquals(PixTransactionStatus.CONFIRMED, pendingPixTransaction.getStatus());

        // Now process the rejected event for the same transaction
        when(pixTransactionRepository.findById("e2e123")).thenReturn(Optional.of(pendingPixTransaction));
        PixWebhookResponse response = webhookService.processWebhookEvent(rejectedRequest);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("Transação já em estado final ou processada. Evento registrado.", response.getMessage());
        assertEquals(PixTransactionStatus.CONFIRMED, pendingPixTransaction.getStatus());

        verify(pixEventRepository, times(2)).save(any(PixEvent.class));
        verify(pixTransactionRepository, times(2)).findById("e2e123");
        verify(walletRepository, never()).findById(1L);
        verify(walletRepository, never()).save(fromWallet);
        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
        verify(pixTransactionRepository, times(1)).save(pendingPixTransaction);
    }
}
