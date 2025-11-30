package com.pixservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pix_event", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "end_to_end_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId; // Idempotency-Key para o webhook

    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PixEventType eventType;

    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;

    public PixEvent(String eventId, String endToEndId, PixEventType eventType, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.endToEndId = endToEndId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.receivedAt = LocalDateTime.now();
    }
}
