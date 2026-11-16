package com.cloudsaver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit por IP (e opcionalmente por X-API-Key) apenas em POST /api/analise.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final int limitePorCliente;
    private final long janelaMs;
    private final Map<String, Deque<Long>> historico = new ConcurrentHashMap<>();

    public RateLimitFilter(int limitePorCliente, int janelaMinutos) {
        this.limitePorCliente = Math.max(0, limitePorCliente);
        this.janelaMs = Math.max(1, janelaMinutos) * 60_000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (limitePorCliente <= 0) {
            return true;
        }
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/analise".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String chave = chaveCliente(request);
        long agora = System.currentTimeMillis();
        Deque<Long> timestamps = historico.computeIfAbsent(chave, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && agora - timestamps.peekFirst() > janelaMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limitePorCliente) {
                response.setStatus(429);
                response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        "Limite de requisições por cliente atingido. Tente novamente mais tarde.");
                return;
            }
            timestamps.addLast(agora);
        }

        filterChain.doFilter(request, response);
    }

    private String chaveCliente(HttpServletRequest request) {
        String apiKey = request.getHeader(ApiKeyAuthFilter.HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey.trim();
        }
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return "ip:" + encaminhado.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
