package com.pixservice.application.dto;

import com.pixservice.domain.model.PixEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixWebhookRequest {
    private String endToEndId;
    private String eventId;
    private PixEventType eventType;
    private LocalDateTime occurredAt;
}
