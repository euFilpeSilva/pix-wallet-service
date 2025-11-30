package com.pixservice.application.service;

import com.pixservice.application.dto.PixKeyResponse;
import com.pixservice.application.dto.RegisterPixKeyRequest;
import com.pixservice.domain.model.PixKey;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.PixKeyRepository;
import com.pixservice.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PixKeyService {

    private final PixKeyRepository pixKeyRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public PixKeyResponse registerPixKey(RegisterPixKeyRequest request, Long walletId) {
        log.info("Registrando chave Pix - walletId={}, keyValue={}, type={}",
                walletId, request.getKeyValue(), request.getType());

        if (pixKeyRepository.existsByKeyValue(request.getKeyValue())) {
            log.warn("Tentativa de registrar chave Pix duplicada - keyValue={}", request.getKeyValue());
            throw new IllegalArgumentException("Chave Pix já registrada.");
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Carteira não encontrada."));

        try {
            PixKey pixKey = new PixKey(request.getKeyValue(), request.getType(), wallet);
            pixKey = pixKeyRepository.save(pixKey);

            log.info("Chave Pix registrada com sucesso - pixKeyId={}, walletId={}, keyValue={}",
                    pixKey.getId(), walletId, pixKey.getKeyValue());

            return toPixKeyResponse(pixKey);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Violação de unicidade ao registrar chave Pix - keyValue={}", request.getKeyValue());
            throw new IllegalArgumentException("Chave Pix já registrada.");
        }
    }

    @Transactional(readOnly = true)
    public PixKeyResponse getPixKeyByValue(String keyValue) {
        return pixKeyRepository.findByKeyValue(keyValue)
                .map(this::toPixKeyResponse)
                .orElseThrow(() -> new IllegalArgumentException("Chave Pix não encontrada."));
    }

    private PixKeyResponse toPixKeyResponse(PixKey pixKey) {
        return new PixKeyResponse(
                pixKey.getId(),
                pixKey.getKeyValue(),
                pixKey.getType(),
                pixKey.getWallet().getId(),
                pixKey.getWallet().getUserId(),
                pixKey.getCreatedAt()
        );
    }
}
