package com.cloudsaver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Autentica chamadas a /api/** via header X-API-Key quando uma chave está configurada.
 * Se nenhuma chave estiver definida, o filtro não exige autenticação (modo desenvolvimento).
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "X-API-Key";

    private final String apiKeyEsperada;
    private final boolean exigirApiKey;

    public ApiKeyAuthFilter(String apiKeyEsperada, boolean exigirApiKey) {
        this.apiKeyEsperada = apiKeyEsperada == null ? "" : apiKeyEsperada.trim();
        this.exigirApiKey = exigirApiKey && StringUtils.hasText(this.apiKeyEsperada);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!exigirApiKey) {
            filterChain.doFilter(request, response);
            return;
        }

        String fornecida = request.getHeader(HEADER_API_KEY);
        if (!chaveValida(fornecida)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("Não autorizado. Informe o header X-API-Key válido.");
            return;
        }

        var autenticacao = new UsernamePasswordAuthenticationToken(
                "api-client", "n/a", AuthorityUtils.createAuthorityList("ROLE_API"));
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
        filterChain.doFilter(request, response);
    }

    private boolean chaveValida(String fornecida) {
        if (!StringUtils.hasText(fornecida)) {
            return false;
        }
        byte[] a = fornecida.trim().getBytes(StandardCharsets.UTF_8);
        byte[] b = apiKeyEsperada.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
