package com.pixservice.application.validation;

import com.pixservice.domain.model.PixTransaction;
import com.pixservice.domain.model.PixTransactionStatus;
import com.pixservice.domain.model.Wallet;
import com.pixservice.domain.repository.PixTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Validador de regras de negócio para transferências Pix.
 * Centraliza todas as validações para manter PixTransferService focado na orquestração.
 */
@Component
@RequiredArgsConstructor
public class PixTransferValidator {

    @Value("${pix.duplicate.window-minutes:0}")
    private long duplicateWindowMinutes;

    @Value("${pix.duplicate.enabled:true}")
    private boolean duplicateEnabled;

    private final PixTransactionRepository pixTransactionRepository;

    /**
     * Valida que o valor da transferência é positivo.
     */
    public void validateAmount(BigDecimal amount) {
        if (isNull(amount) || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor da transferência deve ser positivo.");
        }
    }

    /**
     * Valida que a transferência não é para a mesma carteira.
     */
    public void validateNotSameWallet(Wallet from, Wallet to) {
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Não é possível transferir para a mesma carteira.");
        }
    }

    /**
     * Verifica se existe uma transferência idêntica recente CONFIRMADA dentro da janela configurada.
     * Se a janela estiver desabilitada (duplicateEnabled=false ou duplicateWindowMinutes <= 0) não bloqueia nada.
     */
    public void validateNoDuplicateTransfer(Wallet fromWallet, String toPixKey, BigDecimal amount) {
        if (!duplicateEnabled || duplicateWindowMinutes <= 0) {
            return; // feature desligada
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(duplicateWindowMinutes); // corrigido: usar minutos
        Optional<PixTransaction> recentDuplicate = pixTransactionRepository
                .findRecentDuplicateTransfer(fromWallet, toPixKey, amount, since);
        if (recentDuplicate.isPresent()) {
            PixTransaction previous = recentDuplicate.get();
            if (previous.getStatus() == PixTransactionStatus.CONFIRMED) {
                throw new IllegalStateException(buildDuplicateTransferMessage(previous, amount, toPixKey));
            }
        }
    }

    private String buildDuplicateTransferMessage(PixTransaction previous, BigDecimal amount, String toPixKey) {
        return String.format(
                "Transferência idêntica confirmada detectada há menos de %d minuto(s). Valor: %s Destino: %s EndToEndId anterior: %s.",
                duplicateWindowMinutes,
                amount,
                toPixKey,
                previous.getEndToEndId()
        );
    }

    /**
     * Valida todas as regras de negócio em uma única chamada.
     * Útil quando você quer validar tudo de uma vez.
     */
    public void validateTransfer(BigDecimal amount, Wallet fromWallet, Wallet toWallet, String toPixKey) {
        validateAmount(amount);
        validateNotSameWallet(fromWallet, toWallet);
        validateNoDuplicateTransfer(fromWallet, toPixKey, amount);
    }
}
