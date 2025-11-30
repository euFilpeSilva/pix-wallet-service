package com.pixservice.domain.repository;

import com.pixservice.domain.model.PixEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PixEventRepository extends JpaRepository<PixEvent, Long> {
    boolean existsByEventId(String eventId);

    boolean existsByEventIdAndEndToEndId(String eventId, String endToEndId);
}
