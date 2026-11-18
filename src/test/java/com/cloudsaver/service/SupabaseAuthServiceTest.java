package com.cloudsaver.service;

import com.cloudsaver.security.SupabaseUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseAuthServiceTest {

    private SupabaseAuthService authService;
    private final String secret = "super-secret-jwt-key-for-test-32-chars-long";

    @BeforeEach
    void setUp() {
        authService = new SupabaseAuthService(
                "https://test-project.supabase.co",
                "test-anon-key",
                secret,
                true,
                new RestTemplate()
        );
    }

    private String createValidToken(String userId, String email, long expSeconds) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = String.format("{\"sub\":\"%s\",\"email\":\"%s\",\"role\":\"authenticated\",\"exp\":%d}", userId, email, expSeconds);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

        return header + "." + payload + "." + signature;
    }

    @Test
    void deveValidarTokenValido() throws Exception {
        long futuro = (System.currentTimeMillis() / 1000) + 3600;
        String token = createValidToken("user-123", "user@example.com", futuro);

        Optional<SupabaseUser> userOpt = authService.validateToken("Bearer " + token);

        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().getId()).isEqualTo("user-123");
        assertThat(userOpt.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void deveRejeitarTokenExpirado() throws Exception {
        long passado = (System.currentTimeMillis() / 1000) - 3600;
        String token = createValidToken("user-123", "user@example.com", passado);

        Optional<SupabaseUser> userOpt = authService.validateToken(token);

        assertThat(userOpt).isEmpty();
    }

    @Test
    void deveRejeitarTokenComAssinaturaInvalida() throws Exception {
        long futuro = (System.currentTimeMillis() / 1000) + 3600;
        String token = createValidToken("user-123", "user@example.com", futuro);
        String tokenAdulterado = token.substring(0, token.length() - 4) + "XXXX";

        Optional<SupabaseUser> userOpt = authService.validateToken(tokenAdulterado);

        assertThat(userOpt).isEmpty();
    }

    @Test
    void deveRetornarDevUserQuandoSolicitado() {
        SupabaseUser devUser = authService.getFallbackDevUser();
        assertThat(devUser).isNotNull();
        assertThat(devUser.getId()).isEqualTo("00000000-0000-0000-0000-000000000000");
    }
}
