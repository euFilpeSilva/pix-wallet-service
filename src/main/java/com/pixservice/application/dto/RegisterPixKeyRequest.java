package com.pixservice.application.dto;

import com.pixservice.domain.model.PixKeyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPixKeyRequest {
    private String keyValue;
    private PixKeyType type;
}
