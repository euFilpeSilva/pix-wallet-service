package com.pixservice.service;

import com.pixservice.application.dto.PixKeyResponse;
import com.pixservice.application.dto.RegisterPixKeyRequest;
import com.pixservice.application.service.PixKeyService;
import com.pixservice.domain.model.PixKey;
import com.pixservice.domain.model.PixKeyType;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.PixKeyRepository;
import com.pixservice.domain.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PixKeyServiceTest {

    @Mock
    private PixKeyRepository pixKeyRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private PixKeyService pixKeyService;

    private Wallet testWallet;
    private PixKey testPixKey;

    @BeforeEach
    void setUp() {
        testWallet = new Wallet("user1", new BigDecimal("500.00"));
        testWallet.setId(1L);

        testPixKey = new PixKey("email@example.com", PixKeyType.EMAIL, testWallet);
        testPixKey.setId(10L);
        testPixKey.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void shouldRegisterPixKeySuccessfully() {
        Long walletId = 10L;
        RegisterPixKeyRequest request = new RegisterPixKeyRequest("newkey@example.com", PixKeyType.EMAIL);
        when(pixKeyRepository.existsByKeyValue(request.getKeyValue())).thenReturn(false);
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(testWallet));
        when(pixKeyRepository.save(any(PixKey.class))).thenReturn(testPixKey);

        PixKeyResponse response = pixKeyService.registerPixKey(request, walletId);

        assertNotNull(response);
        assertEquals(testPixKey.getId(), response.getId());
        assertEquals(testPixKey.getKeyValue(), response.getKeyValue());
        assertEquals(testPixKey.getType(), response.getType());
        assertEquals(testWallet.getId(), response.getWalletId());
        assertEquals(testWallet.getUserId(), response.getUserId());

        verify(pixKeyRepository, times(1)).existsByKeyValue(request.getKeyValue());
        verify(walletRepository, times(1)).findById(walletId);
        verify(pixKeyRepository, times(1)).save(any(PixKey.class));
    }

    @Test
    void shouldThrowExceptionWhenRegisterPixKeyWithExistingKeyValue() {
        Long walletId = 10L;
        RegisterPixKeyRequest request = new RegisterPixKeyRequest("email@example.com", PixKeyType.EMAIL);
        when(pixKeyRepository.existsByKeyValue(request.getKeyValue())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixKeyService.registerPixKey(request, walletId);
        });

        assertEquals("Chave Pix já registrada.", exception.getMessage());
        verify(pixKeyRepository, times(1)).existsByKeyValue(request.getKeyValue());
        verify(walletRepository, never()).findById(anyLong());
        verify(pixKeyRepository, never()).save(any(PixKey.class));
    }

    @Test
    void shouldThrowExceptionWhenRegisterPixKeyWithWalletNotFound() {
        Long walletId = 99L;
        RegisterPixKeyRequest request = new RegisterPixKeyRequest("newkey@example.com", PixKeyType.EMAIL);
        when(pixKeyRepository.existsByKeyValue(request.getKeyValue())).thenReturn(false);
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixKeyService.registerPixKey(request, walletId);
        });

        assertEquals("Carteira não encontrada.", exception.getMessage());
        verify(pixKeyRepository, times(1)).existsByKeyValue(request.getKeyValue());
        verify(walletRepository, times(1)).findById(walletId);
        verify(pixKeyRepository, never()).save(any(PixKey.class));
    }

    @Test
    void shouldGetPixKeyByValueSuccessfully() {
        when(pixKeyRepository.findByKeyValue("email@example.com")).thenReturn(Optional.of(testPixKey));

        PixKeyResponse response = pixKeyService.getPixKeyByValue("email@example.com");

        assertNotNull(response);
        assertEquals(testPixKey.getId(), response.getId());
        assertEquals(testPixKey.getKeyValue(), response.getKeyValue());
        verify(pixKeyRepository, times(1)).findByKeyValue("email@example.com");
    }

    @Test
    void shouldThrowExceptionWhenGetPixKeyByValueNotFound() {
        when(pixKeyRepository.findByKeyValue("nonexistent@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            pixKeyService.getPixKeyByValue("nonexistent@example.com");
        });

        assertEquals("Chave Pix não encontrada.", exception.getMessage());
        verify(pixKeyRepository, times(1)).findByKeyValue("nonexistent@example.com");
    }
}
