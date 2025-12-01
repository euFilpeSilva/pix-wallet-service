package com.pixservice.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixservice.application.dto.PixTransferRequest;
import com.pixservice.domain.model.IdempotencyKey;
import com.pixservice.domain.model.PixKey;
import com.pixservice.domain.model.PixKeyType;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.IdempotencyKeyRepository;
import com.pixservice.domain.repository.PixKeyRepository;
import com.pixservice.domain.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class PixTransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PixKeyRepository pixKeyRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private com.pixservice.domain.repository.PixTransactionRepository pixTransactionRepository;

    @Autowired
    private com.pixservice.domain.repository.LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private com.pixservice.domain.repository.PixEventRepository pixEventRepository;

    private Wallet fromWallet;
    private Wallet toWallet;
    private PixKey toPixKey;

    @BeforeEach
    void setUp() {
        cleanupDatabase();

        fromWallet = new Wallet("user1", new BigDecimal("1000.00"));
        fromWallet = walletRepository.save(fromWallet);

        toWallet = new Wallet("user2", new BigDecimal("500.00"));
        toWallet = walletRepository.save(toWallet);

        toPixKey = new PixKey("recipient@email.com", PixKeyType.EMAIL, toWallet);
        toPixKey = pixKeyRepository.save(toPixKey);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        // Delete in correct order to avoid foreign key constraint violations
        idempotencyKeyRepository.deleteAll();
        pixEventRepository.deleteAll();
        pixTransactionRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        pixKeyRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void shouldInitiatePixTransfer() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PixTransferRequest request = new PixTransferRequest(fromWallet.getId(), toPixKey.getKeyValue(), new BigDecimal("100.00"));

        mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.endToEndId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Verify that the idempotency key is saved
        IdempotencyKey savedIdempotencyKey = idempotencyKeyRepository.findByKeyValue(idempotencyKey).orElseThrow();
        assertNotNull(savedIdempotencyKey);
        assertEquals(202, savedIdempotencyKey.getHttpStatus());

        // Verify that the fromWallet balance is updated
        Wallet updatedFromWallet = walletRepository.findById(fromWallet.getId()).orElseThrow();
        assertEquals(new BigDecimal("900.00"), updatedFromWallet.getBalance());
    }

    @Test
    void shouldReturnCachedResponseForIdempotentRequest() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PixTransferRequest request = new PixTransferRequest(fromWallet.getId(), toPixKey.getKeyValue(), new BigDecimal("100.00"));

        // First request
        mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // Second request with same idempotency key
        mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.endToEndId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Verify that the fromWallet balance is updated only once
        Wallet updatedFromWallet = walletRepository.findById(fromWallet.getId()).orElseThrow();
        assertEquals(new BigDecimal("900.00"), updatedFromWallet.getBalance());
    }

    @Test
    void shouldReturnBadRequestWhenTransferAmountIsInvalid() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PixTransferRequest request = new PixTransferRequest(fromWallet.getId(), toPixKey.getKeyValue(), BigDecimal.ZERO);

        mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("O valor da transferência deve ser positivo."));
    }

    @Test
    void shouldReturnBadRequestWhenFromWalletNotFound() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PixTransferRequest request = new PixTransferRequest(999L, toPixKey.getKeyValue(), new BigDecimal("100.00"));

        mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Carteira de origem não encontrada."));
    }

    @Test
    void shouldReturnBadRequestWhenToPixKeyNotFound() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PixTransferRequest request = new PixTransferRequest(fromWallet.getId(), "nonexistent@email.com", new BigDecimal("100.00"));

        mockMvc.perform(post("/pix/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Chave Pix de destino não encontrada."));
    }
}
