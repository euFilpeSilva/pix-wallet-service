package com.pixservice.application.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixservice.domain.model.IdempotencyKey;
import com.pixservice.domain.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;

/**
 * Serviço centralizado para gerenciar idempotência de operações da API.
 *
 * Este serviço permite que qualquer operação armazene sua resposta associada
 * a uma chave de idempotência, garantindo que requisições duplicadas retornem
 * a mesma resposta sem reprocessar a operação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Verifica se existe uma resposta idempotente para a chave fornecida.
     *
     * @param key Chave de idempotência (geralmente vinda do header Idempotency-Key)
     * @return Optional contendo a resposta deserializada se existir
     */
    @Transactional(readOnly = true)
    public <T> Optional<IdempotentResponse<T>> getIdempotentResponse(String key, Class<T> responseType) {
        if (key == null || key.isBlank()) {
            log.warn("Tentativa de obter resposta idempotente com chave nula ou vazia");
            return Optional.empty();
        }

        Optional<IdempotencyKey> existingKey = idempotencyKeyRepository.findByKeyValue(key);

        if (existingKey.isPresent()) {
            IdempotencyKey idempotentKey = existingKey.get();
            log.info("Resposta idempotente encontrada para key={}, httpStatus={}", key, idempotentKey.getHttpStatus());

            try {
                T response = objectMapper.readValue(idempotentKey.getResponseBody(), responseType);
                return Optional.of(new IdempotentResponse<>(response));
            } catch (JsonProcessingException e) {
                log.error("Erro ao deserializar resposta idempotente para key={}", key, e);
                // Em caso de erro de deserialização, retornamos vazio para reprocessar
                return Optional.empty();
            }
        }

        log.debug("Nenhuma resposta idempotente encontrada para key={}", key);
        return Optional.empty();
    }

    /**
     * Armazena uma resposta para ser reutilizada em requisições idempotentes futuras.
     *
     * @param key Chave de idempotência
     * @param response Objeto de resposta a ser serializado e armazenado
     * @param httpStatus Status HTTP da resposta
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> void saveIdempotentResponse(String key, T response, HttpStatus httpStatus) {
        if (key == null || key.isBlank()) {
            log.warn("Tentativa de salvar resposta idempotente com chave nula ou vazia");
            return;
        }

        try {
            String responseBody = objectMapper.writeValueAsString(response);
            IdempotencyKey idempotencyKey = new IdempotencyKey(key, responseBody, httpStatus.value());
            idempotencyKeyRepository.save(idempotencyKey);

            log.info("Resposta idempotente salva com sucesso para key={}, httpStatus={}", key, httpStatus.value());
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar resposta para idempotência key={}", key, e);
            throw new IdempotencySerializationException("Erro ao serializar resposta para idempotência", e);
        }
    }
}
