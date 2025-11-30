package com.pixservice.application.service;

import com.pixservice.application.dto.CreateWalletRequest;
import com.pixservice.application.dto.WalletResponse;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.WalletRepository;
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

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private com.pixservice.domain.repository.LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private WalletService walletService;

    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testWallet = new Wallet("testUser", new BigDecimal("100.00"));
        testWallet.setId(1L);
        testWallet.setCreatedAt(LocalDateTime.now());
        testWallet.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void shouldCreateWalletSuccessfully() {
        CreateWalletRequest request = new CreateWalletRequest("newUser", new BigDecimal("200.00"));
        when(walletRepository.findByUserId(request.getUserId())).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);

        WalletResponse response = walletService.createWallet(request);

        assertNotNull(response);
        assertEquals(testWallet.getId(), response.getId());
        assertEquals(testWallet.getUserId(), response.getUserId());
        assertEquals(testWallet.getBalance(), response.getBalance());
        verify(walletRepository, times(1)).findByUserId(request.getUserId());
        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    @Test
    void shouldThrowExceptionWhenCreateWalletWithExistingUser() {
        CreateWalletRequest request = new CreateWalletRequest("testUser", new BigDecimal("200.00"));
        when(walletRepository.findByUserId(request.getUserId())).thenReturn(Optional.of(testWallet));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            walletService.createWallet(request);
        });

        assertEquals("Carteira para este usuário já existe.", exception.getMessage());
        verify(walletRepository, times(1)).findByUserId(request.getUserId());
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void shouldGetWalletByIdSuccessfully() {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(testWallet));

        WalletResponse response = walletService.getWalletById(1L);

        assertNotNull(response);
        assertEquals(testWallet.getId(), response.getId());
        verify(walletRepository, times(1)).findById(1L);
    }

    @Test
    void shouldThrowExceptionWhenGetWalletByIdNotFound() {
        when(walletRepository.findById(2L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            walletService.getWalletById(2L);
        });

        assertEquals("Carteira não encontrada.", exception.getMessage());
        verify(walletRepository, times(1)).findById(2L);
    }

    @Test
    void shouldDepositSuccessfully() {
        BigDecimal depositAmount = new BigDecimal("50.00");
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);

        WalletResponse response = walletService.deposit(1L, depositAmount);

        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), response.getBalance());
        verify(walletRepository, times(1)).findByIdForUpdate(1L);
        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    @Test
    void shouldThrowExceptionWhenDepositInvalidAmount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            walletService.deposit(1L, BigDecimal.ZERO);
        });
        assertEquals("O valor do depósito deve ser positivo.", exception.getMessage());
    }

    @Test
    void shouldWithdrawSuccessfully() {
        BigDecimal withdrawAmount = new BigDecimal("30.00");
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);

        WalletResponse response = walletService.withdraw(1L, withdrawAmount);

        assertNotNull(response);
        assertEquals(new BigDecimal("70.00"), response.getBalance());
        verify(walletRepository, times(1)).findByIdForUpdate(1L);
        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    @Test
    void shouldThrowExceptionWhenWithdrawInvalidAmount() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            walletService.withdraw(1L, BigDecimal.ZERO);
        });
        assertEquals("O valor do saque deve ser positivo.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenWithdrawInsufficientBalance() {
        BigDecimal withdrawAmount = new BigDecimal("200.00");
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testWallet));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            walletService.withdraw(1L, withdrawAmount);
        });

        assertEquals("Saldo insuficiente.", exception.getMessage());
        verify(walletRepository, times(1)).findByIdForUpdate(1L);
        verify(walletRepository, never()).save(any(Wallet.class));
    }
}
