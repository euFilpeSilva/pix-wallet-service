package com.pixservice.infrastructure.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro para adicionar correlation ID (traceId) em todas as requisições HTTP.
 * O traceId é propagado automaticamente via MDC (Mapped Diagnostic Context) para todos os logs.
 */
@Component
@Slf4j
public class LoggingFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_MDC_KEY = "idempotencyKey";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            // Obter ou gerar traceId
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            // Capturar Idempotency-Key se presente
            String idempotencyKey = httpRequest.getHeader(IDEMPOTENCY_KEY_HEADER);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                MDC.put(IDEMPOTENCY_KEY_MDC_KEY, idempotencyKey);
            }

            log.debug("Requisição recebida: {} {} - traceId={}",
                    httpRequest.getMethod(), httpRequest.getRequestURI(), traceId);

            chain.doFilter(request, response);
        } finally {
            // Limpar MDC após processamento
            MDC.clear();
        }
    }
}

