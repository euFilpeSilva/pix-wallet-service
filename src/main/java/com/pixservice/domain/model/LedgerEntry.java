package com.pixservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal balanceBefore;

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    private String transactionId; // Pode ser o endToEndId de uma transação Pix
    private String description;
    private LocalDateTime createdAt;

    public static LedgerEntry openingBalance(Wallet wallet, BigDecimal amount) {
        LedgerEntry e = new LedgerEntry();
        e.setWallet(wallet);
        e.setType(LedgerEntryType.DEPOSIT);
        e.setAmount(amount);
        e.setBalanceBefore(BigDecimal.ZERO);
        e.setBalanceAfter(amount);
        e.setDescription("Opening balance");
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    public static LedgerEntry deposit(Wallet wallet, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String description) {
        LedgerEntry e = new LedgerEntry();
        e.setWallet(wallet);
        e.setType(LedgerEntryType.DEPOSIT);
        e.setAmount(amount);
        e.setBalanceBefore(balanceBefore);
        e.setBalanceAfter(balanceAfter);
        e.setDescription(description);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    public static LedgerEntry withdraw(Wallet wallet, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String description) {
        LedgerEntry e = new LedgerEntry();
        e.setWallet(wallet);
        e.setType(LedgerEntryType.WITHDRAWAL);
        e.setAmount(amount.negate()); // registrar débito como valor negativo
        e.setBalanceBefore(balanceBefore);
        e.setBalanceAfter(balanceAfter);
        e.setDescription(description);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
