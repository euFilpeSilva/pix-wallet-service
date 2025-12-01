package com.pixservice.application.dto;

import com.pixservice.domain.model.PixTransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PixTransferResponse {
    private String endToEndId;
    private PixTransactionStatus status;
}
