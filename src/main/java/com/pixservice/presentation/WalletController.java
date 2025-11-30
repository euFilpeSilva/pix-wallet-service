package com.pixservice.presentation;

import com.pixservice.application.dto.CreateWalletRequest;
import com.pixservice.application.dto.WalletResponse;
import com.pixservice.application.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@RequestBody CreateWalletRequest request) {
        WalletResponse wallet = walletService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(wallet);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWalletById(@PathVariable Long id) {
        WalletResponse wallet = walletService.getWalletById(id);
        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<WalletResponse> getWalletBalance(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDateTime at) {
        if (at != null) {
            return ResponseEntity.ok(walletService.getHistoricalBalance(id, at));
        } else {
            return ResponseEntity.ok(walletService.getWalletById(id));
        }
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<WalletResponse> deposit(@PathVariable Long id, @RequestParam BigDecimal amount) {
        WalletResponse wallet = walletService.deposit(id, amount);
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<WalletResponse> withdraw(@PathVariable Long id, @RequestParam BigDecimal amount) {
        WalletResponse wallet = walletService.withdraw(id, amount);
        return ResponseEntity.ok(wallet);
    }
}
