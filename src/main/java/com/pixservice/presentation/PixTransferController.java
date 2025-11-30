package com.pixservice.presentation;

import com.pixservice.application.dto.PixTransferRequest;
import com.pixservice.application.dto.PixTransferResponse;
import com.pixservice.application.service.PixTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pix/transfers")
@RequiredArgsConstructor
public class PixTransferController {

    private final PixTransferService pixTransferService;

    @PostMapping
    public ResponseEntity<PixTransferResponse> initiatePixTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PixTransferRequest request) {
        PixTransferResponse response = pixTransferService.transfer(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
