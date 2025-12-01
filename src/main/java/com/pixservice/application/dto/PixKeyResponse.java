package com.pixservice.application.dto;

import com.pixservice.domain.model.PixKeyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixKeyResponse {
    private Long id;
    private String keyValue;
    private PixKeyType type;
    private Long walletId;
    private String userId;
    private LocalDateTime createdAt;
}
