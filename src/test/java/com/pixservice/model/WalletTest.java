package com.pixservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WalletTest {

    @Test
    void shouldCreateWalletWithInitialBalance() {
        Wallet wallet = new Wallet("user123", new BigDecimal("100.00"));
        assertNotNull(wallet);
        assertEquals("user123", wallet.getUserId());
        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
        assertNotNull(wallet.getCreatedAt());
        assertNotNull(wallet.getUpdatedAt());
        assertEquals(0L, wallet.getVersion());
    }

    @Test
    void shouldDepositAmount() {
        Wallet wallet = new Wallet("user123", new BigDecimal("100.00"));
        wallet.deposit(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("150.00"), wallet.getBalance());
    }

    @Test
    void shouldThrowExceptionWhenDepositNegativeAmount() {
        Wallet wallet = new Wallet("user123", new BigDecimal("100.00"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            wallet.deposit(new BigDecimal("-10.00"));
        });
        assertEquals("O valor do depósito deve ser positivo.", exception.getMessage());
    }

    @Test
    void shouldWithdrawAmount() {
        Wallet wallet = new Wallet("user123", new BigDecimal("100.00"));
        wallet.withdraw(new BigDecimal("30.00"));
        assertEquals(new BigDecimal("70.00"), wallet.getBalance());
    }

    @Test
    void shouldThrowExceptionWhenWithdrawNegativeAmount() {
        Wallet wallet = new Wallet("user123", new BigDecimal("100.00"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            wallet.withdraw(new BigDecimal("-10.00"));
        });
        assertEquals("O valor do saque deve ser positivo.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenWithdrawInsufficientBalance() {
        Wallet wallet = new Wallet("user123", new BigDecimal("50.00"));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            wallet.withdraw(new BigDecimal("100.00"));
        });
        assertEquals("Saldo insuficiente.", exception.getMessage());
    }
}
