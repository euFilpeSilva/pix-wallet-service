package com.pixservice.application.idempotency;

/**
 * Wrapper para resposta idempotente que encapsula a resposta deserializada
 * de uma requisição previamente processada.
 *
 * @param <T> Tipo da resposta encapsulada
 */
public record IdempotentResponse<T>(T response) {

}