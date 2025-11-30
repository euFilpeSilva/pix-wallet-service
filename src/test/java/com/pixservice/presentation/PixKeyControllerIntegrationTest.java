package com.pixservice.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixservice.application.dto.RegisterPixKeyRequest;
import com.pixservice.domain.model.PixKey;
import com.pixservice.domain.model.PixKeyType;
import com.pixservice.domain.model.Wallet;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
class PixKeyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PixKeyRepository pixKeyRepository;

    private Wallet existingWallet;
    private PixKey existingPixKey;

    @BeforeEach
    void setUp() {
        pixKeyRepository.deleteAll();
        walletRepository.deleteAll();

        existingWallet = new Wallet("testUser", new BigDecimal("100.00"));
        existingWallet = walletRepository.save(existingWallet);

        existingPixKey = new PixKey("test@email.com", PixKeyType.EMAIL, existingWallet);
        existingPixKey = pixKeyRepository.save(existingPixKey);
    }

    @Test
    void shouldRegisterPixKey() throws Exception {
        RegisterPixKeyRequest request = new RegisterPixKeyRequest("new@email.com", PixKeyType.EMAIL);

        mockMvc.perform(post("/wallets/{walletId}/pix-keys", existingWallet.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyValue").value("new@email.com"))
                .andExpect(jsonPath("$.type").value("EMAIL"));
    }

    @Test
    void shouldReturnBadRequestWhenRegisterPixKeyWithExistingKeyValue() throws Exception {
        RegisterPixKeyRequest request = new RegisterPixKeyRequest("test@email.com", PixKeyType.EMAIL);

        mockMvc.perform(post("/wallets/{walletId}/pix-keys", existingWallet.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Chave Pix já registrada."));
    }

    @Test
    void shouldGetPixKeyByValue() throws Exception {
        mockMvc.perform(get("/wallets/{walletId}/pix-keys/{keyValue}", existingWallet.getId(), "test@email.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyValue").value("test@email.com"))
                .andExpect(jsonPath("$.type").value("EMAIL"));
    }

    @Test
    void shouldReturnBadRequestWhenGetPixKeyByValueNotFound() throws Exception {
        mockMvc.perform(get("/wallets/{walletId}/pix-keys/{keyValue}", existingWallet.getId(), "nonexistent@email.com"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Chave Pix não encontrada."));
    }
}
