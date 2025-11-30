package com.pixservice.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixservice.application.dto.CreateWalletRequest;
import com.pixservice.application.dto.PixTransferRequest;
import com.pixservice.application.dto.PixWebhookRequest;
import com.pixservice.domain.model.*;
import com.pixservice.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PixKeyRepository pixKeyRepository;

    @Autowired
    private PixTransactionRepository pixTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private Wallet fromWallet;
    private Wallet toWallet;
    private PixKey toPixKey;
    private String endToEndId;

    @BeforeEach
    void setUp() throws Exception {
        ledgerEntryRepository.deleteAll();
        pixTransactionRepository.deleteAll();
        pixKeyRepository.deleteAll();
        walletRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();

        fromWallet = new Wallet("user1", new BigDecimal("1000.00"));
        fromWallet = walletRepository.save(fromWallet);

        toWallet = new Wallet("user2", new BigDecimal("500.00"));
        toWallet = walletRepository.save(toWallet);

        toPixKey = new PixKey("recipient@email.com", PixKeyType.EMAIL, toWallet);
        toPixKey = pixKeyRepository.save(toPixKey);

        // Simulate an initial Pix transfer
        String idempotencyKey = UUID.randomUUID().toString();
        PixTransferRequest transferRequest = new PixTransferRequest(fromWallet.getId(), toPixKey.getKeyValue(), new BigDecimal("100.00"));

        String responseContent = mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        endToEndId = objectMapper.readTree(responseContent).get("endToEndId").asText();
    }

    @Test
    void shouldProcessConfirmedWebhookEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PixWebhookRequest webhookRequest = new PixWebhookRequest(endToEndId, eventId, PixEventType.CONFIRMED, LocalDateTime.now());

        mockMvc.perform(post("/pix/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Transação Pix confirmada e saldo creditado."));

        PixTransaction pixTransaction = pixTransactionRepository.findById(endToEndId).orElseThrow();
        assertEquals(PixTransactionStatus.CONFIRMED, pixTransaction.getStatus());

        Wallet updatedFromWallet = walletRepository.findById(fromWallet.getId()).orElseThrow();
        Wallet updatedToWallet = walletRepository.findById(toWallet.getId()).orElseThrow();

        assertEquals(new BigDecimal("900.00"), updatedFromWallet.getBalance()); // Debited in initial transfer
        assertEquals(new BigDecimal("600.00"), updatedToWallet.getBalance()); // Credited in webhook

        List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByWalletOrderByCreatedAtDesc(updatedToWallet);
        assertFalse(ledgerEntries.isEmpty(), "Deve haver pelo menos 1 ledger entry");
        assertTrue(ledgerEntries.stream().anyMatch(entry ->
                entry.getType() == LedgerEntryType.PIX_TRANSFER_IN &&
                entry.getAmount().compareTo(new BigDecimal("100.00")) == 0),
                "Deve haver um ledger entry PIX_TRANSFER_IN de 100.00. Entries encontrados: " + ledgerEntries.size());
    }

    @Test
    void shouldProcessRejectedWebhookEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PixWebhookRequest webhookRequest = new PixWebhookRequest(endToEndId, eventId, PixEventType.REJECTED, LocalDateTime.now());

        mockMvc.perform(post("/pix/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Transação Pix rejeitada e débito estornado."));

        PixTransaction pixTransaction = pixTransactionRepository.findById(endToEndId).orElseThrow();
        assertEquals(PixTransactionStatus.REJECTED, pixTransaction.getStatus());

        Wallet updatedFromWallet = walletRepository.findById(fromWallet.getId()).orElseThrow();
        Wallet updatedToWallet = walletRepository.findById(toWallet.getId()).orElseThrow();

        assertEquals(new BigDecimal("1000.00"), updatedFromWallet.getBalance()); // Debited in initial transfer, then refunded
        assertEquals(new BigDecimal("500.00"), updatedToWallet.getBalance()); // Not credited

        List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByWalletOrderByCreatedAtDesc(updatedFromWallet);
        assertTrue(ledgerEntries.stream().anyMatch(entry -> entry.getType() == LedgerEntryType.PIX_TRANSFER_REVERSAL && entry.getAmount().compareTo(new BigDecimal("100.00")) == 0));
    }

    @Test
    void shouldHandleDuplicateWebhookEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        PixWebhookRequest webhookRequest = new PixWebhookRequest(endToEndId, eventId, PixEventType.CONFIRMED, LocalDateTime.now());

        // First event
        mockMvc.perform(post("/pix/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk());

        // Second (duplicate) event
        mockMvc.perform(post("/pix/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Evento já processado."));

        Wallet updatedToWallet = walletRepository.findById(toWallet.getId()).orElseThrow();
        assertEquals(new BigDecimal("600.00"), updatedToWallet.getBalance()); // Balance should only be credited once
    }

    @Test
    void shouldHandleRejectedEventBeforeConfirmedEvent() throws Exception {
        String eventIdRejected = UUID.randomUUID().toString();
        PixWebhookRequest rejectedWebhookRequest = new PixWebhookRequest(endToEndId, eventIdRejected, PixEventType.REJECTED, LocalDateTime.now());

        // Process rejected event first
        mockMvc.perform(post("/pix/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectedWebhookRequest)))
                .andExpect(status().isOk());

        // Now process confirmed event for the same transaction
        String eventIdConfirmed = UUID.randomUUID().toString();
        PixWebhookRequest confirmedWebhookRequest = new PixWebhookRequest(endToEndId, eventIdConfirmed, PixEventType.CONFIRMED, LocalDateTime.now().plusSeconds(10));

        mockMvc.perform(post("/pix/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmedWebhookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Transação já em estado final ou processada. Evento registrado."));

        PixTransaction pixTransaction = pixTransactionRepository.findById(endToEndId).orElseThrow();
        assertEquals(PixTransactionStatus.REJECTED, pixTransaction.getStatus()); // Should remain rejected

        Wallet updatedFromWallet = walletRepository.findById(fromWallet.getId()).orElseThrow();
        Wallet updatedToWallet = walletRepository.findById(toWallet.getId()).orElseThrow();

        assertEquals(new BigDecimal("1000.00"), updatedFromWallet.getBalance()); // Refunded
        assertEquals(new BigDecimal("500.00"), updatedToWallet.getBalance()); // Not credited
    }
}
