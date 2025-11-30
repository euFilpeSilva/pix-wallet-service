package com.pixservice.domain.repository;

import com.pixservice.domain.model.PixTransaction;
import com.pixservice.domain.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PixTransactionRepository extends JpaRepository<PixTransaction, String> {
    Optional<PixTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Busca transferência recente idêntica para detectar duplicatas.
     * Retorna a mais recente se houver alguma no período especificado.
     */
    @Query(value = "SELECT pt FROM PixTransaction pt " +
           "WHERE pt.fromWallet = :fromWallet " +
           "AND pt.toPixKey = :toPixKey " +
           "AND pt.amount = :amount " +
           "AND pt.initiatedAt > :after " +
           "ORDER BY pt.initiatedAt DESC")
    List<PixTransaction> findRecentDuplicateTransferList(
        @Param("fromWallet") Wallet fromWallet,
        @Param("toPixKey") String toPixKey,
        @Param("amount") BigDecimal amount,
        @Param("after") LocalDateTime after
    );

    default Optional<PixTransaction> findRecentDuplicateTransfer(
        Wallet fromWallet,
        String toPixKey,
        BigDecimal amount,
        LocalDateTime after
    ) {
        List<PixTransaction> results = findRecentDuplicateTransferList(fromWallet, toPixKey, amount, after);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
