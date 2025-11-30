package com.pixservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"key_value"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_value", nullable = false)
    private String keyValue;

    private String responseBody;

    private int httpStatus;

    private LocalDateTime createdAt;

    public IdempotencyKey(String keyValue, String responseBody, int httpStatus) {
        this.keyValue = keyValue;
        this.responseBody = responseBody;
        this.httpStatus = httpStatus;
        this.createdAt = LocalDateTime.now();
    }
}
