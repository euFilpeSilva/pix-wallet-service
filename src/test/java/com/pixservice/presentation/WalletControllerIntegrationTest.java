package com.pixservice.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixservice.application.dto.CreateWalletRequest;
import com.pixservice.application.dto.WalletResponse;
import com.pixservice.domain.model.Wallet;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
class WalletControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    private Wallet existingWallet;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll(); // Clean up before each test
        existingWallet = new Wallet("testUser", new BigDecimal("500.00"));
        existingWallet = walletRepository.save(existingWallet);
    }

    @Test
    void shouldCreateWallet() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("newUser", new BigDecimal("100.00"));

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("newUser"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void shouldReturnBadRequestWhenCreateWalletWithExistingUser() throws Exception {
        CreateWalletRequest request = new CreateWalletRequest("testUser", new BigDecimal("100.00"));

        mockMvc.perform(post("/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Carteira para este usuário já existe."));
    }

    @Test
    void shouldGetWalletBalance() throws Exception {
        mockMvc.perform(get("/wallets/{id}/balance", existingWallet.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingWallet.getId()))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    void shouldDepositIntoWallet() throws Exception {
        mockMvc.perform(post("/wallets/{id}/deposit", existingWallet.getId())
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600.00));

        Wallet updatedWallet = walletRepository.findById(existingWallet.getId()).orElseThrow();
        assertEquals(new BigDecimal("600.00"), updatedWallet.getBalance());
    }

    @Test
    void shouldWithdrawFromWallet() throws Exception {
        mockMvc.perform(post("/wallets/{id}/withdraw", existingWallet.getId())
                        .param("amount", "200.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(300.00));

        Wallet updatedWallet = walletRepository.findById(existingWallet.getId()).orElseThrow();
        assertEquals(new BigDecimal("300.00"), updatedWallet.getBalance());
    }

    @Test
    void shouldReturnConflictWhenWithdrawInsufficientBalance() throws Exception {
        mockMvc.perform(post("/wallets/{id}/withdraw", existingWallet.getId())
                        .param("amount", "1000.00"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Saldo insuficiente."));
    }
}
