package com.cloudsaver.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    @Test
    void deveBloquearAposLimitePorIp() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(2, 60);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analise");
            request.setRemoteAddr("10.0.0.8");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filtro.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest bloqueada = new MockHttpServletRequest("POST", "/api/analise");
        bloqueada.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(bloqueada, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
