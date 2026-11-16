package com.cloudsaver.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApiKeyAuthFilterTest {

    @Test
    void deveRejeitarSemChaveQuandoExigida() throws Exception {
        ApiKeyAuthFilter filtro = new ApiKeyAuthFilter("segredo", true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analise");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void devePermitirComChaveCorreta() throws Exception {
        ApiKeyAuthFilter filtro = new ApiKeyAuthFilter("segredo", true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analise");
        request.addHeader(ApiKeyAuthFilter.HEADER_API_KEY, "segredo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }
}
