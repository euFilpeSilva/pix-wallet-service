package com.pixservice.infrastructure.logging;

import org.slf4j.MDC;

/**
 * Utilitário para gerenciar MDC (Mapped Diagnostic Context) em operações assíncronas
 * e para adicionar contexto de rastreamento em logs.
 *
 * O MDC permite propagar informações de contexto (endToEndId, eventId, etc.)
 * automaticamente para todos os logs dentro do mesmo fluxo de execução.
 */
public class MdcUtils {

    public static final String END_TO_END_ID = "endToEndId";
    public static final String EVENT_ID = "eventId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String WALLET_ID = "walletId";
    public static final String TRACE_ID = "traceId";

    private MdcUtils() {
        // Utility class
    }

    /**
     * Define o endToEndId no MDC para rastreamento de transferências Pix.
     */
    public static void setEndToEndId(String endToEndId) {
        if (endToEndId != null && !endToEndId.isBlank()) {
            MDC.put(END_TO_END_ID, endToEndId);
        }
    }

    /**
     * Define o eventId no MDC para rastreamento de eventos de webhook.
     */
    public static void setEventId(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            MDC.put(EVENT_ID, eventId);
        }
    }

    /**
     * Define o idempotencyKey no MDC para rastreamento de requisições idempotentes.
     */
    public static void setIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            MDC.put(IDEMPOTENCY_KEY, idempotencyKey);
        }
    }

    /**
     * Define o walletId no MDC para rastreamento de operações de carteira.
     */
    public static void setWalletId(Long walletId) {
        if (walletId != null) {
            MDC.put(WALLET_ID, walletId.toString());
        }
    }

    /**
     * Remove o endToEndId do MDC.
     */
    public static void clearEndToEndId() {
        MDC.remove(END_TO_END_ID);
    }

    /**
     * Remove o eventId do MDC.
     */
    public static void clearEventId() {
        MDC.remove(EVENT_ID);
    }

    /**
     * Remove o idempotencyKey do MDC.
     */
    public static void clearIdempotencyKey() {
        MDC.remove(IDEMPOTENCY_KEY);
    }

    /**
     * Remove o walletId do MDC.
     */
    public static void clearWalletId() {
        MDC.remove(WALLET_ID);
    }

    /**
     * Limpa todas as chaves de contexto do MDC.
     * Use com cautela - pode remover traceId e outras informações globais.
     */
    public static void clearAll() {
        MDC.clear();
    }
}

