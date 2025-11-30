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
public class PixTransaction {

    @Id
    private String endToEndId; // Usado como Idempotency-Key para a transação Pix

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id")
    private Wallet toWallet;

    private String toPixKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PixKeyType toPixKeyType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PixTransactionStatus status;

    private String idempotencyKey; // Idempotency-Key da requisição inicial

    private LocalDateTime initiatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime lastUpdateAt;

    @Version
    private Long version; // Para controle de concorrência otimista

    public PixTransaction(String endToEndId, Wallet fromWallet, String toPixKey, PixKeyType toPixKeyType, BigDecimal amount, String idempotencyKey) {
        this.endToEndId = endToEndId;
        this.fromWallet = fromWallet;
        this.toPixKey = toPixKey;
        this.toPixKeyType = toPixKeyType;
        this.amount = amount;
        this.status = PixTransactionStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.initiatedAt = LocalDateTime.now();
        this.lastUpdateAt = LocalDateTime.now();
        this.version = 0L;
    }

    public void confirm() {
        if (this.status != PixTransactionStatus.PENDING) {
            throw new IllegalStateException("Transação não pode ser confirmada, status atual: " + this.status);
        }
        this.status = PixTransactionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.lastUpdateAt = LocalDateTime.now();
    }

    public void reject() {
        if (this.status != PixTransactionStatus.PENDING) {
            throw new IllegalStateException("Transação não pode ser rejeitada, status atual: " + this.status);
        }
        this.status = PixTransactionStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
        this.lastUpdateAt = LocalDateTime.now();
    }
}
