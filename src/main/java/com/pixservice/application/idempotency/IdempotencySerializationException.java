package com.pixservice.application.idempotency;

/**
 * Exceção lançada quando ocorre erro na serialização/deserialização
 * de respostas idempotentes.
 *
 * Representa falha técnica no processo de persistência ou recuperação
 * de dados idempotentes, indicando problema com o formato JSON ou
 * compatibilidade de tipos.
 */
public class IdempotencySerializationException extends RuntimeException {

    public IdempotencySerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdempotencySerializationException(String message) {
        super(message);
    }
}

