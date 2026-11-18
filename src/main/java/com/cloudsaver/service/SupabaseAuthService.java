package com.cloudsaver.service;

import com.cloudsaver.security.SupabaseUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SupabaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthService.class);

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final String supabaseJwtSecret;
    private final boolean authRequired;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache simples em memória para tokens válidos: token -> CacheEntry
    private final Map<String, CacheEntry> tokenCache = new ConcurrentHashMap<>();

    private record CacheEntry(SupabaseUser user, long expiresAtMillis) {}

    public SupabaseAuthService(
            @Value("${cloudsaver.supabase.url:}") String supabaseUrl,
            @Value("${cloudsaver.supabase.anon-key:}") String supabaseAnonKey,
            @Value("${cloudsaver.supabase.jwt-secret:}") String supabaseJwtSecret,
            @Value("${cloudsaver.supabase.auth-required:false}") boolean authRequired,
            RestTemplate restTemplate) {
        this.supabaseUrl = supabaseUrl != null ? supabaseUrl.trim().replaceAll("/+$", "") : "";
        this.supabaseAnonKey = supabaseAnonKey != null ? supabaseAnonKey.trim() : "";
        this.supabaseJwtSecret = supabaseJwtSecret != null ? supabaseJwtSecret.trim() : "";
        this.authRequired = authRequired;
        this.restTemplate = restTemplate;
    }

    public boolean isAuthRequired() {
        return authRequired;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public String getSupabaseAnonKey() {
        return supabaseAnonKey;
    }

    public Optional<SupabaseUser> validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String token = rawToken.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }

        // Verifica cache em memória
        CacheEntry cached = tokenCache.get(token);
        if (cached != null && cached.expiresAtMillis() > System.currentTimeMillis()) {
            return Optional.of(cached.user());
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            long exp = payload.has("exp") ? payload.get("exp").asLong() : 0;
            if (exp > 0 && exp * 1000 < System.currentTimeMillis()) {
                log.debug("Token Supabase expirado");
                return Optional.empty();
            }

            String userId = payload.has("sub") ? payload.get("sub").asText() : null;
            String email = payload.has("email") ? payload.get("email").asText() : "";
            String role = payload.has("role") ? payload.get("role").asText() : "authenticated";

            if (userId == null || userId.isBlank()) {
                return Optional.empty();
            }

            // 1. Se o jwt-secret estiver disponível, valida assinatura HMAC localmente
            if (!supabaseJwtSecret.isEmpty()) {
                if (!verifyHmac(parts[0], parts[1], parts[2], supabaseJwtSecret)) {
                    log.warn("Assinatura do token Supabase inválida via JWT secret");
                    return Optional.empty();
                }
            } else if (!supabaseUrl.isEmpty() && !supabaseAnonKey.isEmpty()) {
                // 2. Se a URL e chave anônima do Supabase estiverem configuradas, valida na API do Supabase Auth
                if (!verifyWithSupabaseAuth(token)) {
                    log.warn("Token rejeitado pela API do Supabase Auth");
                    return Optional.empty();
                }
            }

            SupabaseUser user = SupabaseUser.builder()
                    .id(userId)
                    .email(email)
                    .role(role)
                    .token(token)
                    .build();

            // Cache por 5 minutos ou até a expiração do token (o que for menor)
            long ttlMillis = 5 * 60 * 1000L;
            long tokenExpMillis = exp > 0 ? (exp * 1000L) - System.currentTimeMillis() : ttlMillis;
            long cacheUntil = System.currentTimeMillis() + Math.max(10_000L, Math.min(ttlMillis, tokenExpMillis));
            tokenCache.put(token, new CacheEntry(user, cacheUntil));

            return Optional.of(user);
        } catch (Exception e) {
            log.error("Erro ao validar token Supabase: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean verifyHmac(String headerB64, String payloadB64, String signatureB64, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] computedSig = mac.doFinal((headerB64 + "." + payloadB64).getBytes(StandardCharsets.US_ASCII));
            String computedSigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(computedSig);
            return MessageDigest.isEqual(
                    computedSigB64.getBytes(StandardCharsets.US_ASCII),
                    signatureB64.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            log.error("Falha ao calcular HMAC: {}", e.getMessage());
            return false;
        }
    }

    private boolean verifyWithSupabaseAuth(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("apikey", supabaseAnonKey);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String verifyUrl = supabaseUrl + "/auth/v1/user";
            ResponseEntity<String> response = restTemplate.exchange(
                    verifyUrl,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Falha ao validar token junto ao Supabase: {}", e.getMessage());
            return false;
        }
    }

    public SupabaseUser getFallbackDevUser() {
        return SupabaseUser.builder()
                .id("00000000-0000-0000-0000-000000000000")
                .email("dev@cloudsaver.local")
                .role("authenticated")
                .token("")
                .build();
    }
}
