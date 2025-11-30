package com.pixservice.presentation;

import com.pixservice.application.dto.PixWebhookRequest;
import com.pixservice.application.dto.PixWebhookResponse;
import com.pixservice.application.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pix/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<PixWebhookResponse> processPixWebhook(@RequestBody PixWebhookRequest request) {
        PixWebhookResponse response = webhookService.processWebhookEvent(request);
        if ("ERROR".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } else {
            return ResponseEntity.ok(response);
        }
    }
}
