package com.pixservice.presentation;

import com.pixservice.application.dto.PixKeyResponse;
import com.pixservice.application.dto.RegisterPixKeyRequest;
import com.pixservice.application.service.PixKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallets/{walletId}/pix-keys")
@RequiredArgsConstructor
public class PixKeyController {

    private final PixKeyService pixKeyService;

    @PostMapping
    public ResponseEntity<PixKeyResponse> registerPixKey(@PathVariable Long walletId, @RequestBody RegisterPixKeyRequest request) {
        PixKeyResponse pixKey = pixKeyService.registerPixKey(request, walletId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pixKey);
    }

    @GetMapping("/{keyValue}")
    public ResponseEntity<PixKeyResponse> getPixKeyByValue(@PathVariable String keyValue) {
        PixKeyResponse pixKey = pixKeyService.getPixKeyByValue(keyValue);
        return ResponseEntity.ok(pixKey);
    }
}
