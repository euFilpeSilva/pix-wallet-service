package com.pixservice.domain.repository;

import com.pixservice.domain.model.PixKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PixKeyRepository extends JpaRepository<PixKey, Long> {
    Optional<PixKey> findByKeyValue(String keyValue);
    boolean existsByKeyValue(String keyValue);
}
