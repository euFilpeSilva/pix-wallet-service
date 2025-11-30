package com.pixservice.domain.repository;

import com.pixservice.domain.model.LedgerEntry;
import com.pixservice.domain.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByWalletAndCreatedAtBeforeOrderByCreatedAtDesc(Wallet wallet, LocalDateTime createdAt);
    List<LedgerEntry> findByWalletOrderByCreatedAtDesc(Wallet wallet);
}
