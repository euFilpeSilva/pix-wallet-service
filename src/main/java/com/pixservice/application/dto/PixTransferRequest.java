package com.pixservice.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixTransferRequest {
    private Long fromWalletId;
    private String toPixKey;
    private BigDecimal amount;
}
